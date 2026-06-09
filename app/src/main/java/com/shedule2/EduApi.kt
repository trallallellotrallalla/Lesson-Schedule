package com.shedule2

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class EduLoginResult {
    object Success : EduLoginResult()
    data class Error(val message: String) : EduLoginResult()
}

sealed class EduPageResult {
    data class Success(val html: String, val finalUrl: String) : EduPageResult()
    data class Error(val message: String) : EduPageResult()
}

class EduApi(private val client: OkHttpClient) {

    fun isLoginPage(html: String, finalUrl: String = ""): Boolean {
        // Definitive signs that the user is already authenticated.
        if (html.contains("login/logout.php", ignoreCase = true) ||
            html.contains("logininfo", ignoreCase = true)
        ) {
            return false
        }

        if (finalUrl.contains("/login/", ignoreCase = true)) return true

        val hasUsernameField = html.contains("name=\"username\"", ignoreCase = true)
        val hasPasswordField = html.contains("name=\"password\"", ignoreCase = true)
        val hasLoginToken = html.contains("name=\"logintoken\"", ignoreCase = true)

        return hasLoginToken || (hasUsernameField && hasPasswordField)
    }

    fun login(username: String, password: String): EduLoginResult {
        val (loginPageHtml, pageUrl) = runCatching {
            client.newCall(
                baseRequest("https://edu.gubkin.ru/login/index.php").get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return EduLoginResult.Error("HTTP ${response.code} при открытии формы входа.")
                }
                (response.body?.string().orEmpty()) to response.request.url.toString()
            }
        }.getOrElse { return EduLoginResult.Error("Не удалось открыть страницу входа: ${it.message}") }

        // Extract <form id="login"> block (fallback to anything that looks like a login form).
        val formHtml = Regex(
            """<form\b[^>]*id=["']login["'][^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE
        ).find(loginPageHtml)?.value
            ?: Regex(
                """<form\b[^>]*action=["'][^"']*login[^"']*["'][^>]*>([\s\S]*?)</form>""",
                RegexOption.IGNORE_CASE
            ).find(loginPageHtml)?.value
            ?: return EduLoginResult.Error("Не найдена форма входа на edu.gubkin.ru.")

        val actionRaw = Regex("""action=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(formHtml)?.groupValues?.get(1)
            ?: "https://edu.gubkin.ru/login/index.php"
        val actionUrl = resolveUrl(pageUrl, actionRaw)

        val fields = linkedMapOf<String, String>()

        Regex(
            """<input\b[^>]*\bname=["']([^"']+)["'][^>]*\bvalue=["']([^"']*)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        ).findAll(formHtml).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            fields.putIfAbsent(name, value)
        }

        fields["username"] = username
        fields["password"] = password
        fields.putIfAbsent("anchor", "")
        fields.putIfAbsent("rememberusername", "1")

        val formBuilder = FormBody.Builder().apply {
            fields.forEach { (n, v) -> add(n, v) }
        }
        val seenNames = fields.keys

        return runCatching {
            client.newCall(
                baseRequest(actionUrl)
                    .post(formBuilder.build())
                    .addHeader("Origin", "https://edu.gubkin.ru")
                    .addHeader("Referer", "https://edu.gubkin.ru/login/index.php")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return EduLoginResult.Error("HTTP ${response.code} при отправке формы входа.")
                }

                val body = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                val hasExplicitError = body.contains("loginerrors", ignoreCase = true) ||
                    body.contains("login-error-message", ignoreCase = true) ||
                    body.contains("Неверный логин", ignoreCase = true) ||
                    body.contains("Неверный пароль", ignoreCase = true) ||
                    body.contains("Invalid login", ignoreCase = true)

                if (hasExplicitError) {
                    EduLoginResult.Error("Неверный логин или пароль для edu.gubkin.ru.")
                } else if (finalUrl.contains("/login/", ignoreCase = true) &&
                    !body.contains("logininfo", ignoreCase = true)) {
                    EduLoginResult.Error(
                        "После входа всё ещё на форме (action=$actionUrl). " +
                            "Поля формы: ${seenNames.joinToString()}"
                    )
                } else {
                    EduLoginResult.Success
                }
            }
        }.getOrElse { EduLoginResult.Error("Ошибка при входе: ${it.message}") }
    }

    private fun resolveUrl(baseUrl: String, action: String): String {
        if (action.isBlank()) return baseUrl
        if (action.startsWith("http://") || action.startsWith("https://")) return action
        return runCatching {
            baseUrl.toHttpUrlOrNull()?.resolve(action)?.toString() ?: baseUrl
        }.getOrDefault(baseUrl)
    }

    fun fetchCoursesHtml(): EduPageResult = fetchHtml("https://edu.gubkin.ru/my/courses.php")

    fun extractSesskey(html: String): String? {
        val patterns = listOf(
            Regex("""["']sesskey["']\s*:\s*["']([A-Za-z0-9]+)["']"""),
            Regex("""sesskey=([A-Za-z0-9]+)""")
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
    }

    fun fetchEnrolledCoursesJson(sesskey: String): EduPageResult {
        val url = "https://edu.gubkin.ru/lib/ajax/service.php" +
            "?sesskey=$sesskey&info=core_course_get_enrolled_courses_by_timeline_classification"
        val body = """[{"index":0,"methodname":"core_course_get_enrolled_courses_by_timeline_classification","args":{"offset":0,"limit":0,"classification":"inprogress","sort":"fullname","customfieldname":"","customfieldvalue":""}}]"""
        return runCatching {
            client.newCall(
                baseRequest(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()
            ).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                if (!response.isSuccessful) {
                    EduPageResult.Error("HTTP ${response.code} (url=$finalUrl)")
                } else {
                    EduPageResult.Success(respBody, finalUrl)
                }
            }
        }.getOrElse { EduPageResult.Error("${it::class.java.simpleName}: ${it.message}") }
    }

    fun fetchGradeHtml(courseId: Int): EduPageResult =
        fetchHtml("https://edu.gubkin.ru/grade/report/user/index.php?id=$courseId")

    private fun fetchHtml(url: String): EduPageResult {
        return runCatching {
            client.newCall(baseRequest(url).get().build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()
                if (!response.isSuccessful) {
                    EduPageResult.Error("HTTP ${response.code} (url=$finalUrl)")
                } else {
                    EduPageResult.Success(body, finalUrl)
                }
            }
        }.getOrElse { EduPageResult.Error("${it::class.java.simpleName}: ${it.message}") }
    }

    private fun baseRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .addHeader("Referer", "https://edu.gubkin.ru/")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
            )
    }
}
