package ru.otmetka.core.reports;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The one synchronous call from core to notifier (docs/architecture.md,
 * boundary 4): a report is a request with an immediate answer, which an event
 * cannot give. Authenticated with a shared secret from the environment.
 */
@Component
class NotifierReportRenderer implements ReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(NotifierReportRenderer.class);

    private final RestClient client;
    private final String secret;

    NotifierReportRenderer(RestClient.Builder builder, @Value("${otmetka.notifier.url}") String url,
            @Value("${otmetka.notifier.secret}") String secret) {
        // Plain HTTP/1.1 on purpose. Java's newer client first offers an upgrade to HTTP/2,
        // and notifier's server (uvicorn) then loses the request body.
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(3));
        http.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.baseUrl(url).requestFactory(http).build();
        this.secret = secret;
    }

    @Override
    public byte[] render(ReportData data) {
        try {
            return client.post()
                    .uri("/internal/reports")
                    .header("X-Internal-Secret", secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(data)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            // notifier is down or refused the request: the user can try again later.
            log.error("Report could not be rendered", e);
            throw new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE, "Не удалось подготовить отчёт. Попробуйте позже"), e);
        }
    }
}
