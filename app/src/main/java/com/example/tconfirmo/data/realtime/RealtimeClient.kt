package com.example.tconfirmo.data.realtime

import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.session.SessionManager
import com.google.gson.Gson
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RealtimeClient(
    private val sessionManager: SessionManager,
    private val hubUrl: String = BuildConfig.SIGNALR_HUB_URL
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val gson = Gson()
    private var hubConnection: HubConnection? = null
    private var reconnectEnabled = false

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    suspend fun connect() {
        mutex.withLock {
            val token = sessionManager.getAccessToken()
            if (token.isNullOrBlank()) return
            if (token == "mock-access-token") return

            reconnectEnabled = true
            if (hubConnection != null) return

            val connection = HubConnectionBuilder
                .create(hubUrl)
                .withAccessTokenProvider(Single.defer {
                    Single.just(sessionManager.getAccessToken().orEmpty())
                })
                .build()

            registerHandlers(connection)
            hubConnection = connection

            runCatching {
                withContext(Dispatchers.IO) { connection.start().blockingAwait() }
            }.onSuccess {
                _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = true))
            }.onFailure { error ->
                hubConnection = null
                _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = false, reason = error.message))
                scheduleReconnect()
            }
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            reconnectEnabled = false
            val connection = hubConnection
            hubConnection = null
            runCatching {
                withContext(Dispatchers.IO) { connection?.stop()?.blockingAwait() }
            }
            _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = false))
        }
    }

    suspend fun registerFcmToken(fcmToken: String) {
        if (fcmToken.isBlank()) return
        val connection = hubConnection ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                connection.send("RegisterFcmToken", fcmToken)
            }
        }
    }

    private fun registerHandlers(connection: HubConnection) {
        connection.on(
            "ReceiveEvent",
            { payload: String -> emitEnvelope(payload) },
            String::class.java
        )
        connection.on(
            "ChatMessageCreated",
            { event: RealtimeChatMessageCreatedDto ->
                _events.tryEmit(
                    RealtimeEvent.ChatMessageCreated(
                        depositId = event.depositId,
                        message = event.message,
                        messageId = event.messageId,
                        createdAt = event.createdAt
                    )
                )
            },
            RealtimeChatMessageCreatedDto::class.java
        )
        connection.on(
            "DepositStatusChanged",
            { event: RealtimeDepositStatusChangedDto ->
                _events.tryEmit(
                    RealtimeEvent.DepositStatusChanged(
                        depositId = event.depositId ?: event.deposit?.id,
                        status = event.status ?: event.deposit?.estado,
                        deposit = event.deposit,
                        updatedAt = event.updatedAt
                    )
                )
            },
            RealtimeDepositStatusChangedDto::class.java
        )
        connection.on(
            "DepositUpdated",
            { event: RealtimeDepositUpdatedDto ->
                _events.tryEmit(
                    RealtimeEvent.DepositUpdated(
                        depositId = event.depositId ?: event.deposit?.id,
                        deposit = event.deposit,
                        updatedAt = event.updatedAt
                    )
                )
            },
            RealtimeDepositUpdatedDto::class.java
        )
        connection.on(
            "ChatMessage",
            { message: RealtimeChatMessageDto ->
                _events.tryEmit(
                    RealtimeEvent.ChatMessageCreated(
                        depositId = message.depositId,
                        message = message,
                        messageId = message.id,
                        createdAt = message.createdAt
                    )
                )
            },
            RealtimeChatMessageDto::class.java
        )
        connection.on(
            "DirectMessage",
            { event: BridgeDirectMessageDto ->
                _events.tryEmit(
                    RealtimeEvent.ChatMessageCreated(
                        depositId = event.depositId,
                        message = RealtimeChatMessageDto(
                            depositId = event.depositId,
                            senderType = "finance",
                            content = event.message,
                            messageType = "direct",
                            createdAt = event.timestamp
                        ),
                        messageId = null,
                        createdAt = event.timestamp
                    )
                )
            },
            BridgeDirectMessageDto::class.java
        )
        connection.on(
            "DepositReceived",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(event, "recibido", "Deposito recibido")
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "DepositProcessing",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(event, "processing", event.message ?: "Deposito en procesamiento")
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "DepositProcessingUpdateStatusUpdate",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(event, event.status ?: "processing", event.message)
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "DepositConfirmed",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(
                    event = event,
                    fallbackStatus = event.notification?.estado ?: "confirmado",
                    fallbackMessage = "Deposito confirmado"
                )
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "DepositRejected",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(event, "rechazado", event.reason ?: "Deposito rechazado")
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "QualityRejected",
            { event: BridgeDepositNotificationDto ->
                emitDepositNotification(
                    event = event,
                    fallbackStatus = "rechazado",
                    fallbackMessage = event.issues?.joinToString(", ") ?: "Voucher rechazado por calidad"
                )
            },
            BridgeDepositNotificationDto::class.java
        )
        connection.on(
            "ValidationErrors",
            { event: BridgeValidationErrorsDto ->
                _events.tryEmit(
                    RealtimeEvent.DepositNotification(
                        depositId = event.depositId,
                        status = "rechazado",
                        message = event.errors?.firstOrNull()?.message ?: "Errores de validacion",
                        updatedAt = event.timestamp
                    )
                )
            },
            BridgeValidationErrorsDto::class.java
        )
        connection.on(
            "RequiresReview",
            { event: BridgeValidationErrorsDto ->
                _events.tryEmit(
                    RealtimeEvent.DepositNotification(
                        depositId = event.depositId,
                        status = "observado",
                        message = event.warnings?.firstOrNull()?.message ?: "Deposito requiere revision",
                        updatedAt = event.timestamp
                    )
                )
            },
            BridgeValidationErrorsDto::class.java
        )
        connection.onClosed { error ->
            hubConnection = null
            _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = false, reason = error?.message))
            scheduleReconnect()
        }
    }

    private fun emitEnvelope(payload: String) {
        val envelope = runCatching { gson.fromJson(payload, RealtimeEnvelopeDto::class.java) }.getOrNull()
        when (envelope?.type) {
            "chat.message.created" -> _events.tryEmit(
                RealtimeEvent.ChatMessageCreated(
                    depositId = envelope.depositId,
                    message = envelope.message,
                    messageId = envelope.messageId,
                    createdAt = envelope.createdAt
                )
            )
            "deposit.status.changed" -> _events.tryEmit(
                RealtimeEvent.DepositStatusChanged(
                    depositId = envelope.depositId ?: envelope.deposit?.id,
                    status = envelope.status ?: envelope.deposit?.estado,
                    deposit = envelope.deposit,
                    updatedAt = envelope.updatedAt
                )
            )
            "deposit.updated" -> _events.tryEmit(
                RealtimeEvent.DepositUpdated(
                    depositId = envelope.depositId ?: envelope.deposit?.id,
                    deposit = envelope.deposit,
                    updatedAt = envelope.updatedAt
                )
            )
            else -> _events.tryEmit(RealtimeEvent.Raw(type = envelope?.type, payload = payload))
        }
    }

    private fun emitDepositNotification(
        event: BridgeDepositNotificationDto,
        fallbackStatus: String,
        fallbackMessage: String?
    ) {
        val depositId = event.depositId ?: event.notification?.depositId
        _events.tryEmit(
            RealtimeEvent.DepositNotification(
                depositId = depositId,
                status = event.status ?: event.notification?.estado ?: fallbackStatus,
                message = event.message ?: event.reason ?: fallbackMessage,
                updatedAt = event.timestamp
            )
        )
    }

    private fun scheduleReconnect() {
        if (!reconnectEnabled) return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
