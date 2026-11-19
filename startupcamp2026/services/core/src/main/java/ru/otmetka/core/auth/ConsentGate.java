package ru.otmetka.core.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * No data about a person is stored before they consent (152-ФЗ). Until a
 * consent record exists, every endpoint except login, consent itself and /me
 * answers 403 with problem type "consent-required".
 */
@Configuration
class ConsentGate implements WebMvcConfigurer, HandlerInterceptor {

    private final AuthService auth;

    ConsentGate(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .excludePathPatterns("/health", "/auth/**", "/consents", "/me", "/me/delete", "/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                && !auth.hasConsent(CurrentUser.id(jwt))) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "Сначала нужно согласие на обработку персональных данных");
            problem.setType(URI.create("consent-required"));
            throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
        }
        return true;
    }
}
