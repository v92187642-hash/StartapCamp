package ru.otmetka.core.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The "data" part of each message, exactly as api/asyncapi.yaml describes it.
 * Ids and times only: no labels, no health data.
 */
final class Messages {

    private Messages() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EntryMarkedData(UUID entryId, UUID planId, String status, Instant markedAt) {
    }

    record PlanChangedData(UUID planId, long version, String timezone, List<DueEntry> entries,
            List<UUID> recipients) {
    }

    record DueEntry(UUID entryId, Instant dueAt) {
    }

    record PlanDeletedData(UUID planId) {
    }

    record UserDeletedData(UUID userId) {
    }

    record DeviceRegisteredData(UUID userId, String provider, String token) {
    }
}
