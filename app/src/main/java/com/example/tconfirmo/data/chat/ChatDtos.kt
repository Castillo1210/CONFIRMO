package com.example.tconfirmo.data.chat

data class ChatMessageResponseDto(
    val id: String,
    val senderType: String,
    val senderId: String?,
    val content: String,
    val messageType: String,
    val metadata: Any?,
    val createdAt: String
)

data class SendDirectMessageRequestDto(
    val userId: String,
    val message: String,
    val depositId: String? = null
)

data class ChatHistoryResponseDto(
    val messages: List<ChatMessageResponseDto>,
    val hasMore: Boolean
)

data class SendUserMessageRequestDto(
    val content: String,
    val messageType: String = "text"
)

data class UploadChatImageRequestDto(
    val imagenBase64: String
)
