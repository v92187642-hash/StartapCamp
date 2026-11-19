package ru.otmetka.core.auth;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

/**
 * The one place that knows which features are paid. Today: inviting a
 * relative and the doctor report. The rule is "the caller's account is
 * premium"; see the open decision about who pays in docs/state.md.
 */
@Component
public class PremiumPolicy {

    private final UserDirectory users;
    private final boolean enforced;

    PremiumPolicy(UserDirectory users, @Value("${otmetka.premium.enforced}") boolean enforced) {
        this.users = users;
        this.enforced = enforced;
    }

    /** Answers 403 with problem type "premium-required" for a free account. */
    public void requirePremium(UUID userId) {
        if (enforced && !users.isPremium(userId)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "Эта возможность доступна по подписке");
            problem.setType(URI.create("premium-required"));
            throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
        }
    }
}
