package ru.otmetka.core.questions;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** The Question schema of api/openapi.yaml. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record QuestionResponse(UUID id, UUID planId, String text, Instant createdAt, Instant askedAt, boolean mine) {

    static QuestionResponse of(Question question, UUID callerId) {
        return new QuestionResponse(question.getId(), question.getPlanId(), question.getText(),
                question.getCreatedAt(), question.getAskedAt(), question.getAuthorId().equals(callerId));
    }
}
