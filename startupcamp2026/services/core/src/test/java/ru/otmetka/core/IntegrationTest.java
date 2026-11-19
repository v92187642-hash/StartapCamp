package ru.otmetka.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.otmetka.core.common.Mailer;
import ru.otmetka.core.reports.ReportRenderer;

/**
 * Base class for tests that need the whole application. Locally it starts
 * Postgres and Redis with Testcontainers. In GitLab CI those are sidecar
 * services on the job network ({@code OTMETKA_TEST_DATABASE=service}), so
 * nothing is published on the runner host. RabbitMQ and email are mocks.
 */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:1/unused",
        "DATABASE_USER=unused",
        "DATABASE_PASSWORD=unused",
        "RABBITMQ_HOST=localhost",
        "RABBITMQ_USER=guest",
        "RABBITMQ_PASSWORD=guest",
        "REDIS_HOST=localhost",
        "JWT_SECRET=test-secret-test-secret-test-secret-123",
        "INTERNAL_SECRET=test-internal-secret",
        "NOTIFIER_URL=http://127.0.0.1:9",
        "WEB_BASE_URL=http://localhost",
        "otmetka.rate-limit.register-per-ip=10000",
        // The retry timer is tested by calling it; in tests it must not fire by itself.
        "otmetka.outbox.retry-ms=3600000",
        // Send on the request's own thread, so a test can check right after the request.
        "otmetka.outbox.inline=true"})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final boolean SERVICE_DATABASE = "service".equals(System.getenv("OTMETKA_TEST_DATABASE"));

    static final PostgreSQLContainer<?> POSTGRES =
            SERVICE_DATABASE ? null : new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS =
            SERVICE_DATABASE ? null : new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        if (!SERVICE_DATABASE) {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void databaseAndRedis(DynamicPropertyRegistry registry) {
        if (SERVICE_DATABASE) {
            registry.add("spring.datasource.url", () -> System.getenv("DATABASE_URL"));
            registry.add("spring.datasource.username", () -> System.getenv("DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("DATABASE_PASSWORD"));
            registry.add("spring.data.redis.host", () -> System.getenv().getOrDefault("REDIS_HOST", "redis"));
            registry.add("spring.data.redis.port", () -> System.getenv().getOrDefault("REDIS_PORT", "6379"));
            return;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcClient jdbc;

    @MockitoBean
    protected RabbitTemplate rabbit;

    @MockitoBean
    protected Mailer mailer;

    /** The PDF is built by notifier; tests check the data core would send to it. */
    @MockitoBean
    protected ReportRenderer reportRenderer;

    /**
     * The outbox sends through rabbit.invoke(...), which on a mock would do
     * nothing; make it run the callback, so convertAndSend is still recorded.
     */
    @BeforeEach
    void letTheMockBrokerAcceptMessages() {
        when(rabbit.invoke(any())).thenAnswer(call ->
                call.<RabbitOperations.OperationsCallback<Object>>getArgument(0).doInRabbit(rabbit));
    }

    /** The password of every account a test registers. */
    protected static final String PASSWORD = "correct-horse-1";

    /** A user who registered with an email and a password. */
    protected record Session(UUID userId, String email, String accessToken, String refreshToken) {
    }

    /** A fresh email address, so tests do not share rate limits or accounts. */
    protected static String newEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    /** Goes through the real registration, which also logs the new user in. */
    protected Session login(String email) throws Exception {
        JsonNode tokens = body(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID userId = jdbc.sql("select id from core.users where email = ?").param(email).query(UUID.class).single();
        return new Session(userId, email, tokens.get("accessToken").asText(), tokens.get("refreshToken").asText());
    }

    protected static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Logs in and records consent: a user who can use every endpoint. */
    protected Session loginWithConsent() throws Exception {
        Session session = login(newEmail());
        mvc.perform(as(session, post("/consents")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"personal_data\",\"textVersion\":\"2026-10\"}"))
                .andExpect(status().isCreated());
        return session;
    }

    /** A paying account. There is no payment yet, so the flag is set directly. */
    protected void makePremium(Session session) {
        jdbc.sql("update core.users set premium = true where id = ?").param(session.userId()).update();
    }

    /** A plan of the session's user with one item every day at 08:00, from today for a week. Returns its id. */
    protected String createPlan(Session owner) throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Yekaterinburg"));
        String response = mvc.perform(as(owner, post("/plans")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsOn":"%s","endsOn":"%s","timezone":"Asia/Yekaterinburg",
                                 "items":[{"timeOfDay":"08:00","daysOfWeek":[1,2,3,4,5,6,7]}]}
                                """.formatted(today, today.plusDays(6))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return body(response).get("id").asText();
    }

    /** Invites the relative to the plan and accepts the invitation. The owner must be premium. */
    protected void share(Session owner, String planId, Session relative) throws Exception {
        mvc.perform(as(owner, post("/plans/{id}/shares", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + relative.email() + "\"}"))
                .andExpect(status().isCreated());
        String token = firstMatch("/invite\\?token=([A-Za-z0-9_-]+)", lastMailTo(relative.email()));
        mvc.perform(as(relative, post("/shares/accept")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }

    /** Adds the session's access token to a request. */
    protected static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken());
    }

    protected JsonNode body(String content) throws Exception {
        return json.readTree(content);
    }

    /** Text of the newest email sent to the address. */
    protected String lastMailTo(String email) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).send(eq(email), any(String.class), text.capture());
        return text.getValue();
    }

    protected static String firstMatch(String regex, String text) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No match for " + regex + " in: " + text);
        }
        return matcher.group(1);
    }

    /** Every message of this type that was handed to RabbitMQ, oldest first, as JSON. */
    protected List<JsonNode> published(String type) {
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(rabbit, atLeastOnce()).convertAndSend(eq("otmetka.events"), any(String.class), bodies.capture(),
                any(MessagePostProcessor.class));
        List<JsonNode> result = new ArrayList<>();
        for (Object value : bodies.getAllValues()) {
            JsonNode node = json.valueToTree(value);
            if (type.equals(node.get("type").asText())) {
                result.add(node);
            }
        }
        return result;
    }
}
