package ru.otmetka.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The phone's copy of a plan. Dates are ISO text ("2026-10-09"). */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startsOn: String,
    val endsOn: String,
    val timezone: String,
    /** false: the user watches a relative's plan and cannot mark. */
    val isOwner: Boolean,
)

/** The phone's copy of an entry. Times are milliseconds since 1970, UTC. */
@Entity(tableName = "entries", indices = [Index("planId", "scheduledAt")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val label: String,
    val scheduledAt: Long,
    val isMarked: Boolean,
    val markedAt: Long?,
    /**
     * A change made on the phone that the server has not confirmed yet:
     * [PENDING_MARK], [PENDING_CORRECT], [PENDING_UNMARK] or null. Set offline,
     * cleared by sync.
     */
    val pending: String? = null,
) {
    companion object {
        const val PENDING_MARK = "mark"

        /** A new time for a mark the server already has. */
        const val PENDING_CORRECT = "correct"
        const val PENDING_UNMARK = "unmark"
    }
}
