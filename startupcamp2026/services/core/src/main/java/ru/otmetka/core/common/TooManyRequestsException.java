package ru.otmetka.core.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Answers 429 with a problem+json body. The detail is Russian text for the user. */
public class TooManyRequestsException extends ErrorResponseException {

    public TooManyRequestsException(String detail) {
        super(HttpStatus.TOO_MANY_REQUESTS, ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail), null);
    }
}
