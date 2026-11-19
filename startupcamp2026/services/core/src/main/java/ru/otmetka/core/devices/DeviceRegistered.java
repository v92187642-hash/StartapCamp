package ru.otmetka.core.devices;

import java.util.UUID;

/** Raised when a client registers a push token. core does not store tokens; notifier does. */
public record DeviceRegistered(UUID userId, String provider, String token) {
}
