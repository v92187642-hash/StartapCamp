package ru.otmetka.core.plans;

import java.util.UUID;

/** Raised when a plan has been deleted; announced to other services after the commit. */
public record PlanDeleted(UUID planId) {
}
