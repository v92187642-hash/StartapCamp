package ru.otmetka.core.devices;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;

/**
 * Push tokens pass through core on their way to notifier: clients talk only
 * to core, and notifier, which sends the pushes, keeps the tokens.
 */
@RestController
class DeviceController {

    record DeviceRegistrationRequest(
            @NotBlank @Pattern(regexp = "fcm|rustore") String provider,
            @NotBlank @Size(max = 4096) String token) {
    }

    private final ApplicationEventPublisher events;

    DeviceController(ApplicationEventPublisher events) {
        this.events = events;
    }

    // Transactional so that the after-commit listener in events/RabbitEventRelay runs.
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRegistrationRequest request) {
        events.publishEvent(new DeviceRegistered(CurrentUser.id(jwt), request.provider(), request.token()));
    }
}
