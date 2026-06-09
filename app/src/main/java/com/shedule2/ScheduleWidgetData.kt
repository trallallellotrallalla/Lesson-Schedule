package com.shedule2

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ScheduleWidgetData {
    private const val PREFS_NAME = "schedule_widget_prefs"
    private const val KEY_SELECTED_DAY_PREFIX = "selected_day_"
    private val cacheDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d-M-uuuu")

    private fun selectedDayKey(appWidgetId: Int): String {
        return KEY_SELECTED_DAY_PREFIX + appWidgetId
    }

    private fun getStoredSelectedDay(context: Context, appWidgetId: Int): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = selectedDayKey(appWidgetId)
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun getSelectedDay(context: Context, appWidgetId: Int): Int {
        return getStoredSelectedDay(context, appWidgetId) ?: getCurrentDateWeekDayNumber()
    }

    fun setSelectedDay(context: Context, appWidgetId: Int, weekDayNumber: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(selectedDayKey(appWidgetId), weekDayNumber)
            .apply()
    }

    fun removeSelectedDay(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(selectedDayKey(appWidgetId))
            .apply()
    }

    fun getCachedWeekDays(context: Context): List<WeekDayHeader> {
        val rawJson = ScheduleCache.loadRawJson(context).orEmpty()
        return parseWeekDays(rawJson)
            .filter { it.weekDayNumber in 0..6 }
            .sortedBy { it.weekDayNumber }
    }

    fun getCachedLessons(context: Context): List<SimpleLesson> {
        val rawJson = ScheduleCache.loadRawJson(context).orEmpty()
        return if (rawJson.isBlank()) emptyList() else ScheduleParser.parseSimple(rawJson)
    }

    fun getEffectiveSelectedDay(context: Context, appWidgetId: Int): Int {
        val weekDays = getCachedWeekDays(context)
        if (weekDays.isEmpty()) return getCurrentDateWeekDayNumber()

        val stored = getStoredSelectedDay(context, appWidgetId)
        if (stored != null) {
            weekDays.firstOrNull { it.weekDayNumber == stored }?.let { return it.weekDayNumber }
        }

        val today = LocalDate.now()
        return weekDays.firstOrNull { it.isSameDate(today) }?.weekDayNumber
            ?: weekDays.firstOrNull { it.weekDayNumber == getCurrentDateWeekDayNumber(today) }?.weekDayNumber
            ?: weekDays.first().weekDayNumber
    }

    fun getSelectedWeekDayHeader(context: Context, appWidgetId: Int): WeekDayHeader? {
        val selected = getEffectiveSelectedDay(context, appWidgetId)
        return getCachedWeekDays(context).firstOrNull { it.weekDayNumber == selected }
    }

    fun getPreviousCachedDay(context: Context, currentDay: Int): Int {
        val days = getCachedWeekDays(context).map { it.weekDayNumber }
        if (days.isEmpty()) return currentDay
        val index = days.indexOf(currentDay).takeIf { it >= 0 } ?: 0
        return days[(index - 1 + days.size) % days.size]
    }

    fun getNextCachedDay(context: Context, currentDay: Int): Int {
        val days = getCachedWeekDays(context).map { it.weekDayNumber }
        if (days.isEmpty()) return currentDay
        val index = days.indexOf(currentDay).takeIf { it >= 0 } ?: 0
        return days[(index + 1) % days.size]
    }

    fun getPreviousCachedDayHeader(context: Context, currentDay: Int): WeekDayHeader? {
        val previous = getPreviousCachedDay(context, currentDay)
        return getCachedWeekDays(context).firstOrNull { it.weekDayNumber == previous }
    }

    fun getNextCachedDayHeader(context: Context, currentDay: Int): WeekDayHeader? {
        val next = getNextCachedDay(context, currentDay)
        return getCachedWeekDays(context).firstOrNull { it.weekDayNumber == next }
    }

    private fun WeekDayHeader.isSameDate(date: LocalDate): Boolean {
        return runCatching {
            LocalDate.parse(this.date.trim(), cacheDateFormatter) == date
        }.getOrDefault(false)
    }

    private fun getCurrentDateWeekDayNumber(date: LocalDate = LocalDate.now()): Int {
        return date.dayOfWeek.value - 1
    }

    fun getLessonsForSelectedDay(context: Context, appWidgetId: Int): List<SimpleLesson> {
        val header = getSelectedWeekDayHeader(context, appWidgetId) ?: return emptyList()
        return getCachedLessons(context)
            .filter { it.dayName == header.dayName && !it.isCanceled }
            .sortedBy { lesson ->
                val start = lesson.slotLabels.firstOrNull().orEmpty().substringBefore("-")
                val parts = start.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                hour * 60 + minute
            }
    }
}
