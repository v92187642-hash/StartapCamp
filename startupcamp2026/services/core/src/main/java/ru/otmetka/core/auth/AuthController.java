package ru.otmetka.core.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.common.ClientIp;

@RestController
class AuthController {

    record Credentials(@NotBlank @Email String email, @NotBlank String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record PasswordConfirm(@NotBlank String password) {
    }

    record ConsentRequest(@NotBlank @Pattern(regexp = "personal_data") String kind, @NotBlank String textVersion) {
    }

    record ConsentResponse(String kind, String textVersion, Instant acceptedAt) {
    }

    private final AuthService auth;
    private final RateLimits rateLimits;

    AuthController(AuthService auth, RateLimits rateLimits) {
        this.auth = auth;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokensResponse register(@Valid @RequestBody Credentials request, HttpServletRequest http) {
        rateLimits.checkRegister(ClientIp.of(http));
        return auth.register(request.email(), request.password());
    }

    @PostMapping("/auth/login")
    TokensResponse login(@Valid @RequestBody Credentials request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/auth/refresh")
    TokensResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }

    @PostMapping("/auth/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt) {
        auth.logoutAll(CurrentUser.id(jwt));
    }

    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.CREATED)
    ConsentResponse recordConsent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest request) {
        Consent consent = auth.recordConsent(CurrentUser.id(jwt), request.kind(), request.textVersion());
        return new ConsentResponse(consent.getKind(), consent.getTextVersion(), consent.getAcceptedAt());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(CurrentUser.id(jwt));
    }

    @PostMapping("/me/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordConfirm request) {
        auth.deleteAccount(CurrentUser.id(jwt), request.password());
    }
}
