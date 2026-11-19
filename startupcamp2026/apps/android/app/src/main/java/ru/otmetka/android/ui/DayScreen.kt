package ru.otmetka.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import ru.otmetka.android.R
import ru.otmetka.android.data.db.EntryEntity
import ru.otmetka.android.domain.EntryState
import ru.otmetka.android.domain.entryState
import ru.otmetka.android.domain.questionDraft

private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))

/** The day view: every entry of one day, in one of three states. */
@Composable
fun DayScreen(
    planId: String,
    app: AppViewModel,
    modifier: Modifier = Modifier,
    viewModel: DayViewModel = viewModel(key = planId, factory = DayViewModel.factory(planId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    ScreenColumn(modifier) {
        BackLink(stringResource(R.string.plans_title), onClick = { app.back() })
        val day = state
        if (day == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
            return@ScreenColumn
        }
        Text(day.plan.title, style = MaterialTheme.typography.headlineMedium)
        if (!day.plan.isOwner) {
            Text(stringResource(R.string.day_viewer), style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val previous = stringResource(R.string.day_previous)
            val next = stringResource(R.string.day_next)
            OutlinedButton(
                onClick = viewModel::previousDay,
                modifier = Modifier.bigTouchTarget().semantics { contentDescription = previous },
            ) { Text("←") }
            Text(
                DAY.format(day.date),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            OutlinedButton(
                onClick = viewModel::nextDay,
                modifier = Modifier.bigTouchTarget().semantics { contentDescription = next },
            ) { Text("→") }
        }

        ErrorText(error)
        if (day.entries.isEmpty()) {
            Text(stringResource(R.string.day_empty), style = MaterialTheme.typography.bodyLarge)
        }
        for (entry in day.entries) {
            val scheduled = Instant.ofEpochMilli(entry.scheduledAt).atZone(day.zone)
            val draft = questionDraft(entry.label, DAY.format(day.date), CLOCK.format(scheduled))
            EntryCard(
                entry = entry,
                zone = day.zone,
                isOwner = day.plan.isOwner,
                onMark = { viewModel.mark(entry.id) },
                onUnmark = { viewModel.unmark(entry.id) },
                onCorrect = { viewModel.correct(entry.id, it) },
                onQuestion = { app.open(Screen.Questions(planId, draft)) },
            )
        }

        BigOutlinedButton(stringResource(R.string.questions_title), onClick = { app.open(Screen.Questions(planId)) })
        if (day.plan.isOwner) {
            BigOutlinedButton(stringResource(R.string.settings_open), onClick = { app.open(Screen.PlanSettings(planId)) })
        }
    }
}

@Composable
private fun EntryCard(
    entry: EntryEntity,
    zone: ZoneId,
    isOwner: Boolean,
    onMark: () -> Unit,
    onUnmark: () -> Unit,
    onCorrect: (LocalTime) -> Unit,
    onQuestion: () -> Unit,
) {
    val state = entryState(entry.isMarked, entry.scheduledAt, System.currentTimeMillis())
    fun time(millis: Long) = CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone))
    var correcting by rememberSaveable(entry.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(time(entry.scheduledAt), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(entry.label, style = MaterialTheme.typography.titleMedium)
            }
            // The three states differ by text as well as colour.
            Text(
                text = when (state) {
                    EntryState.PLANNED -> stringResource(R.string.entry_planned)
                    EntryState.MARKED -> stringResource(R.string.entry_marked, time(entry.markedAt ?: entry.scheduledAt))
                    EntryState.NO_MARK -> stringResource(R.string.entry_no_mark)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state == EntryState.PLANNED) FontWeight.Normal else FontWeight.SemiBold,
                color = when (state) {
                    EntryState.PLANNED -> MaterialTheme.colorScheme.onSurfaceVariant
                    EntryState.MARKED -> MarkedGreen
                    EntryState.NO_MARK -> MaterialTheme.colorScheme.error
                },
            )
            if (entry.pending != null) {
                Text(stringResource(R.string.entry_pending), style = MaterialTheme.typography.bodyMedium)
            }
            // A missed entry offers a question for the doctor, never advice.
            if (state == EntryState.NO_MARK) {
                TextButton(onClick = onQuestion) { Text(stringResource(R.string.entry_question)) }
            }
            if (isOwner) {
                if (entry.isMarked) {
                    BigOutlinedButton(stringResource(R.string.entry_correct), onClick = { correcting = true })
                    BigOutlinedButton(stringResource(R.string.entry_unmark), onClick = onUnmark)
                } else {
                    BigButton(stringResource(R.string.entry_mark), onClick = onMark)
                }
            }
        }
    }
    if (correcting) {
        val current = Instant.ofEpochMilli(entry.markedAt ?: entry.scheduledAt).atZone(zone).toLocalTime()
            .truncatedTo(ChronoUnit.MINUTES)
        TimeDialog(
            initial = current,
            // Confirming the same time is not a correction.
            onPick = { if (it != current) onCorrect(it); correcting = false },
            onDismiss = { correcting = false },
        )
    }
}
