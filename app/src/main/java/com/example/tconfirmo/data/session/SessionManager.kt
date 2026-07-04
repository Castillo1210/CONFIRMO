package com.example.tconfirmo.data.session

import android.content.Context
import com.example.tconfirmo.data.auth.LoginResponseDto

class SessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(response: LoginResponseDto, isTestMode: Boolean = false) {
        val expiresAt = System.currentTimeMillis() + response.expiresInSeconds * 1000L
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putBoolean(KEY_TEST_MODE, isTestMode)
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putString(KEY_REFRESH_TOKEN, response.refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_USER_ID, response.user.id)
            .putString(KEY_PHONE_NUMBER, response.user.phoneNumber)
            .putString(KEY_FULL_NAME, response.user.fullName)
            .putString(KEY_EMPRESA_ID, response.user.empresaId)
            .putString(KEY_SUCURSAL_ID, response.user.sucursalId)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return !token.isNullOrBlank() &&
            token != "mock-access-token" &&
            expiresAt > System.currentTimeMillis()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getEmpresaId(): String? = prefs.getString(KEY_EMPRESA_ID, null)

    fun getSucursalId(): String? = prefs.getString(KEY_SUCURSAL_ID, null)

    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, null)

    fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    fun isTestMode(): Boolean = prefs.getBoolean(KEY_TEST_MODE, false)

    fun updateAccessToken(accessToken: String, expiresInSeconds: Int) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "tconfirmo_session"
        const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_TEST_MODE = "test_mode"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMPRESA_ID = "empresa_id"
        private const val KEY_SUCURSAL_ID = "sucursal_id"
    }
}
