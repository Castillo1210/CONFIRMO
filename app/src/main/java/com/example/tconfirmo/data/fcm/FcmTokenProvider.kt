package com.example.tconfirmo.data.fcm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FcmTokenProvider(context: Context) {
    private val tokenStore = FcmTokenStore(context)

    suspend fun getCurrentToken(): String? {
        val token = suspendCancellableCoroutine<String?> { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }

        if (!token.isNullOrBlank()) {
            tokenStore.saveToken(token)
        }
        return token ?: tokenStore.getToken()
    }
}
