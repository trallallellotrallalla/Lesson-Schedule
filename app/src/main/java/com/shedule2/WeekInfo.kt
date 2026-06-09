package com.shedule2

import org.json.JSONObject

data class WeekInfo(
    val number: Int,
    val type: String
) {
    fun typeName(): String = when (type.lowercase()) {
        "upper" -> "Верхняя"
        "lower" -> "Нижняя"
        else -> type
    }
}

fun parseWeekInfo(rawJson: String): WeekInfo? {
    return runCatching {
        val weekRussia = JSONObject(rawJson)
            .getJSONObject("rows")
            .getJSONObject("week")
            .getJSONObject("weekRussia")

        val number = weekRussia.optInt("number", -1)
        val type = weekRussia.optString("type", "")

        if (number <= 0 && type.isBlank()) null
        else WeekInfo(number = number, type = type)
    }.getOrNull()
}
