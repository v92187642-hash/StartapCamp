package ru.otmetka.core.diary;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of the append-only record of what happened to an entry's mark. */
@Entity
@Table(name = "mark_history")
class MarkHistory {

    static final String MARKED = "marked";
    static final String CORRECTED = "corrected";
    static final String UNDONE = "undone";

    @Id
    private UUID id;

    private UUID entryId;

    private String action;

    private Instant markedAt;

    private UUID actorId;

    private Instant at;

    protected MarkHistory() {
        // for JPA
    }

    MarkHistory(UUID entryId, String action, Instant markedAt, UUID actorId, Instant at) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.action = action;
        this.markedAt = markedAt;
        this.actorId = actorId;
        this.at = at;
    }
}
