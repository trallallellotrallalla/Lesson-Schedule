package com.shedule2

import android.content.Context

data class SavedCredentials(
    val login: String,
    val password: String
)

object CredentialsStorage {
    private const val PREFS_NAME = "credentials_storage"
    private const val KEY_LOGIN = "login"
    private const val KEY_PASSWORD = "password"

    fun save(context: Context, login: String, password: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOGIN, login)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun load(context: Context): SavedCredentials? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val login = prefs.getString(KEY_LOGIN, null).orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()

        if (login.isBlank() || password.isBlank()) return null
        return SavedCredentials(login = login, password = password)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}