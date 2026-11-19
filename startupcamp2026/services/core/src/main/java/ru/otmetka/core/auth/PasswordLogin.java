package ru.otmetka.core.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.otmetka.core.common.BadRequestException;
import ru.otmetka.core.common.ConflictException;
import ru.otmetka.core.common.TooManyRequestsException;

/**
 * The way to prove who the user is: an email and a password. Passwords are
 * stored as BCrypt hashes in users.password_hash. Failed attempts are counted
 * in Redis, see docs/architecture.md:
 *   login:fail:{email}  count of failed logins, 15 minutes
 * Another login method (phone, external) would be a class next to this one.
 */
@Component
class PasswordLogin {

    static final int MIN_LENGTH = 8;
    /** BCrypt reads no more than 72 bytes of a password. */
    static final int MAX_BYTES = 72;
    static final int MAX_FAILURES = 10;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final Clock clock;
    /** Compared against when the email is unknown, so that the answer takes the same time. */
    private final String hashOfNothing;

    PasswordLogin(UserRepository users, PasswordEncoder encoder, StringRedisTemplate redis, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.redis = redis;
        this.clock = clock;
        this.hashOfNothing = encoder.encode(Hashing.randomToken());
    }

    /** Creates the account. The address is not confirmed by email. */
    User register(String rawEmail, String password) {
        String email = UserDirectory.normalizeEmail(rawEmail);
        if (TestAccounts.isReserved(email) || users.findByEmail(email).isPresent()) {
            throw new ConflictException("Этот адрес уже зарегистрирован. Войдите с паролем");
        }
        return users.save(new User(email, hash(password), clock.instant()));
    }

    /** Returns the user when the email and the password match. */
    Optional<User> verify(String rawEmail, String password) {
        String email = UserDirectory.normalizeEmail(rawEmail);
        String failKey = "login:fail:" + email;

        // Counting before comparing: a guess is paid for even when requests race.
        Long failures = redis.opsForValue().increment(failKey);
        if (failures != null && failures == 1) {
            redis.expire(failKey, FAILURE_WINDOW);
        }
        if (failures != null && failures > MAX_FAILURES) {
            throw new TooManyRequestsException("Слишком много попыток входа. Попробуйте через 15 минут");
        }

        Optional<User> user = users.findByEmail(email);
        String stored = user.map(User::getPasswordHash).orElse(null);
        boolean matches = encoder.matches(password, stored != null ? stored : hashOfNothing) && stored != null;
        if (!matches) {
            return Optional.empty();
        }
        redis.delete(failKey);
        return user;
    }

    /** Checks a password without counting it as a login attempt. */
    boolean matches(User user, String password) {
        String stored = user.getPasswordHash();
        return stored != null && encoder.matches(password, stored);
    }

    String hash(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException("Пароль должен быть не короче " + MIN_LENGTH + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("Пароль слишком длинный");
        }
        return encoder.encode(password);
    }
}
