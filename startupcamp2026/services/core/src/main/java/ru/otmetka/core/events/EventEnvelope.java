package ru.otmetka.core.events;

import java.time.Instant;
import java.util.UUID;

/** The JSON wrapper every event is sent in, see api/asyncapi.yaml. */
record EventEnvelope<T>(UUID eventId, String type, Instant occurredAt, T data) {
}
