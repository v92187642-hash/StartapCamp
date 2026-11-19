package ru.otmetka.core.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Answers 409 with a problem+json body. The detail is Russian text for the user. */
public class ConflictException extends ErrorResponseException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail), null);
    }
}
