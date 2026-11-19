package ru.otmetka.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import ru.otmetka.android.R
import ru.otmetka.android.data.db.PlanEntity

@Composable
fun PlansScreen(
    app: AppViewModel,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    ScreenColumn(modifier) {
        Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.headlineMedium)

        NotificationPermissionCard()
        BatteryCard(onOpen = { app.open(Screen.BatteryGuide) })

        if (status.offline) {
            Text(stringResource(R.string.offline_copy), style = MaterialTheme.typography.bodyMedium)
        }
        ErrorText(status.error)

        if (plans.isEmpty() && !status.loading) {
            Text(stringResource(R.string.plans_empty), style = MaterialTheme.typography.bodyLarge)
        }
        for (plan in plans) {
            PlanCard(plan, onClick = { app.open(Screen.Day(plan.id)) })
        }
        BigButton(stringResource(R.string.plans_new), onClick = { app.open(Screen.NewPlan(System.nanoTime())) })

        BigOutlinedButton(
            text = stringResource(if (status.loading) R.string.loading else R.string.plans_refresh),
            onClick = viewModel::refresh,
            enabled = !status.loading,
        )
        TextButton(onClick = { app.open(Screen.Account) }) { Text(stringResource(R.string.account_title)) }
    }
}

@Composable
private fun PlanCard(plan: PlanEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    R.string.plans_dates,
                    LONG_DATE.format(LocalDate.parse(plan.startsOn)),
                    LONG_DATE.format(LocalDate.parse(plan.endsOn)),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!plan.isOwner) {
                Text(stringResource(R.string.plans_watching), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Android 13+: reminders are invisible until the user allows notifications. */
@Composable
private fun NotificationPermissionCard() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleResumeEffect(Unit) {
        granted = hasNotificationPermission(context)
        onPauseOrDispose { }
    }
    if (granted) return

    NoticeCard(
        title = stringResource(R.string.notifications_ask_title),
        text = stringResource(R.string.notifications_ask_text),
        action = stringResource(R.string.notifications_allow),
        onAction = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Shown until the app is exempt from battery optimisation. */
@Composable
private fun BatteryCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isExemptFromBatteryOptimisation(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = isExemptFromBatteryOptimisation(context)
        onPauseOrDispose { }
    }
    if (exempt) return

    NoticeCard(
        title = stringResource(R.string.battery_card_title),
        text = stringResource(R.string.battery_card_text),
        action = stringResource(R.string.battery_card_open),
        onAction = onOpen,
    )
}

private fun isExemptFromBatteryOptimisation(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@Composable
private fun NoticeCard(title: String, text: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            BigButton(action, onClick = onAction)
        }
    }
}
