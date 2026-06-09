package com.shedule2

import okhttp3.OkHttpClient
import okhttp3.Request

sealed class PerformanceResult {
    data class Success(val json: String) : PerformanceResult()
    data class Error(val message: String) : PerformanceResult()
}

class PerformanceApi(private val client: OkHttpClient) {

    fun fetchPerformance(login: String): PerformanceResult {
        return try {
            val url = "https://lk.gubkin.ru/api/api.php" +
                "?module=study&resource=Performance&method=getPerformance" +
                "&studentId=${login.trim()}-1"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://lk.gubkin.ru/")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return PerformanceResult.Error("HTTP ${response.code}\n\n$body")
                }
                PerformanceResult.Success(body)
            }
        } catch (e: Exception) {
            PerformanceResult.Error("${e::class.java.simpleName}\n${e.message}")
        }
    }

    fun fetchPlan(login: String): PerformanceResult {
        return try {
            val url = "https://lk.gubkin.ru/ci/individual_plan/api/api.php" +
                "?act=Plan&method=getPlanByStudentId&forFrontGrid=1" +
                "&studentId=${login.trim()}-1&page=1&start=0&limit=100"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://lk.gubkin.ru/")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return PerformanceResult.Error("HTTP ${response.code}\n\n$body")
                }
                PerformanceResult.Success(body)
            }
        } catch (e: Exception) {
            PerformanceResult.Error("${e::class.java.simpleName}\n${e.message}")
        }
    }
}
