package ru.otmetka.android.data

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import ru.otmetka.android.api.models.Entry
import ru.otmetka.android.api.models.EntryStatus
import ru.otmetka.android.api.models.MarkRequest
import ru.otmetka.android.api.models.Plan
import ru.otmetka.android.api.models.PlanCreate
import ru.otmetka.android.api.models.PlanItem
import ru.otmetka.android.api.models.PlanItemCreate
import ru.otmetka.android.data.db.DiaryDao
import ru.otmetka.android.data.db.EntryEntity
import ru.otmetka.android.data.db.PlanEntity

/**
 * Plans and entries. The screens read only the phone's database, so they work
 * offline; [refresh] and [pushPending] bring the database and the server
 * together when there is a network.
 */
class DiaryRepository(private val api: ApiFactory, private val dao: DiaryDao) {

    fun plans(): Flow<List<PlanEntity>> = dao.plans()

    fun plan(planId: String): Flow<PlanEntity?> = dao.plan(planId)

    /** Entries of one calendar day in the plan's time zone. */
    fun day(planId: String, date: LocalDate, zone: ZoneId): Flow<List<EntryEntity>> = dao.entries(
        planId,
        date.atStartOfDay(zone).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    /**
     * Loads the plans and, for each, the entries from yesterday to a week
     * ahead: enough for the day view and for reminders while offline.
     *
     * @throws IOException without a network
     * @throws ApiException when the server refuses
     */
    suspend fun refresh() {
        val plans = api.plans.listPlans().bodyOrThrow()
        dao.replacePlans(plans.map { it.toEntity() })
        for (plan in plans) {
            loadDays(plan.id.toString(), ZoneId.of(plan.timezone), LocalDate.now(ZoneId.of(plan.timezone)).minusDays(1), CACHED_DAYS)
        }
    }

    /** Loads one day the user navigated to, if it is outside the cached range. */
    suspend fun refreshDay(planId: String, date: LocalDate, zone: ZoneId) = loadDays(planId, zone, date, 1)

    private suspend fun loadDays(planId: String, zone: ZoneId, from: LocalDate, days: Int) {
        val entries = api.diary.listEntries(UUID.fromString(planId), from, days).bodyOrThrow()
        dao.replaceEntries(
            planId,
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            entries.map { it.toEntity() },
        )
    }

    /**
     * Creates a plan on the server; needs a network. The plan is put into the
     * phone's database at once, so the day view can open it.
     *
     * @return the id of the new plan
     */
    suspend fun createPlan(
        title: String?,
        startsOn: LocalDate,
        endsOn: LocalDate,
        zone: ZoneId,
        items: List<PlanItemCreate>,
    ): String {
        val plan = api.plans.createPlan(PlanCreate(startsOn, endsOn, zone.id, title, items)).bodyOrThrow()
        dao.upsertPlans(listOf(plan.toEntity()))
        loadCachedDaysQuietly(plan.id.toString(), zone)
        return plan.id.toString()
    }

    /** The plan's items, from the server. */
    suspend fun planItems(planId: String): List<PlanItem> =
        api.plans.getPlan(UUID.fromString(planId)).bodyOrThrow().items.orEmpty()

    /** Adds an item on the server and loads the entries it created. */
    suspend fun addItem(planId: String, item: PlanItemCreate): PlanItem {
        val created = api.plans.addPlanItem(UUID.fromString(planId), item).bodyOrThrow()
        planZone(planId)?.let { loadCachedDaysQuietly(planId, it) }
        return created
    }

    /** The change itself succeeded; if the entries cannot be loaded now, the next sync brings them. */
    private suspend fun loadCachedDaysQuietly(planId: String, zone: ZoneId) {
        try {
            loadDays(planId, zone, LocalDate.now(zone).minusDays(1), CACHED_DAYS)
        } catch (e: IOException) {
            // Offline for a moment.
        }
    }

    suspend fun planTitle(planId: String): String? = dao.planNow(planId)?.title

    suspend fun isOwner(planId: String): Boolean = dao.planNow(planId)?.isOwner == true

    /** Marks on the phone at once; the server learns about it in [pushPending]. */
    suspend fun mark(entryId: String, now: Instant) {
        val entry = dao.entry(entryId) ?: return
        if (entry.isMarked) return
        dao.upsertEntry(entry.copy(isMarked = true, markedAt = now.toEpochMilli(), pending = EntryEntity.PENDING_MARK))
    }

    /** Changes the time of an existing mark, on the phone at once like [mark]. */
    suspend fun correct(entryId: String, markedAt: Instant) {
        val entry = dao.entry(entryId) ?: return
        if (!entry.isMarked) return
        // A mark the server never saw is still just a mark, with the new time.
        val pending = if (entry.pending == EntryEntity.PENDING_MARK) EntryEntity.PENDING_MARK else EntryEntity.PENDING_CORRECT
        dao.upsertEntry(entry.copy(markedAt = markedAt.toEpochMilli(), pending = pending))
    }

    suspend fun unmark(entryId: String) {
        val entry = dao.entry(entryId) ?: return
        if (!entry.isMarked) return
        // A mark the server never saw simply disappears; otherwise the undo has to be sent.
        val pending = if (entry.pending == EntryEntity.PENDING_MARK) null else EntryEntity.PENDING_UNMARK
        dao.upsertEntry(entry.copy(isMarked = false, markedAt = null, pending = pending))
    }

    /**
     * Sends the changes made on the phone. The time of a mark is the moment
     * the user tapped, not the moment of sending.
     *
     * @return false when the network failed and the work should be retried
     */
    suspend fun pushPending(): Boolean {
        for (entry in dao.pendingEntries()) {
            try {
                val id = UUID.fromString(entry.id)
                val response = if (entry.pending == EntryEntity.PENDING_MARK || entry.pending == EntryEntity.PENDING_CORRECT) {
                    // The server treats a new time for a marked entry as a correction.
                    val markedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(entry.markedAt!!), ZoneOffset.UTC)
                    api.diary.markEntry(id, MarkRequest(markedAt))
                } else {
                    api.diary.unmarkEntry(id)
                }
                when {
                    response.isSuccessful -> dao.upsertEntry(response.body()!!.toEntity())
                    // The server will never accept it (entry gone, access withdrawn): drop the
                    // change; the next refresh shows the server's state.
                    response.code() in 400..499 && response.code() != 401 && response.code() != 429 ->
                        dao.upsertEntry(entry.copy(pending = null))
                    else -> return false
                }
            } catch (e: IOException) {
                return false
            }
        }
        return true
    }

    /** Entries of the user's own plans that are still ahead; reminders are chosen from them. */
    suspend fun upcomingOwnEntries(now: Instant, limit: Int): List<EntryEntity> =
        dao.ownEntriesAfter(now.toEpochMilli(), limit)

    suspend fun entry(entryId: String): EntryEntity? = dao.entry(entryId)

    suspend fun planZone(planId: String): ZoneId? = dao.planNow(planId)?.let { ZoneId.of(it.timezone) }

    suspend fun clear() = dao.clear()

    private fun Plan.toEntity() = PlanEntity(
        id = id.toString(),
        title = title,
        startsOn = startsOn.toString(),
        endsOn = endsOn.toString(),
        timezone = timezone,
        isOwner = role == Plan.Role.owner,
    )

    private fun Entry.toEntity() = EntryEntity(
        id = id.toString(),
        planId = planId.toString(),
        label = label,
        scheduledAt = scheduledAt.toInstant().toEpochMilli(),
        isMarked = status == EntryStatus.marked,
        markedAt = markedAt?.toInstant()?.toEpochMilli(),
    )

    private companion object {
        const val CACHED_DAYS = 9
    }
}
