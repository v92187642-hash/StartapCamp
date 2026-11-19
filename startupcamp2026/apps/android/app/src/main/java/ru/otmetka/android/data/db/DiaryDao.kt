package ru.otmetka.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Query("SELECT * FROM plans ORDER BY startsOn DESC, title")
    fun plans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :planId")
    fun plan(planId: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :planId")
    suspend fun planNow(planId: String): PlanEntity?

    @Query("SELECT * FROM entries WHERE planId = :planId AND scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt, id")
    fun entries(planId: String, from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :entryId")
    suspend fun entry(entryId: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE pending IS NOT NULL")
    suspend fun pendingEntries(): List<EntryEntity>

    /** Entries of the user's own plans from this moment on: candidates for reminders. */
    @Query(
        """SELECT entries.* FROM entries JOIN plans ON plans.id = entries.planId
           WHERE plans.isOwner = 1 AND entries.scheduledAt > :from ORDER BY entries.scheduledAt LIMIT :limit"""
    )
    suspend fun ownEntriesAfter(from: Long, limit: Int): List<EntryEntity>

    @Upsert
    suspend fun upsertPlans(plans: List<PlanEntity>)

    @Upsert
    suspend fun upsertEntries(entries: List<EntryEntity>)

    @Upsert
    suspend fun upsertEntry(entry: EntryEntity)

    @Query("DELETE FROM plans WHERE id NOT IN (:keep)")
    suspend fun deletePlansExcept(keep: List<String>)

    @Query("DELETE FROM entries WHERE planId NOT IN (:keep)")
    suspend fun deleteEntriesOfOtherPlans(keep: List<String>)

    @Query("DELETE FROM entries WHERE planId = :planId AND scheduledAt >= :from AND scheduledAt < :to AND pending IS NULL AND id NOT IN (:keep)")
    suspend fun deleteStaleEntries(planId: String, from: Long, to: Long, keep: List<String>)

    @Query("DELETE FROM plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM entries")
    suspend fun deleteAllEntries()

    @Transaction
    suspend fun replacePlans(plans: List<PlanEntity>) {
        val ids = plans.map { it.id }
        deletePlansExcept(ids)
        deleteEntriesOfOtherPlans(ids)
        upsertPlans(plans)
    }

    /**
     * Stores what the server returned for a range of a plan. An entry with a
     * change the server has not seen yet keeps the phone's version.
     */
    @Transaction
    suspend fun replaceEntries(planId: String, from: Long, to: Long, fromServer: List<EntryEntity>) {
        val pendingIds = pendingEntries().map { it.id }.toSet()
        deleteStaleEntries(planId, from, to, fromServer.map { it.id })
        upsertEntries(fromServer.filter { it.id !in pendingIds })
    }

    @Transaction
    suspend fun clear() {
        deleteAllEntries()
        deleteAllPlans()
    }
}
