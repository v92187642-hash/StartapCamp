package ru.otmetka.core.diary;

import java.time.Instant;

/** Body of PUT /entries/{id}/mark. The whole body is optional. */
record MarkRequest(Instant markedAt) {
}
