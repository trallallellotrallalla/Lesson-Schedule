package com.shedule2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    data: PerformanceData?,
    isLoading: Boolean,
    errorText: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM", Locale("ru")) }
    val todayLabel = remember { LocalDate.now().format(dateFormatter) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Успеваемость на $todayLabel") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                data != null -> PerformanceList(data = data)
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorText != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = errorText) }
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = "Нет данных.") }
            }
        }
    }
}

private enum class SortDirection { NONE, ASC, DESC }

@Composable
private fun PerformanceList(data: PerformanceData) {
    var sortDirection by rememberSaveable { mutableStateOf(SortDirection.NONE) }

    val sortedSubjects = remember(data.subjects, sortDirection) {
        when (sortDirection) {
            SortDirection.NONE -> data.subjects
            SortDirection.ASC -> data.subjects.sortedBy { subjectCurrentPoints(it) }
            SortDirection.DESC -> data.subjects.sortedByDescending { subjectCurrentPoints(it) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SortBar(
                direction = sortDirection,
                onAscending = {
                    sortDirection = if (sortDirection == SortDirection.ASC) SortDirection.NONE else SortDirection.ASC
                },
                onDescending = {
                    sortDirection = if (sortDirection == SortDirection.DESC) SortDirection.NONE else SortDirection.DESC
                }
            )
        }

        if (data.truancyAll != null || data.truancyJustified != null) {
            item { TruancyCard(all = data.truancyAll ?: 0, justified = data.truancyJustified) }
        }

        items(sortedSubjects) { subject -> SubjectCard(subject) }

        if (sortedSubjects.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Нет данных об успеваемости.")
                }
            }
        }
    }
}

@Composable
private fun SortBar(
    direction: SortDirection,
    onAscending: () -> Unit,
    onDescending: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Сортировать по баллам",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onAscending,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (direction == SortDirection.ASC)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = if (direction == SortDirection.ASC)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "По возрастанию")
        }
        IconButton(
            onClick = onDescending,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (direction == SortDirection.DESC)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = if (direction == SortDirection.DESC)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "По убыванию")
        }
    }
}

private fun subjectCurrentPoints(subject: PerformanceSubject): Int {
    return subject.currentPoints.toIntOrNull() ?: 0
}

private fun totalPointsColor(points: Int): Color = when {
    points < 50 -> Color(0xFFD32F2F)
    points < 70 -> Color(0xFFF57C00)
    points < 85 -> Color(0xFF03A9F4)
    else -> Color(0xFF2E7D32)
}

@Composable
private fun TruancyCard(all: Int, justified: Int?) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Пропуски:", fontWeight = FontWeight.SemiBold)
            Text(text = "всего $all")
            Text(text = "по уважительной ${justified ?: 0}")
        }
    }
}

@Composable
private fun SubjectCard(subject: PerformanceSubject) {
    var expanded by rememberSaveable(subject.name) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subject.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTotalOverHundred(subject),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = totalPointsColor(subjectCurrentPoints(subject)),
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }
            }

            StagesRow(subject)
            ProgressBar(subject)

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (subject.works.isEmpty()) {
                        Text(text = "Нет работ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        subject.works.forEach { work -> WorkRow(work) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StagesRow(subject: PerformanceSubject) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StageItem("Сдано", subject.passed)
    }
}

@Composable
private fun StageItem(label: String, stage: PerformanceStage) {
    val current = stage.currentPoints?.toString() ?: "_"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label • ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            text = "$current / ${stage.maxPoints}",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProgressBar(subject: PerformanceSubject) {
    val current = subject.passed.currentPoints?.toFloat() ?: 0f
    val progress = (current / 100f).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    )
}

@Composable
private fun WorkRow(work: PerformanceWork) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatWorkTitle(work),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatPoints(work),
            fontSize = 14.sp,
            fontWeight = if (work.isChecked) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun formatTotalOverHundred(subject: PerformanceSubject): String {
    val current = subject.currentPoints.takeIf { it.isNotBlank() } ?: "0"
    return "$current/100"
}

private fun formatPoints(work: PerformanceWork): String {
    val current = work.currentPoints?.takeIf { it.isNotBlank() } ?: "0"
    val max = work.maxPoints.takeIf { it.isNotBlank() } ?: "0"
    return "$current/$max"
}

private fun formatWorkTitle(work: PerformanceWork): String {
    val test = work.testName.trim()
    val name = work.name.trim()
    val testWithDot = if (test.isEmpty() || test.endsWith(".")) test else "$test."
    return if (name.isBlank()) testWithDot else "$testWithDot $name"
}
