package com.shedule2

import org.json.JSONObject

fun parseWeekDays(rawJson: String): List<WeekDayHeader> {
    return try {
        val root = JSONObject(rawJson)
        val days = root
            .getJSONObject("rows")
            .getJSONObject("week")
            .getJSONObject("weekRussia")
            .getJSONArray("days")

        val dayNames = listOf(
            "Понедельник",
            "Вторник",
            "Среда",
            "Четверг",
            "Пятница",
            "Суббота",
            "Воскресенье"
        )

        buildList {
            for (i in 0 until days.length()) {
                val day = days.getJSONObject(i)
                val weekDayNumber = day.getInt("weekDayNumber")
                val date = day.getString("date")
                val dayOfMonth = date.substringBefore("-")
                add(
                    WeekDayHeader(
                        weekDayNumber = weekDayNumber,
                        dayName = dayNames.getOrElse(weekDayNumber) { "День" },
                        date = date,
                        dayOfMonth = dayOfMonth,
                        isStudyDay = day.optBoolean("isStudyDay", true)
                    )
                )
            }
        }.sortedBy { it.weekDayNumber }
    } catch (_: Exception) {
        emptyList()
    }
}
