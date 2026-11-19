package ru.otmetka.core.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import ru.otmetka.core.IntegrationTest;

@TestPropertySource(properties = "otmetka.rate-limit.register-per-ip=1")
class RegisterLimitTest extends IntegrationTest {

    @Test
    void secondRegistrationFromTheSameIpIsRefused() throws Exception {
        String ip = "203.0.113.50";
        mvc.perform(post("/auth/register")
                        .header("X-Real-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(newEmail(), PASSWORD)))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/register")
                        .header("X-Real-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(newEmail(), PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }
}
