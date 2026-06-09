package com.shedule2

import org.json.JSONArray
import org.json.JSONObject

object ScheduleParser {

    private val dayNames = listOf(
        "Понедельник",
        "Вторник",
        "Среда",
        "Четверг",
        "Пятница",
        "Суббота",
        "Воскресенье"
    )

    fun parseSimple(json: String): List<SimpleLesson> {
        val root = JSONObject(json)

        if (!root.optBoolean("state", false)) {
            return emptyList()
        }

        val rows = root.getJSONObject("rows")
        val weekRussiaDays = rows
            .getJSONObject("week")
            .getJSONObject("weekRussia")
            .getJSONArray("days")

        val organizations = rows.getJSONArray("organizations")
        val moscow = findMoscowOrganization(organizations) ?: organizations.getJSONObject(0)

        val timeChunks = moscow.getJSONArray("lessonsTimeChunks")
        val lessons = moscow.getJSONArray("lessons")

        val dateByWeekDay = mutableMapOf<Int, String>()
        for (i in 0 until weekRussiaDays.length()) {
            val day = weekRussiaDays.getJSONObject(i)
            dateByWeekDay[day.getInt("weekDayNumber")] = day.getString("date")
        }

        val result = mutableListOf<SimpleLesson>()

        for (i in 0 until lessons.length()) {
            val lesson = lessons.getJSONObject(i)

            val weekDayNumber = lesson.optInt("weekDayNumber", -1)
            if (weekDayNumber !in 0..6) continue

            val date = dateByWeekDay[weekDayNumber].orEmpty()
            val dayName = dayNames[weekDayNumber]

            val subject = lesson
                .optJSONObject("course")
                ?.optString("name", "")
                .orEmpty()

            val type = lesson.optString("type", "")
            val slotLabels = parseSlotLabels(lesson.optJSONArray("timeChunks"), timeChunks)
            val time = buildDisplayTime(slotLabels)
            val room = parseRoom(lesson)
            val teacher = parseTeacher(lesson)
            val teacherFullName = parseTeacherFullName(lesson)
            val departments = parseDepartments(lesson)
            val isCanceled = lesson.optBoolean("isCanceled", false)
            val note = parseNote(lesson)

            result.add(
                SimpleLesson(
                    date = date,
                    dayName = dayName,
                    time = time,
                    slotLabels = slotLabels,
                    subject = subject,
                    type = type,
                    room = room,
                    teacher = teacher,
                    teacherFullName = teacherFullName,
                    departments = departments,
                    isCanceled = isCanceled,
                    note = note
                )
            )
        }

        return result.sortedWith(
            compareBy<SimpleLesson>(
                { it.date },
                { slotStartToSortableValue(it.slotLabels.firstOrNull().orEmpty()) }
            )
        )
    }

    private fun findMoscowOrganization(organizations: JSONArray): JSONObject? {
        for (i in 0 until organizations.length()) {
            val org = organizations.getJSONObject(i)
            if (org.optString("name") == "Москва") {
                return org
            }
        }
        return null
    }

    private fun parseSlotLabels(ids: JSONArray?, chunks: JSONArray): List<String> {
        if (ids == null || ids.length() == 0) return emptyList()

        val result = mutableListOf<String>()
        for (i in 0 until ids.length()) {
            val chunkIndex = ids.getInt(i)
            result += chunks.getString(chunkIndex)
        }
        return result
    }

    private fun buildDisplayTime(slotLabels: List<String>): String {
        if (slotLabels.isEmpty()) return ""
        val start = slotLabels.first().substringBefore("-")
        val end = slotLabels.last().substringAfter("-")
        return "$start-$end"
    }

    private fun parseRoom(lesson: JSONObject): String {
        val changedRooms = lesson.optJSONObject("changes")?.optJSONArray("rooms")
        if (changedRooms != null && changedRooms.length() > 0) {
            return changedRooms.getJSONObject(0).optString("number", "")
        }

        val rooms = lesson.optJSONArray("rooms")
        if (rooms != null && rooms.length() > 0) {
            return rooms.getJSONObject(0).optString("number", "")
        }

        return ""
    }

    private fun parseTeacher(lesson: JSONObject): String {
        return parseTeacherName(lesson, ::shortName)
    }

    private fun parseTeacherFullName(lesson: JSONObject): String {
        return parseTeacherName(lesson, ::fullName)
    }

    private fun parseTeacherName(
        lesson: JSONObject,
        formatter: (JSONObject) -> String
    ): String {
        val changedTeachers = lesson.optJSONObject("changes")?.optJSONArray("teachers")
        if (changedTeachers != null && changedTeachers.length() > 0) {
            return formatter(changedTeachers.getJSONObject(0))
        }

        val teachers = lesson.optJSONArray("teachers")
        if (teachers != null && teachers.length() > 0) {
            return formatter(teachers.getJSONObject(0))
        }

        return ""
    }

    private fun fullName(obj: JSONObject): String {
        val lastName = obj.optString("lastName")
        val firstName = obj.optString("firstName")
        val patronymic = obj.optString("patronymic")

        return listOf(lastName, firstName, patronymic)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank {
                obj.optString("fullName")
                    .ifBlank { obj.optString("name") }
            }
    }

    private fun shortName(obj: JSONObject): String {
        val lastName = obj.optString("lastName")
        val firstName = obj.optString("firstName")
        val patronymic = obj.optString("patronymic")

        val firstInitial = firstName.firstOrNull()?.let { "$it." }.orEmpty()
        val patronymicInitial = patronymic.firstOrNull()?.let { "$it." }.orEmpty()

        return listOf(lastName, firstInitial, patronymicInitial)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun parseDepartments(lesson: JSONObject): String {
        val departments = mutableListOf<String>()

        fun collectFrom(obj: JSONObject?) {
            if (obj == null) return
            departmentKeys.forEach { key ->
                if (obj.has(key)) {
                    departments += departmentTexts(obj.opt(key))
                }
            }
        }

        val teacherSources = lesson.optJSONObject("changes")
            ?.optJSONArray("teachers")
            ?.takeIf { it.length() > 0 }
            ?: lesson.optJSONArray("teachers")

        if (teacherSources != null) {
            for (i in 0 until teacherSources.length()) {
                collectFrom(teacherSources.optJSONObject(i))
            }
        }

        collectFrom(lesson.optJSONObject("course"))
        collectFrom(lesson)

        return departments
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun departmentTexts(value: Any?): List<String> {
        return when (value) {
            null, JSONObject.NULL -> emptyList()
            is String -> listOf(value)
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    addAll(departmentTexts(value.opt(i)))
                }
            }
            is JSONObject -> {
                val directName = departmentNameKeys
                    .firstNotNullOfOrNull { key ->
                        value.optString(key).takeIf { it.isNotBlank() }
                    }
                if (directName != null) {
                    listOf(directName)
                } else {
                    departmentKeys.flatMap { key ->
                        if (value.has(key)) departmentTexts(value.opt(key)) else emptyList()
                    }
                }
            }
            else -> listOf(value.toString())
        }
    }

    private fun parseNote(lesson: JSONObject): String? {
        val notes = mutableListOf<String>()

        if (lesson.optBoolean("isCanceled", false)) {
            notes += "Отменено"
        }

        val movedFrom = lesson.optString("movedFrom")
        val movedTo = lesson.optString("movedTo")
        if (movedFrom.isNotBlank() && movedTo.isNotBlank()) {
            notes += "Перенос: $movedFrom -> $movedTo"
        }

        return notes.takeIf { it.isNotEmpty() }?.joinToString(". ")
    }

    private fun slotStartToSortableValue(slot: String): Int {
        val start = slot.substringBefore("-")
        val parts = start.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }

    private val departmentKeys = listOf(
        "departments",
        "department",
        "departmentName",
        "chairs",
        "chair",
        "chairName",
        "cathedras",
        "cathedra",
        "cathedraName",
        "cafedras",
        "cafedra",
        "cafedraName",
        "kafedras",
        "kafedra",
        "kafedraName"
    )

    private val departmentNameKeys = listOf(
        "name",
        "fullName",
        "title",
        "caption",
        "value"
    )
}
