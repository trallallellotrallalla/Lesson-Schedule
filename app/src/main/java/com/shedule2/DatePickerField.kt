package com.shedule2

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minSelectableDate: LocalDate = LocalDate.now(),
    enabled: Boolean = true
) {
    val pickerDate = if (selectedDate.isBefore(minSelectableDate)) minSelectableDate else selectedDate
    val minSelectableDateMillis = remember(minSelectableDate) { minSelectableDate.toUtcStartOfDayMillis() }
    val initialSelectedDateMillis = remember(pickerDate) { pickerDate.toUtcStartOfDayMillis() }
    var showDatePicker by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    IconButton(
        onClick = { showDatePicker = true },
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            disabledContainerColor = colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.38f)
        )
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Выбрать дату"
        )
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= minSelectableDateMillis
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year >= minSelectableDate.year
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedDate = datePickerState.selectedDateMillis
                            ?.toLocalDateFromUtcMillis()

                        if (pickedDate != null && !pickedDate.isBefore(minSelectableDate)) {
                            onDateSelected(pickedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Выбрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.primary,
                    headlineContentColor = colorScheme.primary,
                    weekdayContentColor = colorScheme.primary,
                    navigationContentColor = colorScheme.primary,
                    selectedDayContainerColor = colorScheme.primary,
                    selectedDayContentColor = colorScheme.onPrimary,
                    todayContentColor = colorScheme.primary,
                    todayDateBorderColor = colorScheme.primary
                )
            )
        }
    }
}

private fun LocalDate.toUtcStartOfDayMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateFromUtcMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
