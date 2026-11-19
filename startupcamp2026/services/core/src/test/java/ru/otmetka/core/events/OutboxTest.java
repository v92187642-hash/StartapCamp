package ru.otmetka.core.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

/** An event survives RabbitMQ being down at the moment of the change. */
class OutboxTest extends IntegrationTest {

    @Autowired
    OutboxPublisher publisher;

    @BeforeEach
    void emptyOutbox() {
        // Rows left unpublished by other tests would be sent here and confuse the counts.
        jdbc.sql("update core.outbox set published_at = now() where published_at is null").update();
    }

    @Test
    void eventIsKeptWhileTheBrokerIsDownAndSentWhenItIsBack() throws Exception {
        Session owner = loginWithConsent();
        UUID entryId = pastEntry(owner);
        brokerIsDown();

        // The user's request succeeds: the mark is stored, and so is the event.
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId))).andExpect(status().isOk());
        assertThat(unpublished("entry.marked")).isEqualTo(1);
        UUID eventId = jdbc.sql("select id from core.outbox where type = 'entry.marked' and published_at is null")
                .query(UUID.class).single();

        // The timer fires while the broker is still down: nothing is lost.
        publisher.publishPending();
        assertThat(unpublished("entry.marked")).isEqualTo(1);

        brokerIsUp();
        publisher.publishPending();

        assertThat(unpublished("entry.marked")).isZero();
        List<JsonNode> sent = published("entry.marked").stream()
                .filter(event -> entryId.toString().equals(event.at("/data/entryId").asText())).toList();
        // Every attempt carried the same eventId, so a consumer can drop a repeat.
        assertThat(sent).isNotEmpty().allMatch(event -> eventId.toString().equals(event.get("eventId").asText()));
        assertThat(sent.getLast().at("/data/status").asText()).isEqualTo("marked");
    }

    @Test
    void snapshotSentLateDescribesThePlanAsItIsNow() throws Exception {
        Session owner = loginWithConsent();
        brokerIsDown();
        String planId = createPlan(owner);                       // version 1, stays in the outbox
        mvc.perform(as(owner, post("/plans/{id}/items", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeOfDay\":\"21:00\",\"daysOfWeek\":[1,2,3,4,5,6,7]}"))
                .andExpect(status().isCreated());                // version 2, stays too
        assertThat(unpublished("plan.changed")).isEqualTo(2);

        // Forget the failed attempts: only what is sent from now on reaches the broker.
        clearInvocations(rabbit);
        brokerIsUp();
        publisher.publishPending();

        List<JsonNode> sent = published("plan.changed").stream()
                .filter(event -> planId.equals(event.at("/data/planId").asText())).toList();
        assertThat(sent).isNotEmpty();
        // Both waiting messages carry the newest state; the consumer keeps one and skips the equal one.
        assertThat(sent).allMatch(event -> event.at("/data/version").asLong() == 2);
        assertThat(unpublished("plan.changed")).isZero();
    }

    @Test
    void rolledBackChangeLeavesNoEvent() throws Exception {
        Session owner = loginWithConsent();
        UUID entryId = pastEntry(owner);

        // A mark in the future is refused after the entry was loaded: the transaction rolls back.
        mvc.perform(as(owner, put("/entries/{id}/mark", entryId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"markedAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.sql("select count(*) from core.outbox where data like ?").param("%" + entryId + "%")
                .query(Long.class).single()).isZero();
    }

    private void brokerIsDown() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(MessagePostProcessor.class));
    }

    private void brokerIsUp() {
        doNothing().when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class),
                any(MessagePostProcessor.class));
    }

    private long unpublished(String type) {
        return jdbc.sql("select count(*) from core.outbox where type = ? and published_at is null").param(type)
                .query(Long.class).single();
    }

    /** An entry an hour ago in a new plan of the owner; the plan's own events are marked as sent. */
    private UUID pastEntry(Session owner) throws Exception {
        String planId = createPlan(owner);
        UUID itemId = jdbc.sql("select id from core.plan_items where plan_id = ?").param(UUID.fromString(planId))
                .query(UUID.class).single();
        UUID entryId = UUID.randomUUID();
        jdbc.sql("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, now() - interval '1 hour')")
                .params(entryId, itemId).update();
        return entryId;
    }
}
