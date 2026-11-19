package ru.otmetka.core.sharing;

import com.fasterxml.jackson.annotation.JsonValue;

/** What the caller is to a plan. */
public enum PlanRole {
    OWNER("owner"),
    VIEWER("viewer");

    private final String value;

    PlanRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
