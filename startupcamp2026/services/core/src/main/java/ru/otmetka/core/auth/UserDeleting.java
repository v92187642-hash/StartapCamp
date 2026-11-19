package ru.otmetka.core.auth;

import java.util.UUID;

/**
 * Raised inside the transaction that deletes an account, before the user row
 * goes. Every module that keeps data about a user listens and removes its own.
 */
public record UserDeleting(UUID userId) {
}
