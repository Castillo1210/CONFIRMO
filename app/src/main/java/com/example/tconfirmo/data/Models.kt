package com.example.tconfirmo.data

enum class ReportStatus {
    PENDING, VALIDATED, REJECTED
}

data class VoucherCard(
    val solicitudId: String,
    val voucherName: String,
    val imageUrl: String,
    val empresa: String,
    val banco: String,
    val cliente: String,
    val status: ReportStatus
)

data class DepositDraft(
    val empresa: String,
    val banco: String,
    val cliente: String,
    val imageUri: String
)

data class ChatMessage(
    val id: String,
    val from: MessageFrom,
    val text: String? = null,
    val imageUrl: String? = null,
    val voucherCard: VoucherCard? = null,
    val structuredData: StructuredBotData? = null, // Para mensajes de confirmación/rechazo
    val time: String,
    val status: MessageStatus? = null
)

enum class MessageStatus {
    SENT, DELIVERED, READ
}

data class StructuredBotData(
    val type: BotMessageType,
    val title: String,
    val rows: List<Pair<String, String>>,
    val footer: String
)

enum class BotMessageType {
    CONFIRMATION, REJECTION
}

enum class MessageFrom {
    USER, BOT
}

data class Report(
    val id: String,
    val solicitudNum: String,
    val empresa: String,
    val cliente: String,
    val banco: String,
    val fecha: String,
    val hora: String,
    val status: ReportStatus,
    val importe: String? = null,
    val operacion: String? = null,
    val sucursal: String? = null,
    val anexo: String? = null,
    val voucherName: String? = null,
    val solicitadoPor: String? = null,
    val imageUrl: String? = null,
    val mensajeValidacion: String? = null,
    val historial: List<HistoryItem> = emptyList()
)

data class HistoryItem(
    val fecha: String,
    val evento: String
)
