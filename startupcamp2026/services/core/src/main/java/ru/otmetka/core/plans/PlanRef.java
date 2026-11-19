package ru.otmetka.core.plans;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** What other modules may know about a plan. */
public record PlanRef(UUID planId, UUID ownerId, String title, LocalDate startsOn, LocalDate endsOn,
        ZoneId timezone, long version) {
}
