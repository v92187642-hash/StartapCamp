package ru.otmetka.core.auth;

import java.util.UUID;

/** Raised when an account has been deleted; announced to other services after the commit. */
public record UserDeleted(UUID userId) {
}
