package ru.otmetka.core.plans;

import java.time.LocalTime;
import java.util.UUID;

/** What other modules may know about a plan item. */
public record PlanItemRef(UUID itemId, UUID planId, String label, LocalTime timeOfDay) {
}
