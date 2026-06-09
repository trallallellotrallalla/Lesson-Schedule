package com.shedule2

sealed class ScheduleResult {
    data class Success(val json: String) : ScheduleResult()
    data class NeedCaptcha(val message: String) : ScheduleResult()
    data class Error(val message: String) : ScheduleResult()
}