package com.example.tconfirmo.data.offline

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirebaseOfflineClient {
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun initialize(context: Context) {
        FirebaseApp.initializeApp(context)
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }
}
