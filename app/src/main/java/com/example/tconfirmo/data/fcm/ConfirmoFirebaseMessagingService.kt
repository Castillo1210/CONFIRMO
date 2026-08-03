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

        // FIX: el backend enviaba notificaciones "mixtas" (Notification + Data),
        // lo que hacia que a veces se mostrara doble -- una auto-desplegada por
        // el sistema desde el bloque Notification, y otra construida a mano aca
        // mismo desde el mismo mensaje (el comportamiento de cuando el SO la
        // auto-muestra o no es mas inconsistente de lo que asumia este comentario
        // original). El backend ahora manda el mensaje solo con Data (ver
        // FCMNotificationService.SendNotificationAsync), asi que este es el
        // UNICO lugar que muestra algo, siempre -- sin importar foreground o
        // background.
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Confirmo"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return

        // FIX: antes solo se usaba depositId para el id de la notificacion --
        // para un Aviso ese campo siempre es null (el backend manda avisoId),
        // asi que caia siempre al timestamp y nunca colapsaba con una anterior
        // del mismo aviso, quedando apiladas en vez de reemplazadas.
        val depositId = remoteMessage.data["depositId"]
        val avisoId = remoteMessage.data["avisoId"]
        val notificationId = depositId?.hashCode()
            ?: avisoId?.hashCode()
            ?: System.currentTimeMillis().toInt()

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
