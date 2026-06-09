package com.shedule2

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ScheduleFaculty(
    val id: Int,
    val name: String,
    val code: String
)

data class ScheduleGroup(
    val id: Int,
    val code: String,
    val facultyId: Int
)

sealed class FacultyListResult {
    data class Success(val faculties: List<ScheduleFaculty>) : FacultyListResult()
    data class NeedCaptcha(val message: String) : FacultyListResult()
    data class Error(val message: String) : FacultyListResult()
}

sealed class FacultyGroupsResult {
    data class Success(val groups: List<ScheduleGroup>) : FacultyGroupsResult()
    data class NeedCaptcha(val message: String) : FacultyGroupsResult()
    data class Error(val message: String) : FacultyGroupsResult()
}

class ScheduleApi(
    private val client: OkHttpClient
) {

    fun fetchSchedule(date: String, groupId: String): ScheduleResult {
        return try {
            val bootstrapError = bootstrapScheduleModule()
            if (bootstrapError != null) {
                return ScheduleResult.Error(bootstrapError)
            }

            val url = "https://lk.gubkin.ru/schedule/api/api.php?act=schedule&date=$date&groupId=$groupId"

            val request = scheduleRequest(url).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (response.code == 429) {
                    val message = extractReason(body).ifBlank { "Необходимо пройти капчу" }
                    return ScheduleResult.NeedCaptcha(message)
                }

                if (response.code == 500 && containsForbiddenReason(body)) {
                    val message = extractReason(body).ifBlank {
                        "Ресурс запрещен. Необходимо пройти капчу"
                    }
                    return ScheduleResult.NeedCaptcha(message)
                }

                if (response.isSuccessful) {
                    if (containsCaptchaReason(body) || containsForbiddenReason(body)) {
                        val message = extractReason(body).ifBlank { "Необходимо пройти капчу" }
                        return ScheduleResult.NeedCaptcha(message)
                    }
                    return ScheduleResult.Success(body)
                }

                ScheduleResult.Error("HTTP ${response.code}\n\n$body")
            }
        } catch (e: Exception) {
            ScheduleResult.Error("${e::class.java.simpleName}\n${e.message}")
        }
    }

    fun fetchFaculties(): FacultyListResult {
        return try {
            val bootstrapError = bootstrapScheduleModule()
            if (bootstrapError != null) {
                return FacultyListResult.Error(bootstrapError)
            }

            val url = "https://lk.gubkin.ru/schedule/api/api.php?act=list&method=getFaculties"
            val request = scheduleRequest(url).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val captchaMessage = captchaMessageOrNull(response.code, body)
                if (captchaMessage != null) {
                    return FacultyListResult.NeedCaptcha(captchaMessage)
                }

                if (!response.isSuccessful) {
                    return FacultyListResult.Error("HTTP ${response.code}\n\n$body")
                }

                val json = JSONObject(body)
                if (!json.optBoolean("state", false)) {
                    return FacultyListResult.Error(json.optString("reason", "Не удалось загрузить факультеты."))
                }

                val rows = json.optJSONArray("rows")
                    ?: return FacultyListResult.Success(emptyList())

                val faculties = buildList {
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        add(
                            ScheduleFaculty(
                                id = item.optInt("id"),
                                name = item.optString("name"),
                                code = item.optString("code")
                            )
                        )
                    }
                }.sortedWith(compareBy<ScheduleFaculty> { it.code }.thenBy { it.name })

                FacultyListResult.Success(faculties)
            }
        } catch (e: Exception) {
            FacultyListResult.Error("${e::class.java.simpleName}\n${e.message}")
        }
    }

    fun fetchFacultyGroups(facultyId: Int): FacultyGroupsResult {
        return try {
            val bootstrapError = bootstrapScheduleModule()
            if (bootstrapError != null) {
                return FacultyGroupsResult.Error(bootstrapError)
            }

            val url = "https://lk.gubkin.ru/schedule/api/api.php?act=list&method=getFacultyGroups&facultyId=$facultyId"
            val request = scheduleRequest(url).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val captchaMessage = captchaMessageOrNull(response.code, body)
                if (captchaMessage != null) {
                    return FacultyGroupsResult.NeedCaptcha(captchaMessage)
                }

                if (!response.isSuccessful) {
                    return FacultyGroupsResult.Error("HTTP ${response.code}\n\n$body")
                }

                val json = JSONObject(body)
                if (!json.optBoolean("state", false)) {
                    return FacultyGroupsResult.Error(json.optString("reason", "Не удалось загрузить группы."))
                }

                val rows = json.optJSONArray("rows")
                    ?: return FacultyGroupsResult.Success(emptyList())

                val groups = buildList {
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        add(
                            ScheduleGroup(
                                id = item.optInt("id"),
                                code = item.optString("code"),
                                facultyId = item.optInt("facultyId", facultyId)
                            )
                        )
                    }
                }.sortedBy { it.code }

                FacultyGroupsResult.Success(groups)
            }
        } catch (e: Exception) {
            FacultyGroupsResult.Error("${e::class.java.simpleName}\n${e.message}")
        }
    }

    private fun bootstrapScheduleModule(): String? {
        val pageError = simpleGet(
            "https://lk.gubkin.ru/schedule/",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            referer = "https://lk.gubkin.ru/"
        )
        if (pageError != null) return pageError

        val metaError = simpleGet(
            "https://lk.gubkin.ru/schedule/api/api.php?act=meta",
            accept = "application/json, text/plain, */*",
            referer = "https://lk.gubkin.ru/schedule/"
        )
        if (metaError != null) return metaError

        val studiesError = simpleGet(
            "https://lk.gubkin.ru/schedule/api/api.php?act=list&method=getStudies",
            accept = "application/json, text/plain, */*",
            referer = "https://lk.gubkin.ru/schedule/"
        )
        if (studiesError != null) return studiesError

        return null
    }

    private fun simpleGet(url: String, accept: String, referer: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", accept)
            .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .addHeader("Referer", referer)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                return "Ошибка инициализации расписания: HTTP ${response.code}" +
                        (if (body.isNotBlank()) "\n$body" else "")
            }
        }

        return null
    }

    private fun scheduleRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .addHeader("Referer", "https://lk.gubkin.ru/schedule/")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            )
    }

    private fun captchaMessageOrNull(code: Int, body: String): String? {
        if (code == 429) {
            return extractReason(body).ifBlank { "Необходимо пройти капчу" }
        }

        if (code == 500 && containsForbiddenReason(body)) {
            return extractReason(body).ifBlank { "Ресурс запрещен. Необходимо пройти капчу" }
        }

        if (containsCaptchaReason(body) || containsForbiddenReason(body)) {
            return extractReason(body).ifBlank { "Необходимо пройти капчу" }
        }

        return null
    }

    private fun containsCaptchaReason(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            json.optString("reason", "").contains("капч", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun containsForbiddenReason(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            json.optString("reason", "").contains("запрещ", ignoreCase = true)
        } catch (_: Exception) {
            body.contains("запрещ", ignoreCase = true)
        }
    }

    private fun extractReason(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("reason", "")
        } catch (_: Exception) {
            ""
        }
    }
}
