package ru.otmetka.core.reports;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.otmetka.core.auth.PremiumPolicy;
import ru.otmetka.core.common.BadRequestException;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.diary.DiaryDirectory;
import ru.otmetka.core.diary.DiaryDirectory.DiaryLine;
import ru.otmetka.core.plans.PlanDirectory;
import ru.otmetka.core.plans.PlanRef;
import ru.otmetka.core.questions.QuestionDirectory;
import ru.otmetka.core.sharing.AccessPolicy;

/** Collects the data of the doctor report from the other modules and has it rendered. */
@Service
class ReportService {

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", RU);

    private final PlanDirectory plans;
    private final DiaryDirectory diary;
    private final QuestionDirectory questions;
    private final AccessPolicy access;
    private final PremiumPolicy premium;
    private final ReportRenderer renderer;
    private final Clock clock;

    ReportService(PlanDirectory plans, DiaryDirectory diary, QuestionDirectory questions, AccessPolicy access,
            PremiumPolicy premium, ReportRenderer renderer, Clock clock) {
        this.plans = plans;
        this.diary = diary;
        this.questions = questions;
        this.access = access;
        this.premium = premium;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** The owner or a relative may export; the caller's account must be premium. */
    byte[] report(UUID userId, UUID planId, LocalDate from, LocalDate to) {
        PlanRef plan = plans.findPlan(planId).orElseThrow(() -> new NotFoundException("План не найден"));
        access.requireCanView(userId, plan);
        premium.requirePremium(userId);
        return renderer.render(collect(plan, from, to));
    }

    ReportData collect(PlanRef plan, LocalDate requestedFrom, LocalDate requestedTo) {
        ZoneId zone = plan.timezone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        // Defaults: from the start of the plan to today, but not beyond the plan.
        LocalDate from = requestedFrom != null ? requestedFrom : plan.startsOn();
        LocalDate to = requestedTo != null ? requestedTo : (today.isBefore(plan.endsOn()) ? today : plan.endsOn());
        if (to.isBefore(from)) {
            throw new BadRequestException("Дата окончания раньше даты начала");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new BadRequestException("Отчёт можно построить не больше чем за год");
        }

        Map<LocalDate, List<ReportData.Row>> byDay = new LinkedHashMap<>();
        for (DiaryLine line : diary.entriesBetween(plan.planId(), from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant())) {
            String state = line.marked() ? "marked" : line.scheduledAt().isBefore(now) ? "no_mark" : "planned";
            byDay.computeIfAbsent(LocalDate.ofInstant(line.scheduledAt(), zone), day -> new ArrayList<>())
                    .add(new ReportData.Row(
                            TIME.format(line.scheduledAt().atZone(zone)),
                            line.label(),
                            state,
                            line.markedAt() == null ? null : TIME.format(line.markedAt().atZone(zone)),
                            line.corrected()));
        }
        List<ReportData.Day> days = byDay.entrySet().stream()
                .map(day -> new ReportData.Day(DAY.format(day.getKey()), day.getValue()))
                .toList();

        List<ReportData.QuestionRow> questionRows = questions.questionsOf(plan.planId()).stream()
                .map(q -> new ReportData.QuestionRow(q.text(), DATE.format(q.createdAt().atZone(zone)),
                        q.askedAt() != null))
                .toList();

        return new ReportData(plan.title(), DATE.format(from) + " — " + DATE.format(to),
                DATE_TIME.format(now.atZone(zone)), zone.getId(), days, questionRows);
    }
}
