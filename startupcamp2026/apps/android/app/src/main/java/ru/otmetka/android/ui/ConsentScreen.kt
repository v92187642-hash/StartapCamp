package ru.otmetka.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.otmetka.android.R

/** Shown once, after the first login: nothing is stored before consent. */
@Composable
fun ConsentScreen(state: AppState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    ScreenColumn(modifier) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
        for (point in listOf(R.string.consent_1, R.string.consent_2, R.string.consent_3, R.string.consent_4, R.string.consent_5)) {
            Text("• " + stringResource(point), style = MaterialTheme.typography.bodyLarge)
        }
        BigButton(stringResource(R.string.consent_accept), onClick = viewModel::acceptConsent, enabled = !state.busy)
        TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.logout)) }
        ErrorText(state.error)
    }
}
