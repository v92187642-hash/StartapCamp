package ru.otmetka.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

/** The consent text promises that a person can have their data deleted. */
class DeleteAccountTest extends IntegrationTest {

    @Test
    void ownerDeletesAccountAndEverythingGoes() throws Exception {
        Session owner = loginWithConsent();
        makePremium(owner);
        Session relative = loginWithConsent();
        String planId = createPlan(owner);
        share(owner, planId, relative);
        UUID itemId = jdbc.sql("select id from core.plan_items where plan_id = ?").param(UUID.fromString(planId))
                .query(UUID.class).single();
        UUID entryId = UUID.randomUUID();
        jdbc.sql("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, now() - interval '1 hour')")
                .params(entryId, itemId).update();
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        mvc.perform(as(relative, post("/plans/{id}/questions", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Вопрос\"}"))
                .andExpect(status().isCreated());

        mvc.perform(as(owner, post("/me/delete")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(owner, post("/me/delete")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        for (String table : new String[] {"plans", "plan_items", "entries", "mark_history", "shares", "questions"}) {
            assertThat(count("select count(*) from core." + table + " t where "
                    + whereOfPlan(table), UUID.fromString(planId))).as(table).isZero();
        }
        assertThat(count("select count(*) from core.users where id = ?", owner.userId())).isZero();
        assertThat(count("select count(*) from core.consents where user_id = ?", owner.userId())).isZero();
        assertThat(count("select count(*) from core.refresh_tokens where user_id = ?", owner.userId())).isZero();
        // The old tokens are useless and the relative no longer sees the plan.
        mvc.perform(as(owner, get("/me"))).andExpect(status().isNotFound());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + owner.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(relative, get("/plans"))).andExpect(jsonPath("$.length()").value(0));

        assertThat(published("plan.deleted")).anyMatch(e -> planId.equals(e.at("/data/planId").asText()));
        assertThat(published("user.deleted"))
                .anyMatch(e -> owner.userId().toString().equals(e.at("/data/userId").asText()));
    }

    @Test
    void relativeDeletesAccountAndLeavesTheOwnersPlanIntact() throws Exception {
        Session owner = loginWithConsent();
        makePremium(owner);
        Session relative = loginWithConsent();
        String planId = createPlan(owner);
        share(owner, planId, relative);
        mvc.perform(as(relative, post("/plans/{id}/questions", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Вопрос близкого\"}"))
                .andExpect(status().isCreated());
        mvc.perform(as(owner, post("/plans/{id}/questions", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Вопрос владельца\"}"))
                .andExpect(status().isCreated());

        mvc.perform(as(relative, post("/me/delete")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(as(owner, get("/plans/{id}", planId))).andExpect(status().isOk());
        mvc.perform(as(owner, get("/plans/{id}/shares", planId))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(owner, get("/plans/{id}/questions", planId)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Вопрос владельца"));
        // The relative is no longer among the people to alert.
        var lastSnapshot = published("plan.changed").stream()
                .filter(e -> planId.equals(e.at("/data/planId").asText())).reduce((a, b) -> b).orElseThrow();
        assertThat(lastSnapshot.at("/data/recipients")).isEmpty();
    }

    private static String whereOfPlan(String table) {
        return switch (table) {
            case "plans" -> "t.id = ?";
            case "entries" -> "t.plan_item_id in (select id from core.plan_items where plan_id = ?)";
            case "mark_history" -> "t.entry_id in (select e.id from core.entries e join core.plan_items i "
                    + "on i.id = e.plan_item_id where i.plan_id = ?)";
            default -> "t.plan_id = ?";
        };
    }

    private long count(String sql, Object param) {
        return jdbc.sql(sql).param(param).query(Long.class).single();
    }
}
