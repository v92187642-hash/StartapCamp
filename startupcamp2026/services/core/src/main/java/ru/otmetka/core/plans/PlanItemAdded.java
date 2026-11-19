package ru.otmetka.core.plans;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Raised inside the transaction that adds an item. The diary creates its entries from it. */
public record PlanItemAdded(UUID planId, UUID itemId, LocalDate startsOn, LocalDate endsOn, ZoneId timezone,
        LocalTime timeOfDay, Set<DayOfWeek> daysOfWeek) {
}
