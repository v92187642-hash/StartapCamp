package ru.otmetka.core.plans;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;
import ru.otmetka.core.plans.PlanDtos.PlanCreateRequest;
import ru.otmetka.core.plans.PlanDtos.PlanItemCreateRequest;
import ru.otmetka.core.plans.PlanDtos.PlanItemResponse;
import ru.otmetka.core.plans.PlanDtos.PlanResponse;

@RestController
class PlanController {

    private final PlanService plans;

    PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping("/plans")
    List<PlanResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return plans.list(CurrentUser.id(jwt));
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    PlanResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanCreateRequest request) {
        return plans.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/plans/{planId}")
    PlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return plans.get(CurrentUser.id(jwt), planId);
    }

    @PostMapping("/plans/{planId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    PlanItemResponse addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
            @Valid @RequestBody PlanItemCreateRequest request) {
        return plans.addItem(CurrentUser.id(jwt), planId, request);
    }
}
