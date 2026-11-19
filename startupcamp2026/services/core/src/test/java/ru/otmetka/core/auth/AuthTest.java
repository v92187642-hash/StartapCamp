package ru.otmetka.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

class AuthTest extends IntegrationTest {

    @Autowired
    TestAccounts testAccounts;

    @Test
    void registrationCreatesAccountAndTokenWorks() throws Exception {
        Session session = login(newEmail());

        mvc.perform(as(session, get("/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId().toString()))
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.consentAccepted").value(false));
    }

    @Test
    void loginWithThePasswordIsTheSameAccount() throws Exception {
        Session registered = login(newEmail());

        // The address is compared in lower case.
        String response = signIn(registered.email().toUpperCase(), PASSWORD)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/me").header("Authorization", "Bearer " + body(response).get("accessToken").asText()))
                .andExpect(jsonPath("$.id").value(registered.userId().toString()));
    }

    @Test
    void passwordIsStoredOnlyAsAHash() throws Exception {
        Session session = login(newEmail());

        String stored = jdbc.sql("select password_hash from core.users where id = ?").param(session.userId())
                .query(String.class).single();
        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void secondRegistrationWithTheSameEmailIsRefused() throws Exception {
        String email = newEmail();
        login(email);

        register(email.toUpperCase(), "another-password").andExpect(status().isConflict());
        signIn(email, "another-password").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        Session session = login(newEmail());

        signIn(session.email(), "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));
        signIn(newEmail(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));
    }

    @Test
    void eleventhFailedLoginIsRefusedEvenWithTheRightPassword() throws Exception {
        Session session = login(newEmail());
        for (int i = 0; i < 10; i++) {
            signIn(session.email(), "wrong-password").andExpect(status().isUnauthorized());
        }
        signIn(session.email(), PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginForgetsEarlierFailures() throws Exception {
        Session session = login(newEmail());
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 5; i++) {
                signIn(session.email(), "wrong-password").andExpect(status().isUnauthorized());
            }
            signIn(session.email(), PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void shortOrOverlongPasswordIsRejected() throws Exception {
        register(newEmail(), "1234567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Пароль должен быть не короче 8 символов"));
        // 40 Cyrillic letters are 80 bytes; BCrypt reads 72.
        register(newEmail(), "п".repeat(40)).andExpect(status().isBadRequest());
    }

    @Test
    void malformedEmailIsRejected() throws Exception {
        register("not-an-email", PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Проверьте введённые данные"));
    }

    @Test
    void testAccountsCanLogInAndKeepTheirDataWhenCreatedAgain() throws Exception {
        testAccounts.create();

        for (int number = 1; number <= TestAccounts.COUNT; number++) {
            String response = signIn(TestAccounts.email(number), TestAccounts.password(number))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            mvc.perform(get("/me").header("Authorization", "Bearer " + body(response).get("accessToken").asText()))
                    .andExpect(jsonPath("$.consentAccepted").value(true))
                    .andExpect(jsonPath("$.premium").value(true));
        }

        String firstId = idOfTestAccount(1);
        testAccounts.create();

        assertThat(idOfTestAccount(1)).isEqualTo(firstId);
        assertThat(jdbc.sql("select count(*) from core.users where email like '%@otmetka.test'")
                .query(Long.class).single()).isEqualTo(TestAccounts.COUNT);
    }

    @Test
    void testAddressesCannotBeRegistered() throws Exception {
        register(TestAccounts.email(3), PASSWORD).andExpect(status().isConflict());
        register("someone@otmetka.test", PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void refreshTokenWorksOnce() throws Exception {
        Session session = login(newEmail());

        String response = refresh(session.refreshToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body(response).get("refreshToken").asText()).isNotEqualTo(session.refreshToken());

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        refresh(body(response).get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    void planEndpointsNeedConsent() throws Exception {
        Session session = login(newEmail());

        mvc.perform(as(session, get("/plans")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("consent-required"));

        mvc.perform(as(session, post("/consents")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"personal_data\",\"textVersion\":\"2026-10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedAt").exists());

        mvc.perform(as(session, get("/plans"))).andExpect(status().isOk());
        mvc.perform(as(session, get("/me"))).andExpect(jsonPath("$.consentAccepted").value(true));
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session session = login(newEmail());
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRevokesEveryRefreshToken() throws Exception {
        Session session = login(newEmail());
        mvc.perform(as(session, post("/auth/logout-all"))).andExpect(status().isNoContent());
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutTokenNothingButLoginAnswers() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/plans")).andExpect(status().isUnauthorized());
    }

    private String idOfTestAccount(int number) {
        return jdbc.sql("select id::text from core.users where email = ?").param(TestAccounts.email(number))
                .query(String.class).single();
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }
}
