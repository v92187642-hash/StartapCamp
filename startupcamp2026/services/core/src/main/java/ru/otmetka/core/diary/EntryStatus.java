package ru.otmetka.core.diary;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * "No mark" is not a status: an entry is shown as "no mark" when it is
 * PLANNED and its scheduled time has passed.
 */
public enum EntryStatus {
    PLANNED("planned"),
    MARKED("marked");

    private final String value;

    EntryStatus(String value) {
        this.value = value;
    }

    /** The spelling used in JSON and in the database. */
    @JsonValue
    public String value() {
        return value;
    }

    @Converter(autoApply = true)
    static class DbConverter implements AttributeConverter<EntryStatus, String> {

        @Override
        public String convertToDatabaseColumn(EntryStatus status) {
            return status.value;
        }

        @Override
        public EntryStatus convertToEntityAttribute(String value) {
            for (EntryStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown entry status: " + value);
        }
    }
}
