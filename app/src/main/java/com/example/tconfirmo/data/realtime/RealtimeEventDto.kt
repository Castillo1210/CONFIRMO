package com.example.tconfirmo.data.realtime

data class RealtimeEnvelopeDto(
    val type: String? = null,
    val depositId: String? = null,
    val messageId: String? = null,
    val status: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val message: RealtimeChatMessageDto? = null,
    val deposit: RealtimeDepositDto? = null
)

data class RealtimeChatMessageCreatedDto(
    val depositId: String? = null,
    val messageId: String? = null,
    val createdAt: String? = null,
    val message: RealtimeChatMessageDto? = null
)

data class RealtimeDepositStatusChangedDto(
    val depositId: String? = null,
    val status: String? = null,
    val updatedAt: String? = null,
    val deposit: RealtimeDepositDto? = null
)

data class RealtimeDepositUpdatedDto(
    val depositId: String? = null,
    val updatedAt: String? = null,
    val deposit: RealtimeDepositDto? = null
)

data class RealtimeChatMessageDto(
    val id: String? = null,
    val depositId: String? = null,
    val vendedorId: String? = null,
    val senderType: String? = null,
    val senderId: String? = null,
    val content: String? = null,
    val messageType: String? = null,
    val createdAt: String? = null
)

data class RealtimeDepositDto(
    val id: String? = null,
    val estado: String? = null,
    val motivoRechazo: String? = null,
    val observaciones: String? = null,
    val imagenVoucher: String? = null,
    val fechaValidacion: String? = null,
    val updatedAt: String? = null
)

data class BridgeDepositNotificationDto(
    val depositId: String? = null,
    val timestamp: String? = null,
    val progress: Int? = null,
    val reason: String? = null,
    val issues: List<String>? = null,
    val message: String? = null,
    val status: String? = null,
    val notification: BridgeDepositConfirmedNotificationDto? = null
)

data class BridgeDepositConfirmedNotificationDto(
    val depositId: String? = null,
    val estado: String? = null,
    val referenceNumber: String? = null,
    val empresa: String? = null,
    val sucursal: String? = null,
    val banco: String? = null,
    val anexo: String? = null,
    val fechaDeposito: String? = null,
    val numeroOperacion: String? = null,
    val importe: String? = null,
    val moneda: String? = null
)

data class BridgeDirectMessageDto(
    val message: String? = null,
    val depositId: String? = null,
    val timestamp: String? = null
)

data class BridgeValidationErrorsDto(
    val depositId: String? = null,
    val errors: List<BridgeVoucherIssueDto>? = null,
    val warnings: List<BridgeVoucherIssueDto>? = null,
    val timestamp: String? = null
)

data class BridgeVoucherIssueDto(
    val errorCode: String? = null,
    val warningCode: String? = null,
    val fieldName: String? = null,
    val title: String? = null,
    val message: String? = null,
    val userAction: String? = null,
    val severity: String? = null
)

sealed interface RealtimeEvent {
    data class ChatMessageCreated(
        val depositId: String?,
        val message: RealtimeChatMessageDto?,
        val messageId: String?,
        val createdAt: String?,
        val vendedorId: String? = null
    ) : RealtimeEvent

    data class DepositStatusChanged(
        val depositId: String?,
        val status: String?,
        val deposit: RealtimeDepositDto?,
        val updatedAt: String?
    ) : RealtimeEvent

    data class DepositUpdated(
        val depositId: String?,
        val deposit: RealtimeDepositDto?,
        val updatedAt: String?
    ) : RealtimeEvent

    data class DepositNotification(
        val depositId: String?,
        val status: String?,
        val message: String?,
        val updatedAt: String?
    ) : RealtimeEvent

    data class Raw(
        val type: String?,
        val payload: String
    ) : RealtimeEvent

    data class ConnectionChanged(
        val connected: Boolean,
        val reason: String? = null
    ) : RealtimeEvent
}
