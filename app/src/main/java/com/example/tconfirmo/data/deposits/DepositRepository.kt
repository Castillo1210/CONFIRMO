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

    // Solo pega contra GET /api/v1/deposits (una llamada, sin importar cuantos
    // items tenga la pagina). empresa/banco ya vienen embebidos en cada item
    // de la respuesta (ver DepositListResponseDto), asi que no hace falta ni
    // el detalle por item ni los catalogos de /masters/empresas|bancos solo
    // para poder pintar la lista.
    suspend fun getReports(daysBack: Int = 0, pageSize: Int = 100): List<Report> {
        val (desde, hasta) = reportRange(daysBack)
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
        val vendedor = sessionManager.getFullName().orEmpty()
        return body.items.mapIndexed { index, item ->
            item.toReport(
                solicitudNum = "#${(index + 1).toString().padStart(3, '0')}",
                vendedor = vendedor
            )
        }
    }

    // El endpoint de listado ya trae la referencia cruda del voucher
    // (imagenVoucher) y el reporte usa SignedVoucherImage para resolverla a
    // una URL firmada bajo demanda — no hace falta detalle para mostrar la
    // imagen. Lo que SI falta en el listado es el motivo de rechazo (no viene
    // a proposito, para no cargar texto largo en cada fila). Esto pide el
    // detalle puntual UNA sola vez, bajo demanda, solo cuando el usuario abre
    // un reporte especifico o lo va a regularizar — nunca en bucle por cada
    // item de la lista.
    suspend fun enrichWithDetail(report: Report): Report {
        val detail = getDepositDetail(report.id) ?: return report
        return report.copy(
            imageUrl = detail.imagenUrl ?: detail.imagenVoucher,
            mensajeValidacion = detail.motivoRechazo ?: report.mensajeValidacion
        )
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

    // Reabre un deposito RECHAZADO existente (PUT /{id}/regularize) en vez de
    // crear uno nuevo -- a diferencia de createDepositDetailed, el id ya
    // existe de antes (es el mismo deposito rechazado). El backend lo vuelve
    // a poner en Estado "recibido" y lo reencola para reprocesar.
    suspend fun regularizeDepositDetailed(depositId: String, draft: DepositDraft): DepositCreateResult {
        val imageBase64 = runCatching { readUriAsBase64(draft.imageUri) }.getOrElse { error ->
            return DepositCreateResult.Error("No se pudo leer el voucher: ${error.message ?: "archivo no disponible"}")
        }

        val response = runCatching {
            depositApi.regularizeDeposit(
                id = depositId,
                cliente = draft.cliente.toNullableRequestBody(),
                empresaId = (draft.empresaId ?: sessionManager.getEmpresaId()).toNullableRequestBody(),
                bancoId = draft.bancoId.toNullableRequestBody(),
                imagenBase64 = imageBase64.toPlainRequestBody()
            )
        }.getOrElse { error ->
            return DepositCreateResult.Error("No se pudo conectar con la API: ${error.message ?: "error de red"}")
        }

        return if (response.isSuccessful) {
            DepositCreateResult.Success(depositId)
        } else {
            DepositCreateResult.Error(response.depositErrorMessage())
        }
    }

    // FIX "No se pudo leer el voucher": al regularizar un rechazado, el
    // formulario se precarga con la URL FIRMADA de Google Cloud Storage del
    // voucher original (https://...), no con un archivo local -- eso viene
    // de enrichWithDetail() / DepositResponseDto.imagenUrl. ContentResolver
    // (rama "else" de abajo) solo sabe abrir esquemas content:///file://, asi
    // que una URL https:// caia ahi y explotaba con una excepcion generica.
    // Ahora se descarga por HTTP antes de convertir a Base64 -- la URL ya
    // viene firmada por el backend, no hace falta autenticacion adicional.
    private suspend fun readUriAsBase64(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val bytes = when {
            uri.scheme == "http" || uri.scheme == "https" -> java.net.URL(uriString).openStream().use { it.readBytes() }
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
        vendedor: String
    ): Report {
        val dateTime = fechaRegistro.toBackendDate()
        return Report(
            id = id,
            solicitudNum = solicitudNum,
            empresa = empresa?.nombre.orEmpty().ifBlank { "Empresa no disponible" },
            cliente = cliente.orEmpty(),
            banco = banco?.nombre.orEmpty().ifBlank { "Banco no disponible" },
            fecha = dateTime?.let { DISPLAY_DATE_FORMAT.format(it) } ?: "",
            hora = dateTime?.let { DISPLAY_TIME_FORMAT.format(it) } ?: "",
            status = estado.toReportStatus(),
            importe = if (monto > 0.0) "$moneda ${monto.formatAmount()}" else null,
            operacion = numeroOperacionBanco ?: numeroOperacion,
            // Referencia cruda (no firmada) del voucher: ya viene gratis en el
            // listado. SignedVoucherImage la resuelve a URL firmada bajo
            // demanda, solo para lo que realmente se muestra en pantalla
            // (reporte abierto o burbuja de chat visible).
            imageUrl = imagenVoucher,
            voucherName = "Voucher_${solicitudNum.replace("#", "")}.jpg",
            // El motivo de rechazo si se llena bajo demanda via
            // enrichWithDetail() cuando el usuario abre el detalle puntual.
            mensajeValidacion = null,
            solicitadoPor = vendedor.ifBlank { null }
        )
    }

    // IMPORTANTE: debe reconocer el mismo set de valores de "estado" que el
    // backend puede enviar, igual que la funcion equivalente usada para los
    // eventos de tiempo real (ver String?.toReportStatus() en MainScreen.kt).
    // Antes esta version solo reconocia "confirmado"/"validado"/"rechazado":
    // cualquier otro valor real del backend (p.ej. "CONFIRMADO_EXITOSO",
    // "QUALITY_REJECTED", "ERROR_VALIDACION") caia en el "else" y quedaba
    // como PENDING para siempre en la lista de Reportes, aunque el deposito
    // ya estuviera validado/rechazado. Eso hacia que exportReportsForExcel
    // (que solo exporta VALIDATED/REJECTED) no encontrara nunca filas y
    // generara un archivo vacio.
    private fun String.toReportStatus(): ReportStatus {
        return when (trim().uppercase(Locale.ROOT)) {
            "CONFIRMADO", "VALIDADO", "CONFIRMED", "VALIDATED", "CONFIRMADO_EXITOSO" -> ReportStatus.VALIDATED
            "RECHAZADO", "REJECTED", "QUALITY_REJECTED", "ERROR_VALIDACION" -> ReportStatus.REJECTED
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
