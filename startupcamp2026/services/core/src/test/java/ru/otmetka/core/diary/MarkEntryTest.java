package ru.otmetka.core.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

/** The worked example of services/core/README.md: marking an entry, through every layer. */
class MarkEntryTest extends IntegrationTest {

    Session owner;
    UUID planId;
    UUID entryId;

    /** A fictional plan: one owner, one item with a generic label, one entry an hour ago. */
    @BeforeEach
    void createPlanWithOneEntry() throws Exception {
        owner = loginWithConsent();
        planId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        jdbc.sql("""
                insert into core.plans (id, owner_id, title, starts_on, ends_on, timezone)
                values (?, ?, 'План 1', current_date, current_date + 7, 'Asia/Yekaterinburg')
                """).params(planId, owner.userId()).update();
        jdbc.sql("""
                insert into core.plan_items (id, plan_id, label, time_of_day, days_of_week, position)
                values (?, ?, 'Назначение 1', '08:00', '{1,2,3,4,5,6,7}', 0)
                """).params(itemId, planId).update();
        jdbc.sql("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, now() - interval '1 hour')")
                .params(entryId, itemId).update();
    }

    @Test
    void ownerMarksEntry() throws Exception {
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.label").value("Назначение 1"))
                .andExpect(jsonPath("$.status").value("marked"))
                .andExpect(jsonPath("$.markedBy").value(owner.userId().toString()))
                .andExpect(jsonPath("$.markedAt").exists())
                .andExpect(jsonPath("$.correctedAt").doesNotExist());

        assertThat(statusInDatabase()).isEqualTo("marked");
        assertThat(history()).containsExactly("marked");

        JsonNode event = eventsAboutEntry().getFirst();
        assertThat(event.get("eventId").asText()).isNotBlank();
        assertThat(event.get("occurredAt").asText()).isNotBlank();
        assertThat(event.at("/data/planId").asText()).isEqualTo(planId.toString());
        assertThat(event.at("/data/status").asText()).isEqualTo("marked");
        assertThat(event.at("/data/markedAt").asText()).isNotBlank();
    }

    @Test
    void repeatedMarkChangesNothingAndPublishesOnce() throws Exception {
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctedAt").doesNotExist());

        assertThat(eventsAboutEntry()).hasSize(1);
        assertThat(history()).containsExactly("marked");
    }

    @Test
    void differentTimeIsRecordedAsCorrection() throws Exception {
        Instant corrected = Instant.now().minus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"markedAt\":\"" + corrected + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedAt").value(corrected.toString()))
                .andExpect(jsonPath("$.correctedAt").exists());

        assertThat(eventsAboutEntry()).hasSize(2);
        assertThat(history()).containsExactly("marked", "corrected");
    }

    @Test
    void undoReturnsEntryToPlannedAndKeepsHistory() throws Exception {
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        mvc.perform(as(owner, delete("/entries/{id}/mark", entryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("planned"))
                .andExpect(jsonPath("$.markedAt").doesNotExist())
                .andExpect(jsonPath("$.correctedAt").exists());
        // Undoing again changes nothing.
        mvc.perform(as(owner, delete("/entries/{id}/mark", entryId))).andExpect(status().isOk());

        assertThat(statusInDatabase()).isEqualTo("planned");
        assertThat(history()).containsExactly("marked", "undone");
        List<JsonNode> events = eventsAboutEntry();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).at("/data/status").asText()).isEqualTo("planned");
        assertThat(events.get(1).at("/data").has("markedAt")).isFalse();
    }

    @Test
    void markInTheFutureIsRejected() throws Exception {
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);

        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"markedAt\":\"" + tomorrow + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Время отметки не может быть в будущем"));

        assertThat(statusInDatabase()).isEqualTo("planned");
    }

    @Test
    void strangerIsToldTheEntryDoesNotExist() throws Exception {
        Session stranger = loginWithConsent();

        mvc.perform(as(stranger, put("/entries/{id}/mark", entryId)))
                .andExpect(status().isNotFound());

        assertThat(statusInDatabase()).isEqualTo("planned");
    }

    @Test
    void unknownEntryIsNotFound() throws Exception {
        mvc.perform(as(owner, put("/entries/{id}/mark", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Запись не найдена"));
    }

    @Test
    void tokenIsRequired() throws Exception {
        mvc.perform(put("/entries/{id}/mark", entryId))
                .andExpect(status().isUnauthorized());
    }

    private String statusInDatabase() {
        return jdbc.sql("select status from core.entries where id = ?").param(entryId).query(String.class).single();
    }

    private List<String> history() {
        return jdbc.sql("select action from core.mark_history where entry_id = ? order by at")
                .param(entryId).query(String.class).list();
    }

    /** The mock broker is shared by all tests of the class, so pick this test's entry. */
    private List<JsonNode> eventsAboutEntry() {
        return published("entry.marked").stream()
                .filter(event -> entryId.toString().equals(event.at("/data/entryId").asText()))
                .toList();
    }
}
