package com.medicapp.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Champ date en lecture seule ouvrant le sélecteur Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.let { Format.dateShort(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text("📅") }
        },
        modifier = modifier,
    )
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { DatePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
        )
    }
}

/** Champ heure ouvrant le sélecteur Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: LocalTime?,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.let { Format.time(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text("🕐") }
        },
        modifier = modifier,
    )
    if (showPicker) {
        val state = rememberTimePickerState(initialHour = value?.hour ?: 8, initialMinute = value?.minute ?: 0)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
        )
    }
}

/** Titre de groupe dans un formulaire. */
@Composable
fun FormSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun LocalDateTimeFields(
    date: LocalDate?,
    time: LocalTime?,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    Row {
        DateField(label = "Date", value = date, onChange = onDateChange, modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Spacer(Modifier.weight(0.08f))
        TimeField(label = "Heure", value = time, onChange = onTimeChange, modifier = Modifier.weight(1f))
    }
}

fun LocalDate.atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)
