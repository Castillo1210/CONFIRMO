package com.example.tconfirmo.data.offline

import com.example.tconfirmo.data.Report
import com.google.firebase.Timestamp

class OfflineDepositRepository(
    private val firestoreClient: FirebaseOfflineClient = FirebaseOfflineClient
) {
    fun savePendingReports(reports: List<Report>) {
        if (reports.isEmpty()) return

        val batch = firestoreClient.firestore.batch()
        reports.forEach { report ->
            val ref = firestoreClient.firestore
                .collection(COLLECTION_PENDING_DEPOSITS)
                .document(report.id)

            batch.set(ref, report.toOfflineMap())
        }
        batch.commit()
    }

    private fun Report.toOfflineMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "solicitudNum" to solicitudNum,
            "empresa" to empresa,
            "cliente" to cliente,
            "banco" to banco,
            "fecha" to fecha,
            "hora" to hora,
            "status" to status.name,
            "imageUrl" to imageUrl,
            "voucherName" to voucherName,
            "syncStatus" to "PENDING_SYNC",
            "createdAt" to Timestamp.now()
        )
    }

    private companion object {
        const val COLLECTION_PENDING_DEPOSITS = "pending_deposits"
    }
}
