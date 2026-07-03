package com.example.tconfirmo.data.fcm

import com.google.firebase.messaging.FirebaseMessagingService

class ConfirmoFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmTokenStore(applicationContext).saveToken(token)
    }
}
