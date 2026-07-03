package com.example.tconfirmo.data.fcm

import android.content.Context

class FcmTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "tconfirmo_fcm"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
}
