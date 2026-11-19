package ru.otmetka.core.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The outbox: an event is first written to the table core.outbox, inside the
 * transaction that made the change. If the transaction rolls back, the event
 * disappears with it; if it commits, the event is safely stored even when
 * RabbitMQ is down at that moment. OutboxPublisher sends it afterwards, on a
 * background thread.
 */
@Component
class Outbox {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final OutboxTrigger trigger;
    private final Clock clock;

    Outbox(JdbcClient jdbc, ObjectMapper json, OutboxTrigger trigger, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.trigger = trigger;
        this.clock = clock;
    }

    /** Must be called inside the transaction of the change the event announces. */
    void add(String type, Object data) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An event must be raised inside a transaction: " + type);
        }
        String body;
        try {
            body = json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.sql("insert into core.outbox (id, type, data, occurred_at) values (?, ?, ?, ?)")
                .params(UUID.randomUUID(), type, body, clock.instant().atOffset(ZoneOffset.UTC))
                .update();

        // Usually the broker is up: send right after the commit instead of waiting for the timer.
        // In the background, so the user's request does not wait for the broker.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trigger.publishSoon();
            }
        });
    }
}
