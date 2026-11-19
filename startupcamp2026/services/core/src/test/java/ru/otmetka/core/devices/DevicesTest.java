package ru.otmetka.core.devices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

class DevicesTest extends IntegrationTest {

    @Test
    void registeredTokenIsAnnouncedToNotifier() throws Exception {
        Session session = loginWithConsent();
        String token = "token-" + UUID.randomUUID();

        mvc.perform(as(session, post("/devices")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"rustore\",\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());

        JsonNode event = published("device.registered").stream()
                .filter(e -> token.equals(e.at("/data/token").asText())).findFirst().orElseThrow();
        assertThat(event.at("/data/userId").asText()).isEqualTo(session.userId().toString());
        assertThat(event.at("/data/provider").asText()).isEqualTo("rustore");
    }

    @Test
    void unknownProviderIsRejected() throws Exception {
        Session session = loginWithConsent();

        mvc.perform(as(session, post("/devices")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"pigeon\",\"token\":\"t\"}"))
                .andExpect(status().isBadRequest());
    }
}
