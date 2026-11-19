package ru.otmetka.core.diary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;

@RestController
class EntryController {

    private final DiaryService diary;

    EntryController(DiaryService diary) {
        this.diary = diary;
    }

    @GetMapping("/plans/{planId}/entries")
    List<EntryResponse> day(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
            @RequestParam LocalDate date, @RequestParam(defaultValue = "1") @Min(1) @Max(31) int days) {
        return diary.days(CurrentUser.id(jwt), planId, date, days);
    }

    @PutMapping("/entries/{entryId}/mark")
    EntryResponse mark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entryId,
            @RequestBody(required = false) MarkRequest request) {
        return diary.mark(CurrentUser.id(jwt), entryId, request == null ? null : request.markedAt());
    }

    @DeleteMapping("/entries/{entryId}/mark")
    EntryResponse unmark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entryId) {
        return diary.unmark(CurrentUser.id(jwt), entryId);
    }
}
