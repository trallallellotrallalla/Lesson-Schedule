package com.shedule2

data class SimpleLesson(
    val date: String,
    val dayName: String,
    val time: String,
    val slotLabels: List<String>,
    val subject: String,
    val type: String,
    val room: String,
    val teacher: String,
    val teacherFullName: String = "",
    val departments: String = "",
    val isCanceled: Boolean = false,
    val note: String? = null
)
