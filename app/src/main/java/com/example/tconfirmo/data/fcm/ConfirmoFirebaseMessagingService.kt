package com.example.tconfirmo.data.fcm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tconfirmo.R
import com.example.tconfirmo.data.auth.AuthRepository
import com.example.tconfirmo.data.remote.ApiClient
import com.example.tconfirmo.data.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConfirmoFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val DEPOSITS_CHANNEL_ID = "deposits_channel"
    }

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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // El backend envía notificaciones "mixtas" (Notification + Data) para que
        // Android las muestre solo automáticamente cuando la app está en background.
        // En foreground el sistema no muestra nada y llama a esta función con el
        // RemoteMessage completo: si no la construimos y mostramos a mano acá, se pierde.
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Confirmo"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return

        val depositId = remoteMessage.data["depositId"]
        val notificationId = depositId?.hashCode() ?: System.currentTimeMillis().toInt()

        showNotification(title, body, notificationId)
    }

    private fun showNotification(title: String, body: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(this, DEPOSITS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Red de seguridad adicional: si el permiso se revoca justo antes del notify,
            // evitamos que el proceso crashee.
        }
    }
}
