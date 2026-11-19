package ru.otmetka.core.plans;

import java.util.UUID;

/** Raised inside the transaction whenever a plan, its items or its shares change. */
public record PlanChanged(UUID planId) {
}
