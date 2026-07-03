package com.example.tconfirmo.data.deposits

import com.google.gson.annotations.SerializedName

// Create/update payloads send the voucher content as Base64.
data class DepositCreateRequestDto(
    val cliente: String?,
    val empresaId: String?,
    val bancoId: String?,
    val imagenBase64: String
)

data class DepositCreateResponseDto(
    val depositId: String
)

data class DepositResponseDto(
    val id: String,
    val numeroOperacion: String,
    val cliente: String?,
    val monto: Double,
    val moneda: String,
    val fechaRegistro: String,
    // Read payloads expose a URL/path to the stored voucher, never Base64.
    @SerializedName("imagenVoucher")
    val voucherUrl: String?,
    val anexo: String?,
    val numeroOperacionBanco: String?,
    val fechaDeposito: String?,
    val estado: String,
    val observaciones: String?,
    val motivoRechazo: String?,
    val fechaValidacion: String?,
    val empresaId: String?,
    val bancoId: String?,
    val sucursalId: String?,
    val vendedorId: String,
    val referenciaCliente: String?,
    val datosOcr: Any?,
    val rucCliente: String?
)

data class DepositListResponseDto(
    val id: String,
    val numeroOperacion: String,
    val cliente: String?,
    val monto: Double,
    val moneda: String,
    val fechaRegistro: String,
    val estado: String,
    val numeroOperacionBanco: String?,
    val fechaDeposito: String?
)

data class DepositListPagedResponseDto(
    val items: List<DepositListResponseDto>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

data class BancoResponseDto(
    val id: String,
    val nombre: String,
    val codigo: String?
)
