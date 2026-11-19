package ru.otmetka.core.auth;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller's user id from the access token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
