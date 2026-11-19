package ru.otmetka.core.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Answers 403 with a problem+json body. The detail is Russian text for the user. */
public class ForbiddenException extends ErrorResponseException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail), null);
    }
}
