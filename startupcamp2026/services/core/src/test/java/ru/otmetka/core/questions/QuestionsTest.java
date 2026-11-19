package ru.otmetka.core.questions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

class QuestionsTest extends IntegrationTest {

    @Test
    void ownerAndRelativeKeepAListOfQuestionsForTheDoctor() throws Exception {
        Session owner = loginWithConsent();
        makePremium(owner);
        Session relative = loginWithConsent();
        String planId = createPlan(owner);
        share(owner, planId, relative);

        String first = add(owner, planId, "  Можно ли перенести время назначения 1?  ");
        String second = add(relative, planId, "Что делать, если отметка пропущена?");

        mvc.perform(as(owner, get("/plans/{id}/questions", planId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Можно ли перенести время назначения 1?"))
                .andExpect(jsonPath("$[0].mine").value(true))
                .andExpect(jsonPath("$[0].askedAt").doesNotExist())
                .andExpect(jsonPath("$[1].mine").value(false));

        // Only the owner says what was asked at the visit.
        mvc.perform(as(relative, put("/questions/{id}/asked", first))).andExpect(status().isForbidden());
        mvc.perform(as(owner, put("/questions/{id}/asked", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.askedAt").exists());
        mvc.perform(as(owner, delete("/questions/{id}/asked", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.askedAt").doesNotExist());

        // A relative deletes their own question but not the owner's; the owner deletes any.
        mvc.perform(as(relative, delete("/questions/{id}", first))).andExpect(status().isForbidden());
        mvc.perform(as(relative, delete("/questions/{id}", second))).andExpect(status().isNoContent());
        mvc.perform(as(owner, delete("/questions/{id}", first))).andExpect(status().isNoContent());
        mvc.perform(as(owner, get("/plans/{id}/questions", planId))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void strangerSeesNothingAndEmptyTextIsRejected() throws Exception {
        Session owner = loginWithConsent();
        Session stranger = loginWithConsent();
        String planId = createPlan(owner);
        String question = add(owner, planId, "Вопрос");

        mvc.perform(as(stranger, get("/plans/{id}/questions", planId))).andExpect(status().isNotFound());
        mvc.perform(as(stranger, delete("/questions/{id}", question))).andExpect(status().isNotFound());
        mvc.perform(as(owner, post("/plans/{id}/questions", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    private String add(Session session, String planId, String text) throws Exception {
        String response = mvc.perform(as(session, post("/plans/{id}/questions", planId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("text", text))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return body(response).get("id").asText();
    }
}
