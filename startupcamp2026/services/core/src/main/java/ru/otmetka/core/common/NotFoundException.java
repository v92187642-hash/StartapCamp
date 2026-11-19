package ru.otmetka.core.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Answers 404 with a problem+json body. The detail is Russian text for the user. */
public class NotFoundException extends ErrorResponseException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail), null);
    }
}
