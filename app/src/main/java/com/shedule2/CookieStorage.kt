package com.shedule2

import android.content.Context
import android.webkit.CookieManager

object CookieStorage {
    private const val PREFS_NAME = "schedule_cookies"
    private const val KEY_COOKIES = "cookies"
    private const val BASE_URL = "https://lk.gubkin.ru"

    fun saveCookies(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(BASE_URL).orEmpty()

        prefs.edit()
            .putString(KEY_COOKIES, cookies)
            .apply()

        cookieManager.flush()
    }

    fun restoreCookies(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookies = prefs.getString(KEY_COOKIES, null) ?: return

        val cookieManager = CookieManager.getInstance()
        cookies.split(";").forEach { raw ->
            val cookie = raw.trim()
            if (cookie.isNotEmpty()) {
                cookieManager.setCookie(BASE_URL, cookie)
            }
        }
        cookieManager.flush()
    }

    fun clearCookies(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun getCookieHeader(): String {
        return CookieManager.getInstance().getCookie(BASE_URL).orEmpty()
    }
}