package com.shedule2

import android.content.Context

data class CachedSchedule(
    val rawJson: String,
    val date: String,
    val groupId: String
)

object ScheduleCache {
    private const val PREFS_NAME = "schedule_cache"
    private const val KEY_RAW_JSON = "raw_json"
    private const val KEY_DATE = "date"
    private const val KEY_GROUP_ID = "group_id"

    fun save(context: Context, rawJson: String, date: String, groupId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RAW_JSON, rawJson)
            .putString(KEY_DATE, date)
            .putString(KEY_GROUP_ID, groupId)
            .apply()
    }

    fun load(context: Context, date: String? = null, groupId: String? = null): CachedSchedule? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RAW_JSON, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val cachedDate = prefs.getString(KEY_DATE, null).orEmpty()
        val cachedGroupId = prefs.getString(KEY_GROUP_ID, null).orEmpty()

        if (date != null && cachedDate != date) return null
        if (groupId != null && cachedGroupId != groupId) return null

        return CachedSchedule(
            rawJson = rawJson,
            date = cachedDate,
            groupId = cachedGroupId
        )
    }

    fun loadRawJson(context: Context): String? {
        return load(context)?.rawJson
    }

    fun loadDate(context: Context): String? {
        return load(context)?.date
    }

    fun loadGroupId(context: Context): String? {
        return load(context)?.groupId
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
