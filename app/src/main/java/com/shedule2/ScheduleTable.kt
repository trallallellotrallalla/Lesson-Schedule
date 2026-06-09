package com.shedule2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val WEEK_DAY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d-M-uuuu")

private fun WeekDayHeader.isToday(today: LocalDate): Boolean {
    return runCatching {
        LocalDate.parse(date.trim(), WEEK_DAY_DATE_FORMATTER) == today
    }.getOrDefault(false)
}

private const val MIN_SCHEDULE_SCALE = 0.4f
private const val MAX_SCHEDULE_SCALE = 2.0f
private const val SCHEDULE_SCALE_STEP = 0.1f

private fun boundedScheduleScale(scale: Float): Float =
    scale.coerceIn(MIN_SCHEDULE_SCALE, MAX_SCHEDULE_SCALE)

private fun scheduleScalePercent(scale: Float): Int =
    (boundedScheduleScale(scale) * 100f).roundToInt()

private fun headerHeightScaleFor(scale: Float): Float {
    val safeScale = boundedScheduleScale(scale)
    return if (safeScale <= 1f) {
        0.55f + 0.45f * safeScale
    } else {
        1f + (safeScale - 1f) * 0.38f
    }
}

private fun headerTextScaleFor(scale: Float): Float {
    val safeScale = boundedScheduleScale(scale)
    return if (safeScale <= 1f) {
        0.58f + 0.42f * safeScale
    } else {
        1f + (safeScale - 1f) * 0.45f
    }
}

@Composable
fun ScheduleTable(
    lessons: List<SimpleLesson>,
    weekDays: List<WeekDayHeader>,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now()
) {
    if (lessons.isEmpty() || weekDays.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("Пока нет данных для отображения.")
        }
        return
    }

    val horizontalState = rememberScrollState()
    val verticalState = rememberScrollState()

    var scale by remember { mutableStateOf(1f) }
    var selectedLesson by remember { mutableStateOf<SimpleLesson?>(null) }
    val currentScale = boundedScheduleScale(scale)

    val filteredWeekDays = weekDays.filter { it.weekDayNumber in 0..6 }

    val slotList = lessons
        .flatMap { it.slotLabels }
        .distinct()
        .sortedBy { slot ->
            val start = slot.substringBefore("-")
            val parts = start.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            hour * 60 + minute
        }

    if (slotList.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("Не удалось построить сетку расписания.")
        }
        return
    }

    val slotIndexMap = slotList.withIndex().associate { it.value to it.index }

    val baseDayColumnWidth = 220.dp
    val baseHeaderHeight = 68.dp
    val baseSlotHeight = 92.dp

    val baseBodyWidth = baseDayColumnWidth * filteredWeekDays.size
    val baseBodyHeight = baseSlotHeight * slotList.size
    val scaledBodyWidth = baseBodyWidth * currentScale
    val scaledBodyHeight = baseBodyHeight * currentScale
    val scaledHeaderHeight = baseHeaderHeight * headerHeightScaleFor(currentScale)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomButton(
                onClick = { scale = (scale - SCHEDULE_SCALE_STEP).coerceIn(MIN_SCHEDULE_SCALE, MAX_SCHEDULE_SCALE) },
                content = { Text(text = "−", fontWeight = FontWeight.Bold) }
            )

            ZoomButton(
                onClick = { scale = 1f },
                content = {
                    Text(
                        text = "${scheduleScalePercent(currentScale)}%",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            ZoomButton(
                onClick = { scale = (scale + SCHEDULE_SCALE_STEP).coerceIn(MIN_SCHEDULE_SCALE, MAX_SCHEDULE_SCALE) },
                content = { Text(text = "+", fontWeight = FontWeight.Bold) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = scaledHeaderHeight)
                    .horizontalScroll(horizontalState)
                    .verticalScroll(verticalState)
            ) {
                Box(
                    modifier = Modifier
                        .width(scaledBodyWidth)
                        .height(scaledBodyHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .width(baseBodyWidth)
                            .height(baseBodyHeight)
                            .graphicsLayer(
                                scaleX = currentScale,
                                scaleY = currentScale,
                                transformOrigin = TransformOrigin(0f, 0f)
                            )
                    ) {
                        ScheduleGrid(
                            lessons = lessons,
                            filteredWeekDays = filteredWeekDays,
                            slotList = slotList,
                            slotIndexMap = slotIndexMap,
                            dayColumnWidth = baseDayColumnWidth,
                            slotHeight = baseSlotHeight,
                            onLessonClick = { selectedLesson = it }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledHeaderHeight)
                    .horizontalScroll(horizontalState)
                    .align(Alignment.TopStart)
            ) {
                ScheduleHeader(
                    filteredWeekDays = filteredWeekDays,
                    dayColumnWidth = baseDayColumnWidth,
                    headerHeight = baseHeaderHeight,
                    scale = currentScale,
                    today = today
                )
            }
        }
    }

    selectedLesson?.let { lesson ->
        LessonDetailsDialog(
            lesson = lesson,
            onDismiss = { selectedLesson = null }
        )
    }
}

@Composable
private fun ScheduleGrid(
    lessons: List<SimpleLesson>,
    filteredWeekDays: List<WeekDayHeader>,
    slotList: List<String>,
    slotIndexMap: Map<String, Int>,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    slotHeight: androidx.compose.ui.unit.Dp,
    onLessonClick: (SimpleLesson) -> Unit
) {
    slotList.forEachIndexed { rowIndex, _ ->
        filteredWeekDays.forEachIndexed { dayIndex, _ ->
            Box(
                modifier = Modifier
                    .offset(
                        x = dayColumnWidth * dayIndex,
                        y = slotHeight * rowIndex
                    )
                    .width(dayColumnWidth)
                    .height(slotHeight)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }

    val parallelGroups = lessons
        .mapNotNull { lesson ->
            val dayIndex = filteredWeekDays.indexOfFirst { it.dayName == lesson.dayName }
            val firstSlot = lesson.slotLabels.firstOrNull() ?: return@mapNotNull null
            val topSlotIndex = slotIndexMap[firstSlot] ?: return@mapNotNull null
            if (dayIndex == -1) return@mapNotNull null
            Triple(dayIndex, topSlotIndex, lesson)
        }
        .groupBy { (dayIndex, topSlotIndex, _) -> dayIndex to topSlotIndex }

    parallelGroups.forEach { (_, group) ->
        val groupSize = group.size
        group.forEachIndexed { groupIndex, (dayIndex, topSlotIndex, lesson) ->
            val slotWidth = dayColumnWidth / groupSize
            val lessonHeight = (slotHeight * lesson.slotLabels.size) - 8.dp
            val lessonColor = lessonCardColor(lesson.type)
            val lessonTime = lesson.time
            val canceled = lesson.isCanceled
            val textDecoration = if (canceled) TextDecoration.LineThrough else TextDecoration.None

            Box(
                modifier = Modifier
                    .offset(
                        x = dayColumnWidth * dayIndex + slotWidth * groupIndex + 4.dp,
                        y = slotHeight * topSlotIndex + 4.dp
                    )
                    .width(slotWidth - 8.dp)
                    .height(lessonHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .background(lessonColor)
                    .clickable { onLessonClick(lesson) }
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = lesson.subject,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = textDecoration
                        )

                        if (lessonTime.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Время пары",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = lessonTime,
                                    fontSize = 13.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = textDecoration
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (lesson.room.isNotBlank()) {
                            Text(
                                text = "📍 ${lesson.room}",
                                fontSize = 13.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = textDecoration
                            )
                        }

                        if (lesson.teacher.isNotBlank()) {
                            Text(
                                text = "👤 ${lesson.teacher}",
                                fontSize = 13.sp,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = textDecoration
                            )
                        }

                        val canceledMarker = "Отменено"
                        val noteWithoutCancel = lesson.note
                            ?.split(". ")
                            ?.filter { it.isNotBlank() && it != canceledMarker }
                            ?.joinToString(". ")
                            ?.takeIf { it.isNotBlank() }

                        val bottomText = buildAnnotatedString {
                            val parts = mutableListOf<Pair<String, Boolean>>()
                            if (lesson.type.isNotBlank()) parts += lesson.type to false
                            if (!noteWithoutCancel.isNullOrBlank()) parts += noteWithoutCancel to false
                            if (canceled) parts += canceledMarker to true

                            parts.forEachIndexed { index, (text, isCancelMark) ->
                                if (index > 0) append(" • ")
                                if (isCancelMark) {
                                    withStyle(
                                        SpanStyle(
                                            color = Color(0xFFD32F2F),
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append(text)
                                    }
                                } else {
                                    withStyle(SpanStyle(textDecoration = textDecoration)) {
                                        append(text)
                                    }
                                }
                            }
                        }

                        if (bottomText.text.isNotBlank()) {
                            Text(
                                text = bottomText,
                                fontSize = 13.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonDetailsDialog(
    lesson: SimpleLesson,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text(
                text = "Подробнее",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                LessonDetailRow("Дисциплина:", lesson.subject)
                LessonDetailRow("Тип занятия:", lesson.type)
                LessonDetailRow("Дата:", lesson.date.replace('-', '.'))
                LessonDetailRow("Время:", lesson.time.ifBlank { lesson.slotLabels.joinToString(", ") }, icon = "🕒")
                LessonDetailRow("Аудитория:", lesson.room, icon = "📍")
                LessonDetailRow("Преподаватель:", lesson.teacherFullName.ifBlank { lesson.teacher }, icon = "👤")
                if (!lesson.note.isNullOrBlank()) {
                    LessonDetailRow("Примечание:", lesson.note)
                }
            }
        }
    )
}

@Composable
private fun LessonDetailRow(
    label: String,
    value: String,
    icon: String = ""
) {
    val text = value.ifBlank { "Не указано" }
    val displayValue = if (icon.isNotBlank() && value.isNotBlank()) "$icon $text" else text

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.width(118.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayValue,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ScheduleHeader(
    filteredWeekDays: List<WeekDayHeader>,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    headerHeight: androidx.compose.ui.unit.Dp,
    scale: Float,
    today: LocalDate
) {
    val safeScale = boundedScheduleScale(scale)
    val headerHeightScale = headerHeightScaleFor(safeScale)
    val headerTextScale = headerTextScaleFor(safeScale)

    val scaledDayColumnWidth = dayColumnWidth * safeScale
    val scaledHeaderHeight = headerHeight * headerHeightScale
    val scaledHeaderWidth = scaledDayColumnWidth * filteredWeekDays.size

    val dateFontSize = (20f * headerTextScale).coerceIn(8f, 36f).sp
    val dayNameFontSize = (16f * headerTextScale).coerceIn(8f, 29f).sp
    val dateLineHeight = (22f * headerTextScale).coerceIn(9f, 40f).sp
    val dayNameLineHeight = (18f * headerTextScale).coerceIn(9f, 32f).sp
    val horizontalPadding = (8f * safeScale).coerceIn(2f, 16f).dp
    val verticalPadding = (4f * headerHeightScale).coerceIn(2f, 7f).dp

    Box(
        modifier = Modifier
            .width(scaledHeaderWidth)
            .height(scaledHeaderHeight)
    ) {
        filteredWeekDays.forEachIndexed { dayIndex, day ->
            val isCurrentDay = day.isToday(today)
            Box(
                modifier = Modifier
                    .offset(x = scaledDayColumnWidth * dayIndex)
                    .width(scaledDayColumnWidth)
                    .height(scaledHeaderHeight)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .background(
                        if (!day.isStudyDay) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.dayWithMonthLabel(),
                        fontSize = dateFontSize,
                        lineHeight = dateLineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrentDay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = day.dayName,
                        fontSize = dayNameFontSize,
                        lineHeight = dayNameLineHeight,
                        fontWeight = FontWeight.Medium,
                        color = if (isCurrentDay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
@Composable
private fun ZoomButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun lessonCardColor(type: String): Color {
    val colors = listOf(
        Color(0xFFFFE0E6),
        Color(0xFFFFF3C7),
        Color(0xFFDFF7DF),
        Color(0xFFDCEEFF),
        Color(0xFFE8E1FF),
        Color(0xFFFFE8D6),
        Color(0xFFDDF7F4),
        Color(0xFFF0E6D6)
    )
    val index = (type.hashCode() and 0x7fffffff) % colors.size
    return colors[index]
}

private fun WeekDayHeader.dayWithMonthLabel(): String {
    val parts = date.split("-")
    val day = parts.getOrNull(0)?.toIntOrNull()?.toString() ?: dayOfMonth
    val monthNumber = parts.getOrNull(1)?.toIntOrNull()
    val monthName = when (monthNumber) {
        1 -> "янв"
        2 -> "фев"
        3 -> "мар"
        4 -> "апр"
        5 -> "мая"
        6 -> "июн"
        7 -> "июл"
        8 -> "авг"
        9 -> "сен"
        10 -> "окт"
        11 -> "ноя"
        12 -> "дек"
        else -> ""
    }
    return if (monthName.isBlank()) day else "$day $monthName"
}
