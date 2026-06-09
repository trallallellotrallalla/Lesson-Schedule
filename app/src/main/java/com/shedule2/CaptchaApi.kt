package com.shedule2

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed class CaptchaImageResult {
    data class Success(val bytes: ByteArray) : CaptchaImageResult()
    data class Error(val message: String) : CaptchaImageResult()
}

sealed class CaptchaValidateResult {
    object Success : CaptchaValidateResult()
    data class Error(val message: String) : CaptchaValidateResult()
}

class CaptchaApi(
    private val client: OkHttpClient,
    private val hasInternetConnection: (() -> Boolean)? = null
) {

    private fun noInternetError(): String? {
        val isConnected = hasInternetConnection?.invoke() ?: true
        return if (isConnected) {
            null
        } else {
            "Нет подключения к интернету. Подключитесь к сети и попробуйте снова."
        }
    }

    fun generateCaptchaImage(): CaptchaImageResult {
        noInternetError()?.let { message ->
            return CaptchaImageResult.Error(message)
        }

        return try {
            val bootstrapError = bootstrapScheduleModule()
            if (bootstrapError != null) {
                return CaptchaImageResult.Error(bootstrapError)
            }

            val request = Request.Builder()
                .url("https://lk.gubkin.ru/schedule/api/api.php?act=Captcha&method=generateCaptcha")
                .get()
                .addHeader("Accept", "image/*,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://lk.gubkin.ru/schedule/")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    return CaptchaImageResult.Error(
                        "Не удалось получить капчу: HTTP ${response.code}" +
                                (if (body.isNotBlank()) "\n$body" else "")
                    )
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return CaptchaImageResult.Error("Сервер вернул пустую капчу.")
                }

                CaptchaImageResult.Success(bytes)
            }
        } catch (e: Exception) {
            CaptchaImageResult.Error("${e::class.java.simpleName}: ${e.message}")
        }
    }

    fun validateCaptcha(code: String): CaptchaValidateResult {
        noInternetError()?.let { message ->
            return CaptchaValidateResult.Error(message)
        }

        return try {
            val bootstrapError = bootstrapScheduleModule()
            if (bootstrapError != null) {
                return CaptchaValidateResult.Error(bootstrapError)
            }

            val payload = JSONObject()
                .put("key", code)
                .toString()

            val request = Request.Builder()
                .url("https://lk.gubkin.ru/schedule/api/api.php?act=Captcha&method=validateCaptcha")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Content-Type", "application/json")
                .addHeader("Referer", "https://lk.gubkin.ru/schedule/")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return CaptchaValidateResult.Error(
                        "Ошибка проверки капчи: HTTP ${response.code}" +
                                (if (body.isNotBlank()) "\n$body" else "")
                    )
                }

                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return CaptchaValidateResult.Error("Некорректный ответ сервера.")

                val state = json.optBoolean("state", false)
                if (state) {
                    CaptchaValidateResult.Success
                } else {
                    val reason = json.optString("reason", "Неверный код капчи.")
                    CaptchaValidateResult.Error(reason)
                }
            }
        } catch (e: Exception) {
            CaptchaValidateResult.Error("${e::class.java.simpleName}: ${e.message}")
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
}