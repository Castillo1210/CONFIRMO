package com.example.tconfirmo.data

enum class ReportStatus {
    PENDING, VALIDATED, REJECTED
}

data class VoucherCard(
    val solicitudId: String,
    // Guid real del deposito (distinto de solicitudId, que es solo el
    // codigo mostrado "#001"). Se usa para armar la URL del endpoint de
    // imagen (GET /api/v1/deposits/{depositId}/image).
    //
    // NULLABLE A PROPOSITO, aunque conceptualmente siempre deberia existir:
    // los mensajes de chat cacheados localmente (ChatCache, via Gson) de
    // antes de que este campo existiera se deserializan sin pasar por el
    // constructor de Kotlin -- Gson usa reflection y deja este campo en null
    // igual, sin importar que el tipo diga que no puede serlo. Si se declara
    // no-nulo, esos mensajes viejos revientan la app con un
    // NullPointerException apenas se intentan pintar (esto es justo lo que
    // pasaba: crash al hacer scroll en el chat, en tarjetas de voucher ya
    // guardadas de antes de esta actualizacion).
    val depositId: String? = null,
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
    val imageUri: String,
    val empresaId: String? = null,
    val bancoId: String? = null,
    // Si no es null, este draft viene del boton "Regularizar" de un deposito
    // RECHAZADO -- al enviarlo hay que llamar a
    // DepositRepository.regularizeDepositDetailed(regularizeDepositId, draft)
    // en vez de crear un deposito nuevo (ver MainScreen.kt, onSubmit).
    val regularizeDepositId: String? = null
)

data class ChatMessage(
    val id: String,
    val from: MessageFrom,
    val text: String? = null,
    val imageUrl: String? = null,
    val voucherCard: VoucherCard? = null,
    val structuredData: StructuredBotData? = null, // Para mensajes de confirmación/rechazo
    // FUENTE DE VERDAD para vincular la respuesta del bot al voucher que la
    // origino: el GUID real y estable del deposito (mismo valor que
    // Report.id / VoucherCard.depositId). Todo matching/lookup de "a que
    // voucher responde este mensaje" debe hacerse contra este campo.
    val replyToDepositId: String? = null,
    // Etiqueta de presentacion (#001, #002...) capturada al momento de crear
    // el mensaje. Es posicional y se recalcula cada vez que se refresca la
    // lista de reports (ordenados por FechaRegistro DESC), asi que un mismo
    // numero puede terminar apuntando a otro deposito despues de un refresh.
    // NO usar para matching/lookup -- solo como texto de fallback en la UI
    // mientras replyToDepositId todavia no se pudo resolver contra "reports".
    val replyToSolicitudId: String? = null,
    val date: String = "",
    val time: String,
    val status: MessageStatus? = null,
    // Timestamp ISO crudo del backend (sin formatear), usado como cursor "before"
    // para pedir mensajes mas antiguos (paginacion del chat general vendedor).
    val createdAtRaw: String? = null,
    // Distingue el feed general vendedor<->finanzas del chat por deposito, para
    // poder paginar especificamente el primero (igual que el panel web).
    val source: ChatMessageSource = ChatMessageSource.DEPOSIT
)

enum class ChatMessageSource {
    DEPOSIT, VENDEDOR
}

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
