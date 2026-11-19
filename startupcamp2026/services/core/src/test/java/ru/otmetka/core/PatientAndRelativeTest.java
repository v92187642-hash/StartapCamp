package ru.otmetka.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Milestone M1 in one story: a patient creates a plan and invites a relative;
 * both see the same day; a mark made by the patient is visible to the relative.
 */
class PatientAndRelativeTest extends IntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Asia/Yekaterinburg");

    @Test
    void patientAndRelativeSeeTheSameDay() throws Exception {
        Session patient = loginWithConsent();
        makePremium(patient);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);

        // --- the patient creates a plan with two items, every day
        JsonNode plan = body(mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsOn":"%s","endsOn":"%s","timezone":"Asia/Yekaterinburg","items":[
                                  {"timeOfDay":"08:00","daysOfWeek":[1,2,3,4,5,6,7]},
                                  {"timeOfDay":"21:30","daysOfWeek":[1,2,3,4,5,6,7]}]}
                                """.formatted(today, today.plusDays(6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("План 1"))
                .andExpect(jsonPath("$.role").value("owner"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Назначение 1"))
                .andExpect(jsonPath("$.items[0].timeOfDay").value("08:00"))
                .andExpect(jsonPath("$.items[1].label").value("Назначение 2"))
                .andReturn().getResponse().getContentAsString());
        String planId = plan.get("id").asText();

        // --- the day view of tomorrow: both entries, in time order, planned
        JsonNode day = body(mvc.perform(as(patient, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("Назначение 1"))
                .andExpect(jsonPath("$[0].status").value("planned"))
                .andExpect(jsonPath("$[0].scheduledAt").value(tomorrow.atTime(8, 0).atZone(ZONE).toInstant().toString()))
                .andExpect(jsonPath("$[1].label").value("Назначение 2"))
                .andReturn().getResponse().getContentAsString());
        String entryId = day.get(0).get("id").asText();

        // --- a third item added later gets its own entries and raises the version
        mvc.perform(as(patient, post("/plans/{id}/items", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeOfDay\":\"14:00\",\"daysOfWeek\":[" + tomorrow.getDayOfWeek().getValue() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Назначение 3"))
                .andExpect(jsonPath("$.position").value(2));
        mvc.perform(as(patient, get("/plans/{id}", planId)))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(as(patient, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].label").value("Назначение 3"));

        // --- several days at once, for clients that work offline
        mvc.perform(as(patient, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()).param("days", "2"))
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(as(patient, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()).param("days", "32"))
                .andExpect(status().isBadRequest());

        // --- the patient invites a relative; the link arrives by email
        Session relative = loginWithConsent();
        mvc.perform(as(relative, get("/plans/{id}", planId))).andExpect(status().isNotFound());

        mvc.perform(as(patient, post("/plans/{id}/shares", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + relative.email().toUpperCase() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitedEmail").value(relative.email()))
                .andExpect(jsonPath("$.status").value("pending"));
        String token = firstMatch("/invite\\?token=([A-Za-z0-9_-]+)", lastMailTo(relative.email()));

        Session stranger = loginWithConsent();
        mvc.perform(as(stranger, post("/shares/accept")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(as(relative, post("/shares/accept")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
        // The link works once.
        mvc.perform(as(relative, post("/shares/accept")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNotFound());

        // --- the relative sees the plan and the same day, read-only
        mvc.perform(as(relative, get("/plans")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(planId))
                .andExpect(jsonPath("$[0].role").value("viewer"));
        mvc.perform(as(relative, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("planned"));
        mvc.perform(as(relative, put("/entries/{id}/mark", entryId))).andExpect(status().isForbidden());
        mvc.perform(as(relative, post("/plans/{id}/items", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeOfDay\":\"10:00\",\"daysOfWeek\":[1]}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(relative, get("/plans/{id}/shares", planId))).andExpect(status().isForbidden());

        // --- the patient marks; the relative sees it after a refresh
        mvc.perform(as(patient, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        mvc.perform(as(relative, get("/plans/{id}/entries", planId)).param("date", tomorrow.toString()))
                .andExpect(jsonPath("$[0].status").value("marked"))
                .andExpect(jsonPath("$[0].markedAt").exists());

        // --- what the notifier was told
        List<JsonNode> snapshots = published("plan.changed").stream()
                .filter(event -> planId.equals(event.at("/data/planId").asText())).toList();
        assertThat(snapshots).extracting(event -> event.at("/data/version").asLong()).containsExactly(1L, 2L, 3L);
        JsonNode last = snapshots.getLast().get("data");
        assertThat(last.get("timezone").asText()).isEqualTo("Asia/Yekaterinburg");
        assertThat(last.get("recipients")).hasSize(1);
        assertThat(last.at("/recipients/0").asText()).isEqualTo(relative.userId().toString());
        assertThat(last.get("entries")).isNotEmpty();
        assertThat(last.at("/entries/0").has("entryId")).isTrue();
        assertThat(last.at("/entries/0").has("dueAt")).isTrue();
        // Ids and times only: no label may leave core.
        assertThat(last.toString()).doesNotContain("Назначение").doesNotContain("План");

        // --- the patient withdraws access
        String shareId = body(mvc.perform(as(patient, get("/plans/{id}/shares", planId)))
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        mvc.perform(as(patient, delete("/shares/{id}", shareId))).andExpect(status().isNoContent());
        mvc.perform(as(relative, get("/plans/{id}", planId))).andExpect(status().isNotFound());
        mvc.perform(as(relative, get("/plans"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void planIsValidated() throws Exception {
        Session patient = loginWithConsent();
        LocalDate today = LocalDate.now(ZONE);

        mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"timezone\":\"Asia/Yekaterinburg\"}"
                                .formatted(today, today.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Дата окончания раньше даты начала"));
        mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"timezone\":\"Mars/Olympus\"}"
                                .formatted(today, today)))
                .andExpect(status().isBadRequest());
        mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"timezone\":\"Asia/Yekaterinburg\"}"
                                .formatted(today, today.plusDays(400))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsOn":"%s","endsOn":"%s","timezone":"Asia/Yekaterinburg",
                                 "items":[{"timeOfDay":"08:00","daysOfWeek":[8]}]}""".formatted(today, today)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerCannotInviteThemselves() throws Exception {
        Session patient = loginWithConsent();
        makePremium(patient);
        LocalDate today = LocalDate.now(ZONE);
        String planId = body(mvc.perform(as(patient, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsOn\":\"%s\",\"endsOn\":\"%s\",\"timezone\":\"Asia/Yekaterinburg\"}"
                                .formatted(today, today)))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(as(patient, post("/plans/{id}/shares", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + patient.email() + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
