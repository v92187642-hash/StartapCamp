package ru.otmetka.core.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.diary.DiaryDirectory;
import ru.otmetka.core.events.Messages.DueEntry;
import ru.otmetka.core.events.Messages.PlanChangedData;
import ru.otmetka.core.plans.PlanDirectory;
import ru.otmetka.core.sharing.AccessPolicy;

/**
 * Sends the rows of core.outbox to RabbitMQ, oldest first, and marks a row
 * as published only when the broker has confirmed the message. It runs in the
 * background right after every commit that added a row (OutboxTrigger), and
 * on a timer for whatever is left: rows from a moment when the broker was
 * down, or from a crash in between.
 *
 * A message can therefore be sent twice (sent, then the "published" mark was
 * lost). It keeps its eventId, and consumers skip an eventId they have seen.
 */
@Component
class OutboxPublisher {

    /** Recently overdue entries stay in the snapshot, see api/asyncapi.yaml. */
    static final Duration SNAPSHOT_LOOKBACK = Duration.ofHours(24);
    static final Duration KEEP_PUBLISHED = Duration.ofDays(7);
    private static final int BATCH = 50;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private record Row(UUID id, String type, String data, Instant occurredAt) {
    }

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final PlanDirectory plans;
    private final DiaryDirectory diary;
    private final AccessPolicy access;
    private final Clock clock;

    OutboxPublisher(JdbcClient jdbc, RabbitTemplate rabbit, ObjectMapper json, PlanDirectory plans,
            DiaryDirectory diary, AccessPolicy access, Clock clock) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.json = json;
        this.plans = plans;
        this.diary = diary;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Its own transaction (it is called after another one has committed). The
     * rows are locked while they are sent, and SKIP LOCKED lets a second copy
     * of the service, or the timer, pass over rows that are being sent.
     */
    @Scheduled(fixedDelayString = "${otmetka.outbox.retry-ms:5000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishPending() {
        List<Row> rows = jdbc.sql("""
                        select id, type, data, occurred_at from core.outbox
                        where published_at is null order by seq limit ? for update skip locked
                        """)
                .param(BATCH)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("data"), rs.getTimestamp("occurred_at").toInstant()))
                .list();
        for (Row row : rows) {
            try {
                Optional<Object> data = dataOf(row);
                if (data.isPresent()) {
                    send(row, data.get());
                }
            } catch (AmqpException e) {
                // Stop here to keep the order; the timer tries again in a few seconds.
                log.warn("RabbitMQ did not take event {} {}; it stays in the outbox: {}", row.type(), row.id(),
                        e.getMessage());
                return;
            }
            jdbc.sql("update core.outbox set published_at = ? where id = ?")
                    .params(clock.instant().atOffset(ZoneOffset.UTC), row.id()).update();
        }
    }

    /** Published rows are kept for a week, as a trace of what was sent. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void deleteOld() {
        jdbc.sql("delete from core.outbox where published_at < ?")
                .param(clock.instant().minus(KEEP_PUBLISHED).atOffset(ZoneOffset.UTC)).update();
    }

    private void send(Row row, Object data) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(row.id(), row.type(), row.occurredAt(), data);
        // invoke keeps both calls on one channel, so the confirmation belongs to this message.
        rabbit.invoke(operations -> {
            operations.convertAndSend(RabbitConfig.EXCHANGE, row.type(), envelope, message -> {
                message.getMessageProperties().setMessageId(row.id().toString());
                return message;
            });
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    /**
     * The message's "data". For most events it is what was stored. For
     * plan.changed only the plan id was stored and the snapshot is built now,
     * from committed data: whenever it is sent, it describes the plan as it is.
     * Empty when there is nothing left to say (the plan no longer exists).
     */
    private Optional<Object> dataOf(Row row) {
        JsonNode stored;
        try {
            stored = json.readTree(row.data());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox row " + row.id() + " is not JSON", e);
        }
        if (!"plan.changed".equals(row.type())) {
            return Optional.of(stored);
        }
        return plans.findPlan(UUID.fromString(stored.get("planId").asText())).map(plan -> {
            List<DueEntry> entries = diary
                    .plannedEntries(plan.planId(), clock.instant().minus(SNAPSHOT_LOOKBACK)).stream()
                    .map(entry -> new DueEntry(entry.entryId(), entry.scheduledAt()))
                    .toList();
            return new PlanChangedData(plan.planId(), plan.version(), plan.timezone().getId(), entries,
                    access.recipients(plan.planId()));
        });
    }
}
