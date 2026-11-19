package ru.otmetka.core.diary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.common.BadRequestException;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.plans.PlanDirectory;
import ru.otmetka.core.plans.PlanItemRef;
import ru.otmetka.core.plans.PlanRef;
import ru.otmetka.core.sharing.AccessPolicy;

@Service
class DiaryService implements DiaryDirectory {

    /** A phone's clock may run a little ahead of the server's. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final EntryRepository entries;
    private final MarkHistoryRepository history;
    private final PlanDirectory plans;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DiaryService(EntryRepository entries, MarkHistoryRepository history, PlanDirectory plans, AccessPolicy access,
            ApplicationEventPublisher events, Clock clock) {
        this.entries = entries;
        this.history = history;
        this.plans = plans;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /** Day view: the entries of one or more calendar days in the plan's time zone. */
    @Transactional(readOnly = true)
    List<EntryResponse> days(UUID userId, UUID planId, LocalDate date, int days) {
        PlanRef plan = plans.findPlan(planId).orElseThrow(() -> new NotFoundException("План не найден"));
        access.requireCanView(userId, plan);

        Map<UUID, PlanItemRef> items = plans.itemsOf(planId).stream()
                .collect(Collectors.toMap(PlanItemRef::itemId, Function.identity()));
        if (items.isEmpty()) {
            return List.of();
        }
        Instant from = date.atStartOfDay(plan.timezone()).toInstant();
        Instant to = date.plusDays(days).atStartOfDay(plan.timezone()).toInstant();
        return entries.findInRange(items.keySet(), from, to).stream()
                .map(entry -> EntryResponse.of(entry, items.get(entry.getPlanItemId())))
                .toList();
    }

    /**
     * Marks an entry or corrects the time of its mark.
     *
     * @param requestedAt when the prescription was followed; null means now
     */
    @Transactional
    EntryResponse mark(UUID userId, UUID entryId, Instant requestedAt) {
        Entry entry = entries.findByIdForUpdate(entryId)
                .orElseThrow(() -> new NotFoundException("Запись не найдена"));
        PlanItemRef item = requireOwner(userId, entry);

        // Postgres keeps microseconds; truncating keeps a repeated request equal to the stored value.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant at;
        if (requestedAt != null) {
            at = requestedAt.truncatedTo(ChronoUnit.MICROS);
        } else if (entry.getStatus() == EntryStatus.MARKED) {
            at = entry.getMarkedAt(); // a repeated tap keeps the first time
        } else {
            at = now;
        }
        if (at.isAfter(now.plus(CLOCK_SKEW))) {
            throw new BadRequestException("Время отметки не может быть в будущем");
        }

        String action = entry.mark(at, userId, now);
        if (action != null) {
            history.save(new MarkHistory(entryId, action, at, userId, now));
            // Reaches RabbitMQ after this transaction commits, see events/RabbitEventRelay.
            events.publishEvent(new EntryMarked(entryId, item.planId(), entry.getStatus(), entry.getMarkedAt()));
        }
        return EntryResponse.of(entry, item);
    }

    /** Undoes a mark. The entry returns to "planned"; the history keeps the withdrawn mark. */
    @Transactional
    EntryResponse unmark(UUID userId, UUID entryId) {
        Entry entry = entries.findByIdForUpdate(entryId)
                .orElseThrow(() -> new NotFoundException("Запись не найдена"));
        PlanItemRef item = requireOwner(userId, entry);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant withdrawn = entry.getMarkedAt();
        if (entry.unmark(now)) {
            history.save(new MarkHistory(entryId, MarkHistory.UNDONE, withdrawn, userId, now));
            events.publishEvent(new EntryMarked(entryId, item.planId(), entry.getStatus(), null));
        }
        return EntryResponse.of(entry, item);
    }

    private PlanItemRef requireOwner(UUID userId, Entry entry) {
        PlanItemRef item = plans.findItem(entry.getPlanItemId()).orElseThrow();
        access.requireOwner(userId, plans.findPlan(item.planId()).orElseThrow());
        return item;
    }

    // ---- DiaryDirectory: what other modules may ask ----

    @Override
    @Transactional(readOnly = true)
    public List<PlannedEntry> plannedEntries(UUID planId, Instant from) {
        List<UUID> itemIds = plans.itemsOf(planId).stream().map(PlanItemRef::itemId).toList();
        if (itemIds.isEmpty()) {
            return List.of();
        }
        return entries.findPlannedFrom(itemIds, from).stream()
                .map(entry -> new PlannedEntry(entry.getId(), entry.getScheduledAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaryLine> entriesBetween(UUID planId, Instant from, Instant to) {
        Map<UUID, PlanItemRef> items = plans.itemsOf(planId).stream()
                .collect(Collectors.toMap(PlanItemRef::itemId, Function.identity()));
        if (items.isEmpty()) {
            return List.of();
        }
        return entries.findInRange(items.keySet(), from, to).stream()
                .map(entry -> new DiaryLine(items.get(entry.getPlanItemId()).label(), entry.getScheduledAt(),
                        entry.getStatus() == EntryStatus.MARKED, entry.getMarkedAt(), entry.getCorrectedAt() != null))
                .toList();
    }
}
