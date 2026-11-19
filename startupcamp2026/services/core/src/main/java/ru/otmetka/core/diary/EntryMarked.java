package ru.otmetka.core.diary;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised inside the transaction when an entry is marked, corrected or a mark
 * is undone. status is the entry's status after the change; markedAt is null
 * when the mark was undone.
 */
public record EntryMarked(UUID entryId, UUID planId, EntryStatus status, Instant markedAt) {
}
