package ru.otmetka.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.otmetka.android.R
import ru.otmetka.android.api.models.Share

/** Owner only: the plan's items and who may watch the plan. */
@Composable
fun PlanSettingsScreen(
    planId: String,
    app: AppViewModel,
    modifier: Modifier = Modifier,
    viewModel: PlanSettingsViewModel = viewModel(key = "settings-$planId", factory = PlanSettingsViewModel.factory(planId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    ScreenColumn(modifier) {
        BackLink(stringResource(R.string.back_to_diary), onClick = { app.back() })
        Text(
            state.title.ifEmpty { stringResource(R.string.settings_title) },
            style = MaterialTheme.typography.headlineMedium,
        )
        OkText(
            when (val notice = state.notice) {
                is SettingsNotice.ItemAdded -> stringResource(R.string.settings_item_added, notice.label)
                is SettingsNotice.Invited -> stringResource(R.string.settings_invited, notice.email)
                null -> null
            },
        )
        ErrorText(state.error)
        if (state.items == null && state.error == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
        }

        Text(stringResource(R.string.items_title), style = MaterialTheme.typography.titleLarge)
        for (item in state.items.orEmpty()) {
            Text(
                stringResource(R.string.settings_item, item.label, item.timeOfDay.take(5), daysText(item.daysOfWeek)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.item_add), style = MaterialTheme.typography.titleMedium)
                ItemFields(state.newItem, onChange = viewModel::setNewItem)
                BigButton(stringResource(R.string.settings_add), onClick = viewModel::addItem, enabled = !state.busy)
            }
        }

        Text(stringResource(R.string.shares_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.shares_hint), style = MaterialTheme.typography.bodyMedium)
        for (share in state.shares.orEmpty()) {
            val accepted = share.status == Share.Status.accepted
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(share.invitedEmail, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(if (accepted) R.string.share_accepted else R.string.share_pending),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { viewModel.revoke(share.id) }, enabled = !state.busy) {
                        Text(stringResource(if (accepted) R.string.share_close else R.string.share_cancel))
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            label = { Text(stringResource(R.string.share_email)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton(
            stringResource(R.string.share_invite),
            onClick = viewModel::invite,
            enabled = !state.busy && state.email.isNotBlank(),
        )
    }
}
