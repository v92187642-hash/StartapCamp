package ru.otmetka.core.diary;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import ru.otmetka.core.plans.PlanItemRef;

/** The Entry schema of api/openapi.yaml. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record EntryResponse(
        UUID id,
        UUID planId,
        UUID planItemId,
        String label,
        Instant scheduledAt,
        EntryStatus status,
        Instant markedAt,
        UUID markedBy,
        Instant correctedAt) {

    static EntryResponse of(Entry entry, PlanItemRef item) {
        return new EntryResponse(
                entry.getId(),
                item.planId(),
                item.itemId(),
                item.label(),
                entry.getScheduledAt(),
                entry.getStatus(),
                entry.getMarkedAt(),
                entry.getMarkedBy(),
                entry.getCorrectedAt());
    }
}
