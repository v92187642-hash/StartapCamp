package ru.otmetka.core.questions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public interface of the questions module. */
public interface QuestionDirectory {

    record QuestionLine(String text, Instant createdAt, Instant askedAt) {
    }

    /** Questions of the plan, oldest first, for the doctor report. */
    List<QuestionLine> questionsOf(UUID planId);
}
