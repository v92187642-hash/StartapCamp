package ru.otmetka.core.reports;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otmetka.core.auth.CurrentUser;

@RestController
class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/plans/{planId}/report")
    ResponseEntity<byte[]> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        byte[] pdf = reports.report(CurrentUser.id(jwt), planId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("otmetka-report.pdf").build().toString())
                .body(pdf);
    }
}
