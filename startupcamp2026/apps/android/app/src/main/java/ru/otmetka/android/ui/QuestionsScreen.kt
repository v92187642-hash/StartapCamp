package ru.otmetka.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import ru.otmetka.android.R
import ru.otmetka.android.api.models.Question

private const val MAX_QUESTION = 500

/**
 * Questions to take to the doctor, and the PDF report for the visit. The app
 * only keeps the list: it never answers a question or gives advice.
 *
 * @param draft the start of a question, when the screen was opened from a missed entry
 */
@Composable
fun QuestionsScreen(
    planId: String,
    draft: String?,
    app: AppViewModel,
    modifier: Modifier = Modifier,
    viewModel: QuestionsViewModel = viewModel(key = "questions-$planId", factory = QuestionsViewModel.factory(planId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    var text by rememberSaveable(draft) { mutableStateOf(draft.orEmpty()) }
    var seenAdded by rememberSaveable { mutableStateOf(state.added) }

    val resolver = LocalContext.current.contentResolver
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        viewModel.saveReport(resolver, target)
    }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.added) {
        if (state.added != seenAdded) {
            seenAdded = state.added
            text = ""
        }
    }
    // The report is downloaded first, so a refusal is shown before the user picks a place for the file.
    LaunchedEffect(report) {
        if (report != null) saveAs.launch("otmetka-report.pdf")
    }

    ScreenColumn(modifier) {
        BackLink(stringResource(R.string.back_to_diary), onClick = { app.back() })
        Text(stringResource(R.string.questions_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.questions_hint), style = MaterialTheme.typography.bodyMedium)
        ErrorText(state.error)

        when {
            state.questions == null && state.error == null ->
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
            state.questions?.isEmpty() == true ->
                Text(stringResource(R.string.questions_empty), style = MaterialTheme.typography.bodyLarge)
        }
        for (question in state.questions.orEmpty()) {
            QuestionCard(
                question = question,
                isOwner = state.isOwner,
                enabled = !state.busy,
                onAsked = { viewModel.setAsked(question.id, it) },
                onDelete = { viewModel.delete(question.id) },
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_QUESTION) },
            label = { Text(stringResource(R.string.questions_new)) },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton(
            stringResource(R.string.questions_add),
            onClick = { viewModel.add(text) },
            enabled = !state.busy && text.isNotBlank(),
        )

        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.report_hint), style = MaterialTheme.typography.bodyMedium)
        if (state.reportSaved) OkText(stringResource(R.string.report_saved))
        BigOutlinedButton(
            stringResource(if (state.downloading) R.string.report_preparing else R.string.report_download),
            onClick = viewModel::downloadReport,
            enabled = !state.downloading,
        )
    }
}

@Composable
private fun QuestionCard(
    question: Question,
    isOwner: Boolean,
    enabled: Boolean,
    onAsked: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val asked = question.askedAt != null
    val written = LONG_DATE.format(question.createdAt.atZoneSameInstant(ZoneId.systemDefault()))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(question.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (asked) R.string.question_written_asked else R.string.question_written, written),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isOwner) {
                BigOutlinedButton(
                    stringResource(if (asked) R.string.question_not_asked else R.string.question_asked),
                    onClick = { onAsked(!asked) },
                    enabled = enabled,
                )
            }
            if (isOwner || question.mine) {
                TextButton(onClick = onDelete, enabled = enabled) { Text(stringResource(R.string.question_delete)) }
            }
        }
    }
}
