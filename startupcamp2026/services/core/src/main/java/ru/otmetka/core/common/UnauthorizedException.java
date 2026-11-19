package ru.otmetka.core.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Answers 401 with a problem+json body. The detail is Russian text for the user. */
public class UnauthorizedException extends ErrorResponseException {

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail), null);
    }
}
