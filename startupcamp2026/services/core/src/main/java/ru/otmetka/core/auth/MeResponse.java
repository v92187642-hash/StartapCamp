package ru.otmetka.core.auth;

import java.util.UUID;

record MeResponse(UUID id, String email, boolean premium, boolean consentAccepted) {
}
