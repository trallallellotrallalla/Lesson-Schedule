package com.shedule2

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

class SessionCookieJar(
    context: Context
) : CookieJar {

    private val prefs = context.getSharedPreferences("session_cookie_jar", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val current = loadAllMutable()
        val now = System.currentTimeMillis()

        cookies.forEach { cookie ->
            val key = cookieKey(cookie)
            if (cookie.expiresAt < now) {
                current.remove(key)
            } else {
                current[key] = cookie
            }
        }

        persist(current.values.toList())

        Log.d(
            "SessionCookieJar",
            "saveFromResponse url=$url cookies=${cookies.joinToString { "${it.name}=${it.value}; domain=${it.domain}; path=${it.path}" }}"
        )
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val current = loadAllMutable()

        val valid = current.values.filter { cookie ->
            cookie.expiresAt >= now && cookie.matches(url)
        }

        val cleaned = current.filterValues { it.expiresAt >= now }
        persist(cleaned.values.toList())

        Log.d(
            "SessionCookieJar",
            "loadForRequest url=$url cookies=${valid.joinToString { "${it.name}=${it.value}; domain=${it.domain}; path=${it.path}" }}"
        )

        return valid
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun importFromRawCookieHeader(baseUrl: String, rawCookieHeader: String) {
        if (rawCookieHeader.isBlank()) return

        val host = baseUrl.toHttpUrl().host
        val imported = rawCookieHeader
            .split(";")
            .mapNotNull { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@mapNotNull null

                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()

                runCatching {
                    Cookie.Builder()
                        .domain(host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }.getOrNull()
            }

        if (imported.isNotEmpty()) {
            val current = loadAllMutable()
            imported.forEach { current[cookieKey(it)] = it }
            persist(current.values.toList())
        }
    }

    private fun persist(cookies: List<Cookie>) {
        val array = JSONArray()
        cookies.forEach { cookie ->
            val obj = JSONObject()
            obj.put("name", cookie.name)
            obj.put("value", cookie.value)
            obj.put("expiresAt", cookie.expiresAt)
            obj.put("domain", cookie.domain)
            obj.put("path", cookie.path)
            obj.put("secure", cookie.secure)
            obj.put("httpOnly", cookie.httpOnly)
            obj.put("hostOnly", cookie.hostOnly)
            obj.put("persistent", cookie.persistent)
            array.put(obj)
        }

        prefs.edit()
            .putString("cookies_json", array.toString())
            .apply()
    }

    private fun loadAllMutable(): MutableMap<String, Cookie> {
        val raw = prefs.getString("cookies_json", null).orEmpty()
        if (raw.isBlank()) return mutableMapOf()

        val result = mutableMapOf<String, Cookie>()

        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(obj.getString("name"))
                    .value(obj.getString("value"))
                    .path(obj.getString("path"))

                val hostOnly = obj.optBoolean("hostOnly", false)
                val domain = obj.getString("domain")
                if (hostOnly) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }

                if (obj.optBoolean("secure", false)) builder.secure()
                if (obj.optBoolean("httpOnly", false)) builder.httpOnly()

                if (obj.optBoolean("persistent", false)) {
                    builder.expiresAt(obj.getLong("expiresAt"))
                }

                val cookie = builder.build()
                result[cookieKey(cookie)] = cookie
            }
        }

        return result
    }

    private fun cookieKey(cookie: Cookie): String {
        return "${cookie.domain}|${cookie.path}|${cookie.name}"
    }
}