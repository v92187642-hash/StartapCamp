package ru.otmetka.core.sharing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;

@RestController
class ShareController {

    record ShareCreateRequest(@NotBlank @Email String email) {
    }

    record ShareAcceptRequest(@NotBlank String token) {
    }

    private final ShareService shares;

    ShareController(ShareService shares) {
        this.shares = shares;
    }

    @GetMapping("/plans/{planId}/shares")
    List<ShareResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return shares.list(CurrentUser.id(jwt), planId);
    }

    @PostMapping("/plans/{planId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    ShareResponse invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
            @Valid @RequestBody ShareCreateRequest request) {
        return shares.invite(CurrentUser.id(jwt), planId, request.email());
    }

    @PostMapping("/shares/accept")
    ShareResponse accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShareAcceptRequest request) {
        return shares.accept(CurrentUser.id(jwt), request.token());
    }

    @DeleteMapping("/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID shareId) {
        shares.revoke(CurrentUser.id(jwt), shareId);
    }
}
