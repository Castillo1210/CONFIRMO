package com.example.tconfirmo.data.chat

import com.example.tconfirmo.data.ChatMessage
import com.example.tconfirmo.data.DepositDraft
import com.example.tconfirmo.data.MessageFrom
import com.example.tconfirmo.data.MessageStatus
import com.example.tconfirmo.data.Report
import com.example.tconfirmo.data.VoucherCard
import com.example.tconfirmo.data.remote.ApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatRepository(
    private val chatApi: ChatApi = ApiClient.chatApi
) {
    suspend fun getHistory(depositId: String, report: Report? = null, limit: Int = 50): List<ChatMessage> {
        val response = runCatching {
            chatApi.getChatHistory(depositId = depositId, limit = limit)
        }.getOrNull() ?: return emptyList()

        if (!response.isSuccessful) return emptyList()

        return response.body()
            ?.messages
            .orEmpty()
            .mapNotNull { it.toChatMessage(report) }
    }

    suspend fun getHistories(depositIds: List<String>, limit: Int = 50): List<ChatMessage> {
        return depositIds
            .distinct()
            .flatMap { depositId -> getHistory(depositId, limit = limit) }
            .distinctBy { it.id }
            .sortedWith(compareBy<ChatMessage> { it.date.toSortableDate() }.thenBy { it.time })
    }

    suspend fun getHistoriesForReports(reports: List<Report>, limit: Int = 50): List<ChatMessage> {
        val reportsById = reports.associateBy { it.id }
        return reports
            .map { it.id }
            .distinct()
            .flatMap { depositId -> getHistory(depositId, reportsById[depositId], limit) }
            .distinctBy { it.id }
            .sortedWith(compareBy<ChatMessage> { it.date.toSortableDate() }.thenBy { it.time })
    }

    suspend fun registerDepositMessage(
        depositId: String,
        solicitudNum: String,
        draft: DepositDraft
    ): Boolean {
        return runCatching {
            chatApi.sendMessage(
                depositId = depositId,
                request = SendUserMessageRequestDto(
                    content = "",
                    messageType = "image"
                )
            ).isSuccessful
        }.getOrDefault(false)
    }

    suspend fun sendTextMessage(
        depositId: String,
        content: String
    ): Boolean {
        val cleanContent = content.trim()
        if (depositId.isBlank() || cleanContent.isBlank()) return false

        return runCatching {
            chatApi.sendMessage(
                depositId = depositId,
                request = SendUserMessageRequestDto(
                    content = cleanContent,
                    messageType = "text"
                )
            ).isSuccessful
        }.getOrDefault(false)
    }

    private fun ChatMessageResponseDto.toChatMessage(report: Report? = null): ChatMessage? {
        val cleanMessageType = messageType.trim().lowercase(Locale.ROOT)
        val voucherCard = if (cleanMessageType == "image") report?.toVoucherCard() else null
        val replyToSolicitudId = if (cleanMessageType != "image") report?.solicitudNum else null
        val textContent = if (cleanMessageType == "text" || cleanMessageType == "direct" || cleanMessageType == "status_change") {
            content
        } else {
            null
        }
        if (cleanMessageType == "image" && voucherCard == null) return null
        if (cleanMessageType != "image" && textContent.isNullOrBlank()) return null

        return ChatMessage(
            id = id,
            from = senderType.toMessageFrom(),
            text = textContent,
            voucherCard = voucherCard,
            replyToSolicitudId = replyToSolicitudId,
            date = createdAt.toFormattedBackendDate("dd/MM/yyyy") ?: todayDate(),
            time = createdAt.toFormattedBackendDate("HH:mm") ?: currentTime(),
            status = MessageStatus.DELIVERED
        )
    }

    private fun Report.toVoucherCard(): VoucherCard {
        return VoucherCard(
            solicitudId = solicitudNum,
            voucherName = voucherName ?: "Voucher_${solicitudNum.replace("#", "")}.jpg",
            imageUrl = imageUrl.orEmpty(),
            empresa = empresa,
            banco = banco,
            cliente = cliente,
            status = status
        )
    }

    private fun String?.toMessageFrom(): MessageFrom {
        return when (this?.trim()?.lowercase(Locale.ROOT)) {
            "user", "mobile", "cliente", "client" -> MessageFrom.USER
            else -> MessageFrom.BOT
        }
    }

    private fun String?.toFormattedBackendDate(outputPattern: String): String? {
        if (isNullOrBlank()) return null
        val inputPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        val parsedDate = inputPatterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(this)
            }.getOrNull()
        } ?: return null
        return SimpleDateFormat(outputPattern, Locale.getDefault()).format(parsedDate)
    }

    private fun String.toSortableDate(): String {
        val parts = split("/")
        if (parts.size != 3) return this
        return "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
    }

    private fun todayDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun currentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}
