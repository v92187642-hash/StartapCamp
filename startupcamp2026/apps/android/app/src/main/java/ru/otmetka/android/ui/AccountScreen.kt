package ru.otmetka.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.otmetka.android.R

/** The account: email, subscription, logout, and deletion with all data. */
@Composable
fun AccountScreen(
    app: AppViewModel,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load() }

    ScreenColumn(modifier) {
        BackLink(stringResource(R.string.plans_title), onClick = { app.back() })
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.account_email, state.email ?: stringResource(R.string.loading)),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.email != null) {
            Text(
                stringResource(if (state.premium) R.string.account_premium_on else R.string.account_premium_off),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        BigOutlinedButton(stringResource(R.string.logout), onClick = app::logout)
        BigOutlinedButton(stringResource(R.string.logout_all), onClick = viewModel::logoutAll, enabled = !state.busy)

        Text(stringResource(R.string.account_delete_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.account_delete_hint), style = MaterialTheme.typography.bodyMedium)
        ErrorText(state.error)
        if (!confirming) {
            BigOutlinedButton(stringResource(R.string.account_delete), onClick = { confirming = true })
        } else {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton(
                stringResource(R.string.account_delete_confirm),
                onClick = { viewModel.deleteAccount(password) },
                enabled = !state.busy && password.isNotEmpty(),
            )
            TextButton(onClick = { confirming = false }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
        }
    }
}
