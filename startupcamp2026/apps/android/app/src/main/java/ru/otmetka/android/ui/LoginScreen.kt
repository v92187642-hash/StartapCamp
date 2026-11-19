package ru.otmetka.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import ru.otmetka.android.R

private const val MIN_PASSWORD_LENGTH = 8

/** Login with an email and a password, and registration of a new account. */
@Composable
fun LoginScreen(state: AppState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }

    val mismatch = registering && repeated.isNotEmpty() && repeated != password
    val complete = email.isNotBlank() &&
        if (registering) password.length >= MIN_PASSWORD_LENGTH && repeated == password else password.isNotEmpty()
    val submit = { if (complete && !state.busy) viewModel.login(email, password, registering) }

    ScreenColumn(modifier) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.app_lead), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(if (registering) R.string.register_title else R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password)) },
            supportingText = if (registering) {
                { Text(stringResource(R.string.login_password_hint)) }
            } else {
                null
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (registering) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (registering) {
            OutlinedTextField(
                value = repeated,
                onValueChange = { repeated = it },
                label = { Text(stringResource(R.string.login_password_repeat)) },
                supportingText = if (mismatch) {
                    { Text(stringResource(R.string.login_password_mismatch)) }
                } else {
                    null
                },
                isError = mismatch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BigButton(
            text = stringResource(if (registering) R.string.register_enter else R.string.login_enter),
            onClick = submit,
            enabled = !state.busy && complete,
        )
        TextButton(onClick = { registering = !registering; repeated = ""; viewModel.clearError() }) {
            Text(stringResource(if (registering) R.string.register_to_login else R.string.login_to_register))
        }
        ErrorText(state.error)
    }
}
