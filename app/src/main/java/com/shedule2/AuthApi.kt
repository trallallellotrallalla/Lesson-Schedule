package com.shedule2

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ActiveGroupInfo(
    val groupId: String,
    val groupName: String,
    val studentId: String?,
    val userFullName: String
)

sealed class AuthResult {
    data class Success(val activeGroup: ActiveGroupInfo) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthApi(
    private val client: OkHttpClient
) {

    fun ensureAuthorized(login: String, password: String): AuthResult {
        val existing = getActiveGroupInfo()
        if (existing != null) {
            return AuthResult.Success(existing)
        }

        val loginResult = login(login = login, password = password)
        if (loginResult != null) {
            return AuthResult.Error(loginResult)
        }

        val afterLogin = getActiveGroupInfo()
        return if (afterLogin != null) {
            AuthResult.Success(afterLogin)
        } else {
            AuthResult.Error("Не удалось получить данные пользователя после входа.")
        }
    }

    private fun login(login: String, password: String): String? {
        return try {
            val payload = JSONObject()
                .put("login", login)
                .put("password", password)
                .put("rememberMe", 1)
                .toString()

            val request = Request.Builder()
                .url("https://lk.gubkin.ru/api/api.php?module=auth&method=login")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://lk.gubkin.ru")
                .addHeader("Referer", "https://lk.gubkin.ru/login")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return "Ошибка входа: HTTP ${response.code}"
                }

                if (body.isBlank()) {
                    return null
                }

                val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
                val success = json.optBoolean("success", false)
                if (!success) {
                    val reason = json.optString("reason", "Неверный логин или пароль")
                    return reason
                }

                null
            }
        } catch (e: Exception) {
            "${e::class.java.simpleName}: ${e.message}"
        }
    }

    fun getActiveGroupInfo(): ActiveGroupInfo? {
        return try {
            val request = Request.Builder()
                .url("https://lk.gubkin.ru/api/api.php?module=User&method=getUserInfo")
                .get()
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Referer", "https://lk.gubkin.ru/")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) return null

                val result = json.getJSONObject("result")
                val firstName = result.optString("firstName")
                val lastName = result.optString("lastName")
                val patronymic = result.optString("patronymic")
                val fullName = listOf(lastName, firstName, patronymic)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                val educationInfo = result.optJSONArray("educationInfo") ?: JSONArray()

                for (i in 0 until educationInfo.length()) {
                    val item = educationInfo.getJSONObject(i)
                    if (item.optInt("isActiveStudent", 0) == 1) {
                        val group = item.optJSONObject("group") ?: continue
                        return ActiveGroupInfo(
                            groupId = group.optInt("id").toString(),
                            groupName = group.optString("name"),
                            studentId = item.optString("studentId"),
                            userFullName = fullName
                        )
                    }
                }

                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun createClient(cookieJar: SessionCookieJar): OkHttpClient {
            return OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}