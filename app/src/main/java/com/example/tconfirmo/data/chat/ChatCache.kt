package com.example.tconfirmo.data.chat

import android.content.Context
import com.example.tconfirmo.data.ChatMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFS_NAME = "tconfirmo_chat_cache"
private const val KEY_MESSAGES = "cached_messages"

// Limite defensivo para que el JSON guardado en SharedPreferences no crezca
// sin fin con el tiempo de uso de la app.
private const val MAX_CACHED_MESSAGES = 300

/**
 * Cache local del feed de chat (mensajes + tarjetas de voucher ya resueltas)
 * para que la app muestre algo de inmediato al reabrirse, en vez de esperar
 * a que respondan las llamadas a api-bridge. Sigue el mismo patron de
 * SharedPreferences que ya usan SessionManager/FcmTokenStore en este proyecto.
 */
class ChatCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(json, type)
        }.getOrNull().orEmpty()
    }

    fun save(messages: List<ChatMessage>) {
        runCatching {
            val trimmed = if (messages.size > MAX_CACHED_MESSAGES) {
                messages
                    .sortedWith(compareBy({ it.date }, { it.time }))
                    .takeLast(MAX_CACHED_MESSAGES)
            } else {
                messages
            }
            prefs.edit().putString(KEY_MESSAGES, gson.toJson(trimmed)).apply()
        }
    }
}
