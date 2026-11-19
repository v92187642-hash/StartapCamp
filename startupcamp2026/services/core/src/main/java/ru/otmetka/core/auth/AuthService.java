package ru.otmetka.core.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otmetka.core.common.NotFoundException;
import ru.otmetka.core.common.UnauthorizedException;

@Service
class AuthService {

    static final String PERSONAL_DATA = "personal_data";
    static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    /** Long on purpose: older users should not have to log in again every week. */
    static final Duration REFRESH_LIFETIME = Duration.ofDays(90);

    private final PasswordLogin passwords;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ConsentRepository consents;
    private final JwtEncoder jwtEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AuthService(PasswordLogin passwords, UserRepository users, RefreshTokenRepository refreshTokens,
            ConsentRepository consents, JwtEncoder jwtEncoder, ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.passwords = passwords;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.consents = consents;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    /** Registering logs the user in: the answer carries tokens. */
    @Transactional
    TokensResponse register(String email, String password) {
        User user = passwords.register(email, password);
        return issueTokens(user.getId(), clock.instant());
    }

    /** An unknown email and a wrong password get the same answer. */
    @Transactional
    TokensResponse login(String email, String password) {
        User user = passwords.verify(email, password)
                .orElseThrow(() -> new UnauthorizedException("Неверная почта или пароль"));
        return issueTokens(user.getId(), clock.instant());
    }

    /** A refresh token works once: it is replaced by the new one in the answer. */
    @Transactional
    TokensResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256(refreshToken))
                .filter(token -> token.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new UnauthorizedException("Войдите заново"));
        refreshTokens.delete(stored);
        return issueTokens(stored.getUserId(), now);
    }

    @Transactional
    Consent recordConsent(UUID userId, String kind, String textVersion) {
        return consents.findByUserIdAndKindAndTextVersion(userId, kind, textVersion)
                .orElseGet(() -> consents.save(new Consent(userId, kind, textVersion, clock.instant())));
    }

    @Transactional(readOnly = true)
    MeResponse me(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return new MeResponse(user.getId(), user.getEmail(), user.isPremium(), hasConsent(userId));
    }

    /**
     * Deletes the account and everything stored about the person. The other
     * modules remove their data when they hear UserDeleting; the rows of this
     * module (consents, refresh tokens) go with the user row.
     */
    @Transactional
    void deleteAccount(UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (!passwords.matches(user, password)) {
            throw new UnauthorizedException("Неверный пароль");
        }
        events.publishEvent(new UserDeleting(userId));
        users.delete(user);
        users.flush();
        events.publishEvent(new UserDeleted(userId));
    }

    /** Forgets one refresh token. Unknown tokens are ignored. */
    @Transactional
    void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Hashing.sha256(refreshToken)).ifPresent(refreshTokens::delete);
    }

    /** Forgets every refresh token of this account. Access tokens expire on their own. */
    @Transactional
    void logoutAll(UUID userId) {
        refreshTokens.deleteByUserId(userId);
    }

    boolean hasConsent(UUID userId) {
        return consents.existsByUserIdAndKind(userId, PERSONAL_DATA);
    }

    private TokensResponse issueTokens(UUID userId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER)
                .audience(java.util.List.of(SecurityConfig.AUDIENCE))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_LIFETIME))
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String refreshToken = Hashing.randomToken();
        refreshTokens.save(new RefreshToken(userId, Hashing.sha256(refreshToken), now.plus(REFRESH_LIFETIME), now));
        return new TokensResponse(accessToken, refreshToken, ACCESS_LIFETIME.toSeconds());
    }
}
