package ru.otmetka.core.auth;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Six accounts with known passwords, for demos and manual testing:
 * testaccount1@otmetka.test ... testaccount6@otmetka.test, passwords
 * otmetka-test-1 ... otmetka-test-6. Consent is already recorded and all are
 * premium.
 *
 * With SEED_TEST_ACCOUNTS=true they are created at start if missing. Nothing
 * here ever deletes or changes an existing one, so their plans and marks
 * survive restarts and a later false. Nobody can register these addresses.
 */
@Component
class TestAccounts implements ApplicationRunner {

    static final int COUNT = 6;
    /** Version of the consent text the clients show; see ConsentPage.tsx and SessionRepository.kt. */
    static final String CONSENT_TEXT_VERSION = "2026-10";

    private static final Logger log = LoggerFactory.getLogger(TestAccounts.class);
    private static final String DOMAIN = "@otmetka.test";

    private final boolean enabled;
    private final UserRepository users;
    private final PasswordLogin passwords;
    private final AuthService auth;
    private final Clock clock;

    TestAccounts(@Value("${otmetka.test-accounts.enabled}") boolean enabled, UserRepository users,
            PasswordLogin passwords, AuthService auth, Clock clock) {
        this.enabled = enabled;
        this.users = users;
        this.passwords = passwords;
        this.auth = auth;
        this.clock = clock;
    }

    static String email(int number) {
        return "testaccount" + number + DOMAIN;
    }

    static String password(int number) {
        return "otmetka-test-" + number;
    }

    static boolean isReserved(String normalizedEmail) {
        return normalizedEmail.endsWith(DOMAIN);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            create();
            log.warn("Test accounts with known passwords exist: {} ... {}", email(1), email(COUNT));
        }
    }

    /** Creates the accounts that are missing; existing ones are left as they are. */
    @Transactional
    void create() {
        for (int number = 1; number <= COUNT; number++) {
            if (users.findByEmail(email(number)).isPresent()) {
                continue;
            }
            User user = new User(email(number), passwords.hash(password(number)), clock.instant());
            user.setPremium(true);
            users.save(user);
            auth.recordConsent(user.getId(), AuthService.PERSONAL_DATA, CONSENT_TEXT_VERSION);
        }
    }
}
