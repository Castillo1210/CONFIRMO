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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
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

    suspend fun getCompanies(): List<EmpresaResponseDto> {
        val response = runCatching { depositApi.getCompanies() }.getOrNull()
            ?: return emptyList()

        return if (response.isSuccessful) {
            response.body().orEmpty()
        } else {
            emptyList()
        }
    }

    suspend fun getReports(daysBack: Int = 0, pageSize: Int = 100): List<Report> {
        val (desde, hasta) = reportRange(daysBack)
        val companiesById = getCompanies().associateBy { it.id }
        val banksById = getBanks().associateBy { it.id }
        val response = runCatching {
            depositApi.getDeposits(
                desde = desde,
                hasta = hasta,
                page = 1,
                pageSize = pageSize
            )
        }.getOrNull()
            ?: return emptyList()

        if (!response.isSuccessful) return emptyList()

        val body = response.body() ?: return emptyList()
        val reports = mutableListOf<Report>()
        body.items.forEachIndexed { index, item ->
            val detail = getDepositDetail(item.id)
            reports += item.toReport(
                solicitudNum = "#${(index + 1).toString().padStart(3, '0')}",
                imageUrl = detail?.imagenUrl ?: detail?.imagenVoucher,
                mensajeValidacion = detail?.motivoRechazo,
                empresa = detail?.empresaId.resolveCompanyName(companiesById),
                banco = detail?.bancoId.resolveBankName(banksById),
                vendedor = sessionManager.getFullName().orEmpty()
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
        return when (val result = createDepositDetailed(draft)) {
            is DepositCreateResult.Success -> result.depositId
            is DepositCreateResult.Error -> null
        }
    }

    suspend fun createDepositDetailed(draft: DepositDraft): DepositCreateResult {
        // Business rule: mobile sends voucher bytes as Base64 only when creating a deposit.
        // Stored deposits must be read back through the URL returned by the API.
        val imageBase64 = runCatching { readUriAsBase64(draft.imageUri) }.getOrElse { error ->
            return DepositCreateResult.Error("No se pudo leer el voucher: ${error.message ?: "archivo no disponible"}")
        }

        val response = runCatching {
            depositApi.createDeposit(
                cliente = draft.cliente.toNullableRequestBody(),
                empresaId = (draft.empresaId ?: sessionManager.getEmpresaId()).toNullableRequestBody(),
                bancoId = draft.bancoId.toNullableRequestBody(),
                imagenBase64 = imageBase64.toPlainRequestBody()
            )
        }.getOrElse { error ->
            return DepositCreateResult.Error("No se pudo conectar con la API: ${error.message ?: "error de red"}")
        }

        return if (response.isSuccessful) {
            response.body()?.depositId?.let { DepositCreateResult.Success(it) }
                ?: DepositCreateResult.Error("La API respondio sin depositId.")
        } else {
            DepositCreateResult.Error(response.depositErrorMessage())
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

    private fun retrofit2.Response<DepositCreateResponseDto>.depositErrorMessage(): String {
        val rawError = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
        val apiMessage = runCatching {
            val json = JSONObject(rawError)
            json.optString("error")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("detail") }
        }.getOrNull().orEmpty()

        return if (apiMessage.isNotBlank()) {
            "API ${code()}: $apiMessage"
        } else {
            "API ${code()}: ${message().ifBlank { "No se pudo registrar el deposito." }}"
        }
    }

    private fun DepositListResponseDto.toReport(
        solicitudNum: String,
        imageUrl: String?,
        mensajeValidacion: String?,
        empresa: String,
        banco: String,
        vendedor: String
    ): Report {
        val dateTime = fechaRegistro.toBackendDate()
        return Report(
            id = id,
            solicitudNum = solicitudNum,
            empresa = empresa.ifBlank { "Empresa no disponible" },
            cliente = cliente.orEmpty(),
            banco = banco.ifBlank { "Banco no disponible" },
            fecha = dateTime?.let { DISPLAY_DATE_FORMAT.format(it) } ?: "",
            hora = dateTime?.let { DISPLAY_TIME_FORMAT.format(it) } ?: "",
            status = estado.toReportStatus(),
            importe = if (monto > 0.0) "$moneda ${monto.formatAmount()}" else null,
            operacion = numeroOperacionBanco ?: numeroOperacion,
            imageUrl = imageUrl,
            voucherName = "Voucher_${solicitudNum.replace("#", "")}.jpg",
            mensajeValidacion = mensajeValidacion,
            solicitadoPor = vendedor.ifBlank { null }
        )
    }

    private fun String?.resolveCompanyName(companiesById: Map<String, EmpresaResponseDto>): String {
        val id = this?.trim().orEmpty()
        if (id.isBlank()) return ""
        return companiesById[id]?.nombre.orEmpty()
    }

    private fun String?.resolveBankName(banksById: Map<String, BancoResponseDto>): String {
        val id = this?.trim().orEmpty()
        if (id.isBlank()) return ""
        return banksById[id]?.nombre.orEmpty()
    }

    private fun String.toReportStatus(): ReportStatus {
        return when (trim().lowercase(Locale.ROOT)) {
            "confirmado", "validado" -> ReportStatus.VALIDATED
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

    private fun reportRange(daysBack: Int): Pair<String, String> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysBack.coerceAtLeast(0))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return API_DATE_FORMAT.format(start.time) to API_DATE_FORMAT.format(end.time)
    }

    companion object {
        private val DISPLAY_DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private val DISPLAY_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val API_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
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

sealed class DepositCreateResult {
    data class Success(val depositId: String) : DepositCreateResult()
    data class Error(val message: String) : DepositCreateResult()
}
