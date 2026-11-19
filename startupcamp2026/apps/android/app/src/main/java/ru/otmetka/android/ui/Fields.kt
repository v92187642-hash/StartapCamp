package ru.otmetka.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.otmetka.android.R
import ru.otmetka.android.api.models.PlanItemCreate

val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

/** ISO day numbers: 1 is Monday. */
val EVERY_DAY: Set<Int> = (1..7).toSet()

/** Time and weekdays of one plan item while it is being typed in. The item's name is given by the server. */
data class ItemDraft(val time: LocalTime, val days: Set<Int> = EVERY_DAY) {
    fun toRequest() = PlanItemCreate(CLOCK.format(time), days)
}

@Composable
fun BackLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("← $text") }
}

/** A message that something worked, in the same place where an error would be. */
@Composable
fun OkText(text: String?) {
    if (text != null) {
        Text(text, color = MarkedGreen, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Hours and minutes typed with the number keyboard, with OK and cancel. Used
 * for an item's time and for correcting a mark. Not the clock face: with
 * large type its 24 numbers run into each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimeInput(state) },
    )
}

/** A button that shows a time and opens [TimeDialog] to change it. */
@Composable
fun TimeField(label: String, value: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    BigOutlinedButton("$label: ${CLOCK.format(value)}", onClick = { open = true })
    if (open) {
        TimeDialog(value, onPick = { onChange(it); open = false }, onDismiss = { open = false })
    }
}

/** A button that shows a date and opens a calendar to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    BigOutlinedButton("$label: ${LONG_DATE.format(value)}", onClick = { open = true })
    if (open) {
        // The calendar works in UTC days.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state)
        }
    }
}

/** Time and weekdays of one plan item. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemFields(value: ItemDraft, onChange: (ItemDraft) -> Unit) {
    val dayNames = stringArrayResource(R.array.weekdays_short)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(stringResource(R.string.item_time), value.time, onChange = { onChange(value.copy(time = it)) })
        Text(stringResource(R.string.item_days), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (day in 1..7) {
                val selected = day in value.days
                FilterChip(
                    selected = selected,
                    onClick = { onChange(value.copy(days = if (selected) value.days - day else value.days + day)) },
                    label = { Text(dayNames[day - 1], style = MaterialTheme.typography.bodyLarge) },
                )
            }
        }
    }
}

/** "каждый день" or "Пн, Ср, Пт". */
@Composable
fun daysText(days: Collection<Int>): String {
    val dayNames = stringArrayResource(R.array.weekdays_short)
    return if (days.size == 7) stringResource(R.string.item_every_day) else days.sorted().joinToString { dayNames[it - 1] }
}
