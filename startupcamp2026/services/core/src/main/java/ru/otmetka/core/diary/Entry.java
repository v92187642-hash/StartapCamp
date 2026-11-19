package ru.otmetka.core.diary;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entries")
class Entry {

    @Id
    private UUID id;

    private UUID planItemId;

    private Instant scheduledAt;

    private EntryStatus status;

    private Instant markedAt;

    private UUID markedBy;

    private Instant correctedAt;

    protected Entry() {
        // for JPA
    }

    /**
     * Marks the entry, or corrects the time of an existing mark.
     *
     * @param at  when the prescription was followed
     * @param now the current time, recorded as correctedAt when a mark changes
     * @return what happened, as a MarkHistory action; null when nothing changed
     */
    String mark(Instant at, UUID userId, Instant now) {
        String action = MarkHistory.MARKED;
        if (status == EntryStatus.MARKED) {
            if (at.equals(markedAt)) {
                return null;
            }
            correctedAt = now;
            action = MarkHistory.CORRECTED;
        }
        status = EntryStatus.MARKED;
        markedAt = at;
        markedBy = userId;
        return action;
    }

    /**
     * Withdraws the mark. The withdrawn time stays in mark_history.
     *
     * @return false when there was no mark
     */
    boolean unmark(Instant now) {
        if (status != EntryStatus.MARKED) {
            return false;
        }
        status = EntryStatus.PLANNED;
        markedAt = null;
        markedBy = null;
        correctedAt = now;
        return true;
    }

    UUID getId() {
        return id;
    }

    UUID getPlanItemId() {
        return planItemId;
    }

    Instant getScheduledAt() {
        return scheduledAt;
    }

    EntryStatus getStatus() {
        return status;
    }

    Instant getMarkedAt() {
        return markedAt;
    }

    UUID getMarkedBy() {
        return markedBy;
    }

    Instant getCorrectedAt() {
        return correctedAt;
    }
}
