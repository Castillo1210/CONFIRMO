package com.example.tconfirmo.data.fcm

import com.example.tconfirmo.data.auth.AuthRepository
import com.example.tconfirmo.data.remote.ApiClient
import com.example.tconfirmo.data.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConfirmoFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        FcmTokenStore(applicationContext).saveToken(token)

        val sessionManager = SessionManager(applicationContext)
        if (!sessionManager.isLoggedIn() || sessionManager.isTestMode()) return

        ApiClient.initialize(sessionManager)
        serviceScope.launch {
            AuthRepository(
                authApi = ApiClient.authApi,
                sessionManager = sessionManager
            ).updateFcmToken(token)
        }
    }
}
