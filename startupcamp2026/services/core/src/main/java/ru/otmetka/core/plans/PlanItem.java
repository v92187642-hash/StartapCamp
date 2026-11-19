package ru.otmetka.core.plans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_items")
class PlanItem {

    @Id
    private UUID id;

    private UUID planId;

    /** Always generated («Назначение 1»); a user cannot type it, so no drug name gets in. */
    private String label;

    private LocalTime timeOfDay;

    /** ISO day numbers: 1 is Monday, 7 is Sunday. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    private short[] daysOfWeek;

    private int position;

    protected PlanItem() {
        // for JPA
    }

    PlanItem(UUID planId, int position, LocalTime timeOfDay, Set<DayOfWeek> days) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.position = position;
        this.label = "Назначение " + (position + 1);
        this.timeOfDay = timeOfDay;
        this.daysOfWeek = new short[days.size()];
        int i = 0;
        for (DayOfWeek day : new TreeSet<>(days)) {
            this.daysOfWeek[i++] = (short) day.getValue();
        }
    }

    Set<DayOfWeek> days() {
        return dayNumbers().stream().map(DayOfWeek::of).collect(Collectors.toSet());
    }

    List<Integer> dayNumbers() {
        List<Integer> numbers = new java.util.ArrayList<>();
        for (short day : daysOfWeek) {
            numbers.add((int) day);
        }
        return numbers;
    }

    PlanItemRef toRef() {
        return new PlanItemRef(id, planId, label, timeOfDay);
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    String getLabel() {
        return label;
    }

    LocalTime getTimeOfDay() {
        return timeOfDay;
    }

    int getPosition() {
        return position;
    }
}
