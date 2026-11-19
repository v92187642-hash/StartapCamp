package ru.otmetka.core.plans;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.auth.UserDeleting;
import ru.otmetka.core.common.BadRequestException;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.plans.PlanDtos.PlanCreateRequest;
import ru.otmetka.core.plans.PlanDtos.PlanItemCreateRequest;
import ru.otmetka.core.plans.PlanDtos.PlanItemResponse;
import ru.otmetka.core.plans.PlanDtos.PlanResponse;
import ru.otmetka.core.sharing.AccessPolicy;
import ru.otmetka.core.sharing.PlanRole;

@Service
class PlanService implements PlanDirectory {

    /** Entries are created ahead for the whole plan, so its length is limited. */
    static final int MAX_DAYS = 366;
    static final int MAX_ITEMS = 20;

    private final PlanRepository plans;
    private final PlanItemRepository items;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PlanService(PlanRepository plans, PlanItemRepository items, AccessPolicy access,
            ApplicationEventPublisher events, Clock clock) {
        this.plans = plans;
        this.items = items;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<PlanResponse> list(UUID userId) {
        List<PlanResponse> result = new ArrayList<>();
        for (Plan plan : plans.findByOwnerId(userId)) {
            result.add(PlanResponse.of(plan, PlanRole.OWNER, null));
        }
        for (Plan plan : plans.findAllById(access.sharedPlanIds(userId))) {
            result.add(PlanResponse.of(plan, PlanRole.VIEWER, null));
        }
        return result;
    }

    @Transactional(readOnly = true)
    PlanResponse get(UUID userId, UUID planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("План не найден"));
        PlanRole role = access.requireCanView(userId, plan.toRef());
        return PlanResponse.of(plan, role, items.findByPlanIdOrderByPosition(planId));
    }

    @Transactional
    PlanResponse create(UUID userId, PlanCreateRequest request) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timezone());
        } catch (DateTimeException e) {
            throw new BadRequestException("Неизвестный часовой пояс");
        }
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new BadRequestException("Дата окончания раньше даты начала");
        }
        if (request.startsOn().plusDays(MAX_DAYS).isBefore(request.endsOn())) {
            throw new BadRequestException("План не может быть длиннее года");
        }
        String title = request.title() == null || request.title().isBlank()
                ? "План " + (plans.countByOwnerId(userId) + 1)
                : request.title().trim();

        Plan plan = plans.saveAndFlush(new Plan(userId, title, request.startsOn(), request.endsOn(), zone,
                clock.instant()));
        List<PlanItem> created = new ArrayList<>();
        if (request.items() != null) {
            for (PlanItemCreateRequest item : request.items()) {
                created.add(addItem(plan, created.size(), item));
            }
        }
        events.publishEvent(new PlanChanged(plan.getId()));
        return PlanResponse.of(plan, PlanRole.OWNER, created);
    }

    @Transactional
    PlanItemResponse addItem(UUID userId, UUID planId, PlanItemCreateRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("План не найден"));
        access.requireOwner(userId, plan.toRef());
        int position = items.countByPlanId(planId);
        if (position >= MAX_ITEMS) {
            throw new BadRequestException("В плане может быть не больше " + MAX_ITEMS + " назначений");
        }
        PlanItem item = addItem(plan, position, request);
        plan.touch();
        events.publishEvent(new PlanChanged(planId));
        return PlanItemResponse.of(item);
    }

    private PlanItem addItem(Plan plan, int position, PlanItemCreateRequest request) {
        Set<DayOfWeek> days = request.daysOfWeek().stream().map(DayOfWeek::of).collect(Collectors.toSet());
        // Flushed so that the row exists when the diary inserts entries that point to it.
        PlanItem item = items.saveAndFlush(new PlanItem(plan.getId(), position, request.timeOfDay(), days));
        events.publishEvent(new PlanItemAdded(plan.getId(), item.getId(), plan.getStartsOn(), plan.getEndsOn(),
                ZoneId.of(plan.getTimezone()), item.getTimeOfDay(), days));
        return item;
    }

    /** An account is being deleted: its plans go, and with them (by cascade) items, entries, shares, questions. */
    @EventListener
    @Order(2) // after sharing and questions have removed the person's traces on other plans
    void on(UserDeleting event) {
        for (Plan plan : plans.findByOwnerId(event.userId())) {
            plans.delete(plan);
            events.publishEvent(new PlanDeleted(plan.getId()));
        }
        plans.flush();
    }

    // ---- PlanDirectory: what other modules may ask ----

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanRef> findPlan(UUID planId) {
        return plans.findById(planId).map(Plan::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanItemRef> findItem(UUID itemId) {
        return items.findById(itemId).map(PlanItem::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanItemRef> itemsOf(UUID planId) {
        return items.findByPlanIdOrderByPosition(planId).stream().map(PlanItem::toRef).toList();
    }

    @Override
    @Transactional
    public void touch(UUID planId) {
        plans.findById(planId).ifPresent(plan -> {
            plan.touch();
            events.publishEvent(new PlanChanged(planId));
        });
    }
}
