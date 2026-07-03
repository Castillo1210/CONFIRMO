package com.example.tconfirmo.data.deposits

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.tconfirmo.data.DepositDraft
import com.example.tconfirmo.data.Report
import com.example.tconfirmo.data.ReportStatus
import com.example.tconfirmo.data.remote.ApiClient
import com.example.tconfirmo.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DepositRepository(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val depositApi: DepositApi = ApiClient.depositApi
) {
    suspend fun getBanks(): List<BancoResponseDto> {
        val response = runCatching { depositApi.getBanks() }.getOrNull()
            ?: return emptyList()

        return if (response.isSuccessful) {
            response.body().orEmpty()
        } else {
            emptyList()
        }
    }

    suspend fun getReports(pageSize: Int = 100): List<Report> {
        val response = runCatching { depositApi.getDeposits(page = 1, pageSize = pageSize) }.getOrNull()
            ?: return emptyList()

        if (!response.isSuccessful) return emptyList()

        val body = response.body() ?: return emptyList()
        val reports = mutableListOf<Report>()
        body.items.forEachIndexed { index, item ->
            val detail = getDepositDetail(item.id)
            reports += item.toReport(
                solicitudNum = "#${(index + 1).toString().padStart(3, '0')}",
                imageUrl = detail?.voucherUrl,
                mensajeValidacion = detail?.motivoRechazo,
                empresa = detail?.empresaId.orEmpty(),
                banco = detail?.bancoId.orEmpty()
            )
        }
        return reports
    }

    private suspend fun getDepositDetail(id: String): DepositResponseDto? {
        val response = runCatching { depositApi.getDeposit(id) }.getOrNull()
            ?: return null
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun createDeposit(draft: DepositDraft): String? {
        // Business rule: mobile sends voucher bytes as Base64 only when creating a deposit.
        // Stored deposits must be read back through the URL returned by the API.
        val imageBase64 = runCatching { readUriAsBase64(draft.imageUri) }.getOrNull()
            ?: return null

        val response = runCatching {
            depositApi.createDeposit(
                cliente = draft.cliente.toNullableRequestBody(),
                empresaId = (draft.empresaId ?: sessionManager.getEmpresaId()).toNullableRequestBody(),
                bancoId = draft.bancoId.toNullableRequestBody(),
                imagenBase64 = imageBase64.toPlainRequestBody()
            )
        }.getOrNull() ?: return null

        return if (response.isSuccessful) {
            response.body()?.depositId
        } else {
            null
        }
    }

    private suspend fun readUriAsBase64(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val bytes = when {
            uri.scheme.isNullOrBlank() -> File(uriString).readBytes()
            uri.scheme == "file" -> File(uri.path.orEmpty()).readBytes()
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No se pudo leer el voucher.")
        }
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun String?.toNullableRequestBody(): RequestBody? {
        return takeUnless { it.isNullOrBlank() }?.toPlainRequestBody()
    }

    private fun String.toPlainRequestBody(): RequestBody {
        return toRequestBody("text/plain".toMediaType())
    }

    private fun DepositListResponseDto.toReport(
        solicitudNum: String,
        imageUrl: String?,
        mensajeValidacion: String?,
        empresa: String,
        banco: String
    ): Report {
        val dateTime = fechaRegistro.toBackendDate()
        return Report(
            id = id,
            solicitudNum = solicitudNum,
            empresa = empresa.ifBlank { "Empresa" },
            cliente = cliente.orEmpty(),
            banco = banco.ifBlank { "Banco" },
            fecha = dateTime?.let { DISPLAY_DATE_FORMAT.format(it) } ?: "",
            hora = dateTime?.let { DISPLAY_TIME_FORMAT.format(it) } ?: "",
            status = estado.toReportStatus(),
            importe = if (monto > 0.0) "$moneda ${monto.formatAmount()}" else null,
            operacion = numeroOperacionBanco ?: numeroOperacion,
            imageUrl = imageUrl,
            voucherName = "Voucher_${solicitudNum.replace("#", "")}.jpg",
            mensajeValidacion = mensajeValidacion
        )
    }

    private fun String.toReportStatus(): ReportStatus {
        return when (trim().lowercase(Locale.ROOT)) {
            "confirmado", "validado", "procesado" -> ReportStatus.VALIDATED
            "rechazado" -> ReportStatus.REJECTED
            else -> ReportStatus.PENDING
        }
    }

    private fun String.toBackendDate(): Date? {
        return BACKEND_DATE_FORMATS.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(this)
            }.getOrNull()
        }
    }

    private fun Double.formatAmount(): String {
        return String.format(Locale.US, "%.2f", this)
    }

    companion object {
        private val DISPLAY_DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private val DISPLAY_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val BACKEND_DATE_FORMATS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
    }
}
