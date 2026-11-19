package ru.otmetka.core.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public interface of the auth module: what other modules may ask about users. */
@Component
public class UserDirectory {

    private final UserRepository users;

    UserDirectory(UserRepository users) {
        this.users = users;
    }

    public Optional<String> emailOf(UUID userId) {
        return users.findById(userId).map(User::getEmail);
    }

    public boolean isPremium(UUID userId) {
        return users.findById(userId).map(User::isPremium).orElse(false);
    }

    /** Emails are stored trimmed and in lower case; compare and store them only in this form. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
