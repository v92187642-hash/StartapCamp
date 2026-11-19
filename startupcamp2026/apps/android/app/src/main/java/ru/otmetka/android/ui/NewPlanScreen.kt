package ru.otmetka.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.otmetka.android.R

/** Creates a plan: dates and the times of its items. Items are named by number, never by a drug. */
@Composable
fun NewPlanScreen(
    formId: Long,
    app: AppViewModel,
    modifier: Modifier = Modifier,
    // A new form every time the screen is opened, the same one after the phone is rotated.
    viewModel: NewPlanViewModel = viewModel(key = "new-plan-$formId", factory = NewPlanViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdPlanId) {
        state.createdPlanId?.let { app.open(Screen.Day(it)) }
    }

    ScreenColumn(modifier) {
        BackLink(stringResource(R.string.plans_title), onClick = { app.back() })
        Text(stringResource(R.string.new_plan_title), style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text(stringResource(R.string.new_plan_name)) },
            placeholder = { Text(stringResource(R.string.new_plan_name_placeholder)) },
            supportingText = { Text(stringResource(R.string.new_plan_name_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(stringResource(R.string.new_plan_starts), state.startsOn, viewModel::setStartsOn)
        DateField(stringResource(R.string.new_plan_ends), state.endsOn, viewModel::setEndsOn)

        Text(stringResource(R.string.items_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.new_plan_items_hint), style = MaterialTheme.typography.bodyMedium)
        state.items.forEachIndexed { index, item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.item_number, index + 1),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.items.size > 1 && index == state.items.lastIndex) {
                            TextButton(onClick = viewModel::removeLastItem) { Text(stringResource(R.string.item_remove)) }
                        }
                    }
                    ItemFields(item, onChange = { viewModel.setItem(index, it) })
                }
            }
        }
        if (state.items.size < NewPlanViewModel.MAX_ITEMS) {
            BigOutlinedButton(stringResource(R.string.item_add), onClick = viewModel::addItem)
        }

        ErrorText(state.error)
        BigButton(stringResource(R.string.new_plan_create), onClick = viewModel::create, enabled = !state.busy)
    }
}
