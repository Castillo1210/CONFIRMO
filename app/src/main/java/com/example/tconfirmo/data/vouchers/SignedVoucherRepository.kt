package com.example.tconfirmo.data.vouchers

import android.net.Uri
import com.example.tconfirmo.data.remote.ApiClient

class SignedVoucherRepository(
    private val api: SignedVoucherApi = ApiClient.signedVoucherApi
) {
    suspend fun getSignedUrl(voucherReference: String): SignedVoucherResult {
        val objectName = voucherReference.toVoucherObjectName()
        if (objectName.isBlank()) {
            return SignedVoucherResult.Error("Voucher no disponible.")
        }

        return runCatching {
            api.getSignedVoucherUrl(Uri.encode(objectName))
        }.fold(
            onSuccess = { response ->
                if (!response.isSuccessful) {
                    SignedVoucherResult.Error("No se pudo obtener la URL firmada. API ${response.code()}.")
                } else {
                    val signedUrl = response.body()?.signedUrl.orEmpty()
                    if (signedUrl.isBlank()) {
                        SignedVoucherResult.Error("La API no devolvio una URL firmada.")
                    } else {
                        SignedVoucherResult.Success(signedUrl)
                    }
                }
            },
            onFailure = { error ->
                SignedVoucherResult.Error("Error consultando voucher: ${error.message ?: "sin detalle"}")
            }
        )
    }

    private fun String.toVoucherObjectName(): String {
        val value = trim()
        if (value.isBlank()) return ""

        return when {
            value.startsWith("gs://", ignoreCase = true) -> {
                value.removePrefix("gs://").substringAfter('/', missingDelimiterValue = "")
            }
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> {
                Uri.parse(value).encodedPath.orEmpty().trimStart('/')
                    .substringAfter("confirmo-vouchers-dev/", missingDelimiterValue = Uri.parse(value).lastPathSegment.orEmpty())
            }
            else -> value.trimStart('/')
        }
    }
}

sealed class SignedVoucherResult {
    data class Success(val signedUrl: String) : SignedVoucherResult()
    data class Error(val message: String) : SignedVoucherResult()
}
