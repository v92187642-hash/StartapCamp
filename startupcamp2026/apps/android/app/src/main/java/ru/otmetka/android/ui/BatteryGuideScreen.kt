package ru.otmetka.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ru.otmetka.android.R

/**
 * Xiaomi, Huawei and Samsung stop background apps to save battery, and then
 * reminders are late or lost. There is no API to switch that off; the user
 * has to do it, so this screen says where. The instruction for the phone in
 * hand comes first.
 */
@Composable
fun BatteryGuideScreen(app: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val maker = Build.MANUFACTURER.lowercase()
    val guides = listOf(
        listOf("xiaomi", "redmi", "poco") to R.string.battery_xiaomi,
        listOf("huawei", "honor") to R.string.battery_huawei,
        listOf("samsung") to R.string.battery_samsung,
    ).sortedByDescending { (makers, _) -> makers.any { it in maker } }

    ScreenColumn(modifier) {
        Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyLarge)

        Text(stringResource(R.string.battery_step1_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.battery_step1_text), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.battery_open_settings), onClick = {
            context.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        Text(stringResource(R.string.battery_step2_title), style = MaterialTheme.typography.titleMedium)
        for ((_, text) in guides) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.battery_other), style = MaterialTheme.typography.bodyLarge)
        BigOutlinedButton(stringResource(R.string.battery_open_app_settings), onClick = {
            context.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        })

        BigButton(stringResource(R.string.battery_done), onClick = { app.back() })
    }
}

/** Some phones lack one of the settings screens; fall back to the general settings. */
private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
