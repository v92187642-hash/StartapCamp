package ru.otmetka.core.plans;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ru.otmetka.core.sharing.PlanRole;

/** Request and response bodies of the plan endpoints, as in api/openapi.yaml. */
final class PlanDtos {

    private PlanDtos() {
    }

    record PlanCreateRequest(
            @Size(max = 100) String title,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @NotNull String timezone,
            @Size(max = 20) List<@Valid PlanItemCreateRequest> items) {
    }

    record PlanItemCreateRequest(
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime timeOfDay,
            @NotEmpty Set<@NotNull @Min(1) @Max(7) Integer> daysOfWeek) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlanResponse(UUID id, String title, LocalDate startsOn, LocalDate endsOn, String timezone,
            long version, PlanRole role, List<PlanItemResponse> items) {

        static PlanResponse of(Plan plan, PlanRole role, List<PlanItem> items) {
            return new PlanResponse(plan.getId(), plan.getTitle(), plan.getStartsOn(), plan.getEndsOn(),
                    plan.getTimezone(), plan.getVersion(), role,
                    items == null ? null : items.stream().map(PlanItemResponse::of).toList());
        }
    }

    record PlanItemResponse(UUID id, String label, @JsonFormat(pattern = "HH:mm") LocalTime timeOfDay,
            List<Integer> daysOfWeek, int position) {

        static PlanItemResponse of(PlanItem item) {
            return new PlanItemResponse(item.getId(), item.getLabel(), item.getTimeOfDay(), item.dayNumbers(),
                    item.getPosition());
        }
    }
}
