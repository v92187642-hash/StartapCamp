package ru.otmetka.core.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import ru.otmetka.core.IntegrationTest;

/** Milestone M4: a paying account exports the report and a free one cannot. */
class ReportTest extends IntegrationTest {

    static final byte[] PDF = "%PDF-test".getBytes();

    @Test
    void freeAccountCannotExportAndCannotInvite() throws Exception {
        Session owner = loginWithConsent();
        String planId = createPlan(owner);

        mvc.perform(as(owner, get("/plans/{id}/report", planId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("premium-required"))
                .andExpect(jsonPath("$.detail").value("Эта возможность доступна по подписке"));
        mvc.perform(as(owner, post("/plans/{id}/shares", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"relative@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("premium-required"));
    }

    @Test
    void premiumAccountExportsMarksByDayAndQuestions() throws Exception {
        when(reportRenderer.render(any())).thenReturn(PDF);
        Session owner = loginWithConsent();
        makePremium(owner);
        String planId = createPlan(owner);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Yekaterinburg"));
        // Two entries in the past, put there directly: the plan itself creates only future ones.
        UUID itemId = jdbc.sql("select id from core.plan_items where plan_id = ?").param(UUID.fromString(planId))
                .query(UUID.class).single();
        UUID marked = UUID.randomUUID();
        jdbc.sql("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, now() - interval '2 hours')")
                .params(marked, itemId).update();
        jdbc.sql("insert into core.entries (id, plan_item_id, scheduled_at) values (?, ?, now() - interval '1 hour')")
                .params(UUID.randomUUID(), itemId).update();
        mvc.perform(as(owner, put("/entries/{id}/mark", marked))).andExpect(status().isOk());
        mvc.perform(as(owner, post("/plans/{id}/questions", planId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Вопрос врачу\"}"))
                .andExpect(status().isCreated());

        mvc.perform(as(owner, get("/plans/{id}/report", planId))
                        .param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF));

        ArgumentCaptor<ReportData> sent = ArgumentCaptor.forClass(ReportData.class);
        verify(reportRenderer, atLeastOnce()).render(sent.capture());
        ReportData data = sent.getValue();
        assertThat(data.title()).isEqualTo("План 1");
        assertThat(data.timezone()).isEqualTo("Asia/Yekaterinburg");
        assertThat(data.days()).isNotEmpty();
        var rows = data.days().stream().flatMap(day -> day.entries().stream()).toList();
        assertThat(rows).extracting(ReportData.Row::state).contains("marked", "no_mark", "planned");
        assertThat(rows).filteredOn(row -> row.state().equals("marked"))
                .allSatisfy(row -> assertThat(row.markedTime()).matches("\\d\\d:\\d\\d"));
        assertThat(rows).allSatisfy(row -> assertThat(row.label()).isEqualTo("Назначение 1"));
        assertThat(data.questions()).extracting(ReportData.QuestionRow::text).containsExactly("Вопрос врачу");
        assertThat(data.questions().getFirst().asked()).isFalse();
    }

    @Test
    void relativeWithPremiumMayExportAndStrangerMayNot() throws Exception {
        when(reportRenderer.render(any())).thenReturn(PDF);
        Session owner = loginWithConsent();
        makePremium(owner);
        Session relative = loginWithConsent();
        Session stranger = loginWithConsent();
        makePremium(stranger);
        String planId = createPlan(owner);
        share(owner, planId, relative);

        mvc.perform(as(relative, get("/plans/{id}/report", planId))).andExpect(status().isForbidden());
        makePremium(relative);
        mvc.perform(as(relative, get("/plans/{id}/report", planId))).andExpect(status().isOk());
        mvc.perform(as(stranger, get("/plans/{id}/report", planId))).andExpect(status().isNotFound());
    }

    @Test
    void periodIsValidated() throws Exception {
        Session owner = loginWithConsent();
        makePremium(owner);
        String planId = createPlan(owner);

        mvc.perform(as(owner, get("/plans/{id}/report", planId)).param("from", "2026-10-10").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }
}
