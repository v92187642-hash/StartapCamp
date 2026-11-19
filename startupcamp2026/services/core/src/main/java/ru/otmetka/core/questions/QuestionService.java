package ru.otmetka.core.questions;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.auth.UserDeleting;
import ru.otmetka.core.common.ForbiddenException;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.plans.PlanDirectory;
import ru.otmetka.core.plans.PlanRef;
import ru.otmetka.core.sharing.AccessPolicy;
import ru.otmetka.core.sharing.PlanRole;

/**
 * Questions for the doctor. The product stores and lists them and does
 * nothing else with the text: no answers, no advice, no analysis.
 */
@Service
class QuestionService implements QuestionDirectory {

    private final QuestionRepository questions;
    private final PlanDirectory plans;
    private final AccessPolicy access;
    private final Clock clock;

    QuestionService(QuestionRepository questions, PlanDirectory plans, AccessPolicy access, Clock clock) {
        this.questions = questions;
        this.plans = plans;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<QuestionResponse> list(UUID userId, UUID planId) {
        access.requireCanView(userId, plan(planId));
        return questions.findByPlanIdOrderByCreatedAt(planId).stream()
                .map(question -> QuestionResponse.of(question, userId))
                .toList();
    }

    /** The owner and relatives may both add questions. */
    @Transactional
    QuestionResponse add(UUID userId, UUID planId, String text) {
        access.requireCanView(userId, plan(planId));
        Question question = questions.save(new Question(planId, userId, text.trim(), clock.instant()));
        return QuestionResponse.of(question, userId);
    }

    /** Only the owner goes to the visit, so only the owner says what was asked. */
    @Transactional
    QuestionResponse setAsked(UUID userId, UUID questionId, boolean asked) {
        Question question = question(questionId);
        access.requireOwner(userId, plan(question.getPlanId()));
        if (asked != (question.getAskedAt() != null)) {
            question.setAskedAt(asked ? clock.instant() : null);
        }
        return QuestionResponse.of(question, userId);
    }

    @Transactional
    void delete(UUID userId, UUID questionId) {
        Question question = question(questionId);
        PlanRole role = access.requireCanView(userId, plan(question.getPlanId()));
        if (role != PlanRole.OWNER && !question.getAuthorId().equals(userId)) {
            throw new ForbiddenException("Удалить вопрос может его автор или владелец плана");
        }
        questions.delete(question);
    }

    /** An account is being deleted: the questions the person wrote on other people's plans go too. */
    @EventListener
    @Order(1)
    void on(UserDeleting event) {
        questions.deleteAll(questions.findByAuthorId(event.userId()));
        questions.flush();
    }

    private Question question(UUID questionId) {
        return questions.findById(questionId).orElseThrow(() -> new NotFoundException("Вопрос не найден"));
    }

    private PlanRef plan(UUID planId) {
        return plans.findPlan(planId).orElseThrow(() -> new NotFoundException("План не найден"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionLine> questionsOf(UUID planId) {
        return questions.findByPlanIdOrderByCreatedAt(planId).stream()
                .map(q -> new QuestionLine(q.getText(), q.getCreatedAt(), q.getAskedAt()))
                .toList();
    }
}
