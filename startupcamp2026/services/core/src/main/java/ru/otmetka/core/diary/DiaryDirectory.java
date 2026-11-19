package ru.otmetka.core.diary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public interface of the diary module. */
public interface DiaryDirectory {

    record PlannedEntry(UUID entryId, Instant scheduledAt) {
    }

    record DiaryLine(String label, Instant scheduledAt, boolean marked, Instant markedAt, boolean corrected) {
    }

    /** Every entry of the plan scheduled in [from, to), in time order, for the doctor report. */
    List<DiaryLine> entriesBetween(UUID planId, Instant from, Instant to);

    /** Entries of the plan that are still planned and scheduled at or after the given time. */
    List<PlannedEntry> plannedEntries(UUID planId, Instant from);
}
