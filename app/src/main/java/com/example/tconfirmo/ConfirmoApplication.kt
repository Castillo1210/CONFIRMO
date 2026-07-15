package com.example.tconfirmo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ConfirmoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debe instalarse lo antes posible, antes de cualquier otra
        // inicializacion, para capturar tambien crashes tempranos.
        CrashHandler.install(this)
        createDepositsNotificationChannel()
    }

    private fun createDepositsNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            "deposits_channel",
            "Depósitos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones sobre el estado de tus depósitos"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
