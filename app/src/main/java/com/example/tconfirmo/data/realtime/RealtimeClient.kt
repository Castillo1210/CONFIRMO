package com.example.tconfirmo.data.realtime

import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.session.SessionManager
import com.google.gson.Gson
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /**
     * Lambda que intenta renovar el accessToken.
     * Retorna true si la renovación fue exitosa; false si la sesión expiró
     * completamente y el usuario debe re-autenticarse.
     */
    private val refreshToken: suspend () -> Boolean = { false },
    private val hubUrl: String = BuildConfig.SIGNALR_HUB_URL
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val gson = Gson()
    private var hubConnection: HubConnection? = null
    private var reconnectEnabled = false
    // Tracks the pending reconnect coroutine so it can be cancelled on explicit disconnect.
    private var reconnectJob: Job? = null

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    // Deduplicación de ChatMessageCreated: el backend puede disparar el mismo
    // evento por dos canales distintos (método directo "ChatMessageCreated" y
    // envoltorio genérico "ReceiveEvent"). Este mapa evita emitir el mismo
    // depósito más de una vez dentro de una ventana de 3 segundos.
    private val recentChatEventsByDeposit = mutableMapOf<String, Long>()
    private val CHAT_DEDUP_WINDOW_MS = 3_000L

    private fun tryEmitChatMessageCreated(event: RealtimeEvent.ChatMessageCreated) {
        val depositId = event.depositId
        if (depositId == null) {
            // Sin depositId no podemos deduplicar; lo emitimos siempre.
            _events.tryEmit(event)
            return
        }
        val now = System.currentTimeMillis()
        val lastSeen = recentChatEventsByDeposit[depositId]
        if (lastSeen != null && (now - lastSeen) < CHAT_DEDUP_WINDOW_MS) {
            // Ya se emitió este depósito dentro de la ventana → descartar duplicado.
            return
        }
        recentChatEventsByDeposit[depositId] = now
        // Limpiar entradas antiguas para no crecer indefinidamente.
        recentChatEventsByDeposit.entries.removeAll { now - it.value > CHAT_DEDUP_WINDOW_MS }
        _events.tryEmit(event)
    }

    suspend fun connect() {
        mutex.withLock {
            val token = sessionManager.getAccessToken()
            if (token.isNullOrBlank()) return
            if (token == "mock-access-token") return

            // Cancel any pending reconnect so it doesn't race with this explicit connect.
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectEnabled = true

            // If a stale connection exists (e.g. after logout/login), tear it down first
            // so we always build a fresh connection that picks up the new token.
            hubConnection?.let { stale ->
                runCatching {
                    withContext(Dispatchers.IO) { stale.stop().blockingAwait() }
                }
                hubConnection = null
            }

            val connection = HubConnectionBuilder
                .create(hubUrl)
                // Single.defer ensures the token is read from SharedPreferences
                // on every negotiation, never capturing a stale value at build time.
                .withAccessTokenProvider(Single.defer {
                    Single.just(sessionManager.getAccessToken().orEmpty())
                })
                .build()

            registerHandlers(connection)
            hubConnection = connection

            try {
                withContext(Dispatchers.IO) { connection.start().blockingAwait() }
                _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = true))
            } catch (error: Exception) {
                hubConnection = null
                val is401 = error.message?.contains("401") == true
                    || error.message?.contains("Unauthorized", ignoreCase = true) == true
                if (is401) {
                    // El token expiró mientras SignalR intentaba el negotiate.
                    // Intentar renovar y reconectar automáticamente.
                    val refreshed = refreshToken()
                    if (refreshed) {
                        _events.tryEmit(
                            RealtimeEvent.ConnectionChanged(
                                connected = false,
                                reason = "Token renovado, reconectando..."
                            )
                        )
                        scheduleReconnect(delayMs = 500L)
                    } else {
                        // El refreshToken también expiró — detener reconexiones.
                        reconnectEnabled = false
                        _events.tryEmit(
                            RealtimeEvent.ConnectionChanged(
                                connected = false,
                                reason = "Sesion expirada. Inicia sesion nuevamente."
                            )
                        )
                    }
                } else {
                    _events.tryEmit(
                        RealtimeEvent.ConnectionChanged(connected = false, reason = error.message)
                    )
                    scheduleReconnect()
                }
            }
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            // Disable auto-reconnect BEFORE stopping; the onClosed callback
            // will see reconnectEnabled == false and skip scheduleReconnect().
            reconnectEnabled = false
            // Cancel any in-flight reconnect coroutine immediately.
            reconnectJob?.cancel()
            reconnectJob = null
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
                tryEmitChatMessageCreated(
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
                tryEmitChatMessageCreated(
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
            // Snapshot reconnectEnabled before any state change so we don't
            // race with a concurrent disconnect() call that sets it to false.
            val shouldReconnect = reconnectEnabled
            hubConnection = null
            _events.tryEmit(RealtimeEvent.ConnectionChanged(connected = false, reason = error?.message))
            if (shouldReconnect) scheduleReconnect()
        }
    }

    private fun emitEnvelope(payload: String) {
        val envelope = runCatching { gson.fromJson(payload, RealtimeEnvelopeDto::class.java) }.getOrNull()
        when (envelope?.type) {
            "chat.message.created" -> tryEmitChatMessageCreated(
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

    private fun scheduleReconnect(delayMs: Long = RECONNECT_DELAY_MS) {
        if (!reconnectEnabled) return
        // Store the Job so disconnect() can cancel it before it fires.
        reconnectJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            reconnectJob = null
            connect()
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
