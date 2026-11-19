package ru.otmetka.core.diary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.otmetka.core.plans.PlanItemAdded;

/**
 * Creates the entries of a new plan item: one for every matching day from now
 * to the end of the plan. Moments that have already passed get no entry, so a
 * plan created in the afternoon does not start with a "no mark" in the morning.
 *
 * Runs inside the transaction that adds the item.
 */
@Component
class EntryGenerator {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    EntryGenerator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener
    void on(PlanItemAdded item) {
        Instant now = clock.instant();
        List<Object[]> rows = new ArrayList<>();
        for (LocalDate day = item.startsOn(); !day.isAfter(item.endsOn()); day = day.plusDays(1)) {
            if (!item.daysOfWeek().contains(day.getDayOfWeek())) {
                continue;
            }
            Instant scheduledAt = day.atTime(item.timeOfDay()).atZone(item.timezone()).toInstant();
            if (scheduledAt.isAfter(now)) {
                rows.add(new Object[] {UUID.randomUUID(), item.itemId(), scheduledAt.atOffset(ZoneOffset.UTC)});
            }
        }
        // One round trip for the whole plan instead of one per entry.
        jdbc.batchUpdate("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, ?)", rows);
    }
}
