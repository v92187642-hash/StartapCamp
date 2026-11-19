package ru.otmetka.core.questions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;

@RestController
class QuestionController {

    record QuestionCreateRequest(@NotBlank @Size(max = 500) String text) {
    }

    private final QuestionService questions;

    QuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping("/plans/{planId}/questions")
    List<QuestionResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return questions.list(CurrentUser.id(jwt), planId);
    }

    @PostMapping("/plans/{planId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    QuestionResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
            @Valid @RequestBody QuestionCreateRequest request) {
        return questions.add(CurrentUser.id(jwt), planId, request.text());
    }

    @PutMapping("/questions/{questionId}/asked")
    QuestionResponse markAsked(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        return questions.setAsked(CurrentUser.id(jwt), questionId, true);
    }

    @DeleteMapping("/questions/{questionId}/asked")
    QuestionResponse markNotAsked(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        return questions.setAsked(CurrentUser.id(jwt), questionId, false);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        questions.delete(CurrentUser.id(jwt), questionId);
    }
}
