package com.example.tconfirmo.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.sp
import com.example.tconfirmo.R
import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.*
import com.example.tconfirmo.data.auth.AuthRepository
import com.example.tconfirmo.data.auth.AuthResult
import com.example.tconfirmo.data.chat.ChatCache
import com.example.tconfirmo.data.chat.ChatRepository
import com.example.tconfirmo.data.deposits.DepositCreateResult
import com.example.tconfirmo.data.deposits.DepositRepository
import com.example.tconfirmo.data.realtime.RealtimeClient
import com.example.tconfirmo.data.realtime.RealtimeEvent
import com.example.tconfirmo.data.remote.ApiClient
import com.example.tconfirmo.data.session.SessionManager
import com.example.tconfirmo.ui.components.MessageBubble
import com.example.tconfirmo.ui.components.PdfPreview
import com.example.tconfirmo.ui.components.RegisterSheet
import com.example.tconfirmo.ui.components.SignedVoucherImage
import com.example.tconfirmo.ui.theme.AccentGreen
import com.example.tconfirmo.ui.theme.PrimaryDarkGreen
import com.example.tconfirmo.ui.theme.PrimaryGreen
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.launch

import androidx.compose.ui.tooling.preview.Preview
import com.example.tconfirmo.ui.theme.TConfirmoTheme
import com.example.tconfirmo.ui.theme.PlusJakartaSansFamily

private const val REGISTER_SESSION_PREFS = "tconfirmo_register_session"
private const val KEY_PENDING_SHARED_VOUCHERS = "pending_shared_vouchers"
private const val KEY_REPORT_DAYS_BACK = "report_days_back"

@Composable
fun MainScreen(
    initialSelectedTab: Int = 0,
    realtimeClient: RealtimeClient? = null,
    resumeSignal: Int = 0,
    sharedVoucherUris: List<Uri> = emptyList(),
    onSharedVouchersConsumed: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val registerScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val depositRepository = remember { DepositRepository(context.applicationContext, sessionManager) }
    val chatRepository = remember { ChatRepository() }
    val chatCache = remember { ChatCache(context) }
    // Id del vendedor logueado (el propio usuario de la app): identifica el
    // canal general de chat con finanzas (api/v1/chat/vendedores/{vendedorId}),
    // independiente de cualquier deposito puntual.
    val vendedorId = remember { sessionManager.getUserId().orEmpty() }
    var selectedTab by rememberSaveable { mutableStateOf(initialSelectedTab) }
    var chatWallpaper by remember { mutableStateOf(loadChatWallpaper(context)) }
    var showRegisterSheet by remember { mutableStateOf(false) }
    var registerSharedVoucherUriStrings by rememberSaveable {
        mutableStateOf(loadPendingSharedVoucherUriStrings(context))
    }
    var registerInitialDrafts by remember { mutableStateOf<List<DepositDraft>>(emptyList()) }
    var registerResetKey by remember { mutableStateOf(0) }
    // FIX: guarda contra doble envio real del mismo lote de vouchers (ver
    // bloque onSubmit mas abajo). Sin esto, si el LaunchedEffect de
    // RegisterSheet que dispara onSubmit se re-ejecuta por cualquier motivo
    // (recomposicion, reset de estado, etc.) se crean depositos duplicados
    // en el backend con datos identicos.
    var isSubmittingDeposit by remember { mutableStateOf(false) }
    var reportDaysBack by rememberSaveable { mutableStateOf(loadSavedReportDaysBack(context)) }
    var isLoadingReports by remember { mutableStateOf(false) }
    var reportLoadingMessage by remember { mutableStateOf("") }
    // Se inicializa con lo ultimo guardado en disco para que el chat muestre
    // algo de inmediato al reabrir la app, en vez de una pantalla vacia
    // mientras se espera la respuesta de api-bridge (igual que WhatsApp/Telegram
    // muestran el historial local antes de sincronizar).
    var messages by remember { mutableStateOf(chatCache.load()) }
    var reports by remember { mutableStateOf(emptyList<Report>()) }
    var reportsBackPressCount by remember { mutableStateOf(0) }
    // Paginacion del feed general vendedor<->finanzas ("Ver mensajes anteriores").
    var vendedorChatHasMore by remember { mutableStateOf(true) }
    var isLoadingOlderMessages by remember { mutableStateOf(false) }

    suspend fun refreshReportsFromApi(daysBack: Int = reportDaysBack) {
        if (sessionManager.isTestMode()) return
        isLoadingReports = true
        reportLoadingMessage = reportSearchMessage(daysBack)
        val remoteReports = depositRepository.getReports(daysBack = daysBack)
        reports = remoteReports
        val depositHistoryMessages = chatRepository.getHistoriesForReports(remoteReports)
        // Feed general de finanzas <-> vendedor (mensajes sueltos, sin deposito
        // asociado). Se mezcla con el historial por deposito en el mismo feed
        // cronologico unico de ChatTab.
        val vendedorHistoryMessages = if (vendedorId.isNotBlank()) {
            chatRepository.getVendedorHistory(vendedorId)
        } else {
            emptyList()
        }
        val historyMessages = depositHistoryMessages + vendedorHistoryMessages
        if (historyMessages.isNotEmpty()) {
            messages = mergeChatMessages(messages, historyMessages)
            chatCache.save(messages)
        }
        isLoadingReports = false
    }

    // "Ver mensajes anteriores" agotando el historial ya cargado: pide una
    // pagina mas vieja al backend usando como cursor el createdAt del mensaje
    // vendedor mas antiguo que ya se tiene, igual contrato que usa el panel web
    // (before/limit/hasMore).
    suspend fun loadOlderVendedorMessages() {
        if (isLoadingOlderMessages || !vendedorChatHasMore || vendedorId.isBlank()) return
        isLoadingOlderMessages = true
        val oldestVendedorCursor = messages
            .filter { it.source == ChatMessageSource.VENDEDOR && !it.createdAtRaw.isNullOrBlank() }
            .minByOrNull { it.createdAtRaw!! }
            ?.createdAtRaw
        val (olderMessages, hasMore) = chatRepository.getVendedorHistoryPage(
            vendedorId = vendedorId,
            before = oldestVendedorCursor
        )
        vendedorChatHasMore = hasMore
        if (olderMessages.isNotEmpty()) {
            messages = mergeChatMessages(messages, olderMessages)
            chatCache.save(messages)
        }
        isLoadingOlderMessages = false
    }

    // FIX: la tarjeta de voucher que ya se pinto en el chat quedaba "congelada"
    // con el estado (Pendiente/Validado/Rechazado) que tenia al momento de
    // mostrarse — los eventos de tiempo real (DepositStatusChanged, etc.) solo
    // actualizaban "reports", nunca los ChatMessage ya renderizados. Esto
    // propaga el nuevo estado tambien a la tarjeta del chat, buscandola por
    // depositId (GUID estable, unico por deposito).
    //
    // FIX citas cruzadas: antes matcheaba por solicitudId (numero posicional
    // "#001" que se recalcula en cada refresh de "reports"), lo que podia
    // actualizar la tarjeta equivocada despues de un refresh. Ahora matchea
    // por depositId, que nunca cambia para un mismo deposito.
    fun updateVoucherCardStatus(depositId: String?, newStatus: ReportStatus) {
        if (depositId == null) return
        messages = messages.map { message ->
            val card = message.voucherCard
            if (card != null && card.depositId == depositId && card.status != newStatus) {
                message.copy(voucherCard = card.copy(status = newStatus))
            } else {
                message
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshReportsFromApi()
    }

    // BUG "primer mensaje perdido": MainActivity incrementa resumeSignal cada
    // vez que la app vuelve a primer plano (onResume, salvo el primero, que ya
    // cubre el LaunchedEffect(Unit) de arriba). Sin esto, el chat vendedor solo
    // se sincronizaba una vez por proceso -- si el primer mensaje de una
    // conversacion nueva llegaba con el socket de tiempo real caido (app en
    // background, no cerrada), quedaba persistido en el backend pero invisible
    // hasta un cold start real. refreshReportsFromApi() ya trae el historial
    // completo (getVendedorHistory sin cursor "since"), asi que alcanza con
    // volver a llamarla en cada resume real.
    LaunchedEffect(resumeSignal) {
        if (resumeSignal > 0) {
            refreshReportsFromApi()
        }
    }

    LaunchedEffect(realtimeClient) {
        realtimeClient?.events?.collect { event ->
            when (event) {
                is RealtimeEvent.ChatMessageCreated -> {
                    val realtimeMessage = event.message
                    val content = realtimeMessage?.content.orEmpty()
                    val messageId = realtimeMessage?.id ?: event.messageId ?: UUID.randomUUID().toString()
                    val messageType = realtimeMessage?.messageType.orEmpty().lowercase(Locale.ROOT)
                    val depositId = event.depositId ?: realtimeMessage?.depositId
                    val eventVendedorId = event.vendedorId ?: realtimeMessage?.vendedorId
                    // Mensaje suelto del canal general finanzas <-> vendedor
                    // (tabla vendedor_messages): no tiene depositId, asi que no
                    // se intenta resolver voucherCard ni replyToSolicitudId, se
                    // agrega directo al feed unico del chat.
                    if (depositId.isNullOrBlank() && !eventVendedorId.isNullOrBlank()) {
                        if (content.isNotBlank() && messages.none { it.id == messageId }) {
                            val createdAt = realtimeMessage?.createdAt ?: event.createdAt
                            // Este mismo mensaje puede haber sido pintado ya de forma
                            // optimista (ver onSendMessage) con un id temporal
                            // "pending-...". Si es asi, se reemplaza ese registro con el
                            // id/timestamp reales en vez de agregar uno nuevo — evita el
                            // mensaje duplicado que se veia al enviar desde este mismo
                            // dispositivo.
                            val pendingMatch = messages.firstOrNull {
                                it.id.startsWith("pending-") && it.from == MessageFrom.USER && it.text == content
                            }
                            messages = if (pendingMatch != null) {
                                messages.map { msg ->
                                    if (msg.id == pendingMatch.id) {
                                        msg.copy(
                                            id = messageId,
                                            date = createdAt.toChatDate(),
                                            time = createdAt.toChatTime(),
                                            createdAtRaw = createdAt,
                                            source = ChatMessageSource.VENDEDOR,
                                            status = MessageStatus.DELIVERED
                                        )
                                    } else {
                                        msg
                                    }
                                }
                            } else {
                                messages + ChatMessage(
                                    id = messageId,
                                    from = realtimeMessage?.senderType.toMessageFrom(),
                                    text = content,
                                    voucherCard = null,
                                    replyToDepositId = null,
                                    replyToSolicitudId = null,
                                    date = createdAt.toChatDate(),
                                    time = createdAt.toChatTime(),
                                    status = MessageStatus.DELIVERED,
                                    createdAtRaw = createdAt,
                                    source = ChatMessageSource.VENDEDOR
                                )
                            }
                        }
                        return@collect
                    }
                    val voucherCard = if (messageType == "image") {
                        reports.firstOrNull { it.id == depositId }?.toVoucherCard()
                    } else {
                        null
                    }
                    if (messageType == "image" && voucherCard == null) {
                        refreshReportsFromApi()
                        return@collect
                    }
                    val shouldShowMessage = content.isNotBlank() || voucherCard != null
                    // FIX duplicado: al subir un voucher se agrega una tarjeta "optimista"
                    // con un id generado en el cliente (ver bloque onSubmit mas abajo) y
                    // ADEMAS se registra el mensaje en el backend (chatRepository.
                    // registerDepositMessage), que dispara este mismo evento en tiempo real
                    // con el id real del servidor. Como los ids nunca coinciden, el chequeo
                    // "messages.none { it.id == messageId }" nunca detectaba el duplicado.
                    // Para mensajes de tipo "image" (tarjetas de voucher), el depositId
                    // (GUID) es unico por deposito, asi que se usa como llave real de
                    // deduplicacion en vez del id del mensaje. (Antes se usaba
                    // voucherCard.solicitudId, un numero posicional que no es una llave
                    // estable entre refreshes.)
                    val isDuplicateVoucherCard = voucherCard != null &&
                        messages.any { it.voucherCard?.depositId == voucherCard.depositId }
                    if (shouldShowMessage && messages.none { it.id == messageId } && !isDuplicateVoucherCard) {
                        val createdAt = realtimeMessage?.createdAt ?: event.createdAt
                        // FIX: vincula el mensaje del sistema/bot al voucher que le dio
                        // origen: solo los mensajes que NO son la propia tarjeta de imagen
                        // llevan replyToDepositId, que es directamente el depositId del
                        // evento (GUID estable) -- no se resuelve contra solicitudNum.
                        val replyToDepositId = if (messageType != "image") depositId else null
                        // Etiqueta de fallback para la UI (ver ChatRepository.toChatMessage).
                        val replyToSolicitudId = if (messageType != "image") {
                            reports.firstOrNull { it.id == depositId }?.solicitudNum
                        } else {
                            null
                        }
                        messages = messages + ChatMessage(
                            id = messageId,
                            from = realtimeMessage?.senderType.toMessageFrom(),
                            text = if (messageType == "image") null else content,
                            voucherCard = voucherCard,
                            replyToDepositId = replyToDepositId,
                            replyToSolicitudId = replyToSolicitudId,
                            date = createdAt.toChatDate(),
                            time = createdAt.toChatTime(),
                            status = MessageStatus.DELIVERED
                        )
                    }
                }
                is RealtimeEvent.DepositStatusChanged -> {
                    val depositId = event.depositId ?: return@collect
                    val status = (event.status ?: event.deposit?.estado).toReportStatus() ?: return@collect
                    reports = reports.map { report ->
                        if (report.id == depositId) {
                            report.copy(
                                status = status,
                                mensajeValidacion = event.deposit?.motivoRechazo ?: report.mensajeValidacion,
                                imageUrl = event.deposit?.imagenVoucher ?: report.imageUrl
                            )
                        } else {
                            report
                        }
                    }
                    updateVoucherCardStatus(depositId, status)
                    refreshReportsFromApi()
                }
                is RealtimeEvent.DepositUpdated -> {
                    val depositId = event.depositId ?: return@collect
                    val newStatus = event.deposit?.estado.toReportStatus()
                    reports = reports.map { report ->
                        if (report.id == depositId) {
                            report.copy(
                                status = newStatus ?: report.status,
                                mensajeValidacion = event.deposit?.motivoRechazo ?: report.mensajeValidacion,
                                imageUrl = event.deposit?.imagenVoucher ?: report.imageUrl
                            )
                        } else {
                            report
                        }
                    }
                    if (newStatus != null) updateVoucherCardStatus(depositId, newStatus)
                    refreshReportsFromApi()
                }
                is RealtimeEvent.DepositNotification -> {
                    val depositId = event.depositId ?: return@collect
                    val status = event.status.toReportStatus()
                    if (status != null) updateVoucherCardStatus(depositId, status)
                    reports = reports.map { report ->
                        if (report.id == depositId) {
                            report.copy(
                                status = status ?: report.status,
                                mensajeValidacion = event.message ?: report.mensajeValidacion
                            )
                        } else {
                            report
                        }
                    }
                    refreshReportsFromApi()
                }
                is RealtimeEvent.ConnectionChanged,
                is RealtimeEvent.Raw -> Unit
            }
        }
    }

    fun updatePendingSharedVouchers(values: List<String>) {
        registerSharedVoucherUriStrings = values
        savePendingSharedVoucherUriStrings(context, values)
    }

    LaunchedEffect(sharedVoucherUris) {
        if (sharedVoucherUris.isNotEmpty()) {
            updatePendingSharedVouchers(registerSharedVoucherUriStrings + sharedVoucherUris.map { uri ->
                copySharedVoucherToSessionCache(context, uri)
            })
            showRegisterSheet = true
            selectedTab = 0
            onSharedVouchersConsumed()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) {
            reportsBackPressCount = 0
        }
    }

    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
            reportsBackPressCount = 0
            return@BackHandler
        }

        reportsBackPressCount += 1
        when (reportsBackPressCount) {
            2 -> Toast.makeText(
                context,
                "Presiona atrás una vez más para salir de la aplicación.",
                Toast.LENGTH_SHORT
            ).show()
            3 -> (context as? Activity)?.finish()
        }
    }

    // Config anterior del menu inferior: fondo blanco, activo PrimaryGreen, indicador Color(0xFFFFF6B8).
    val bottomBarContainerColor = PrimaryGreen
    val bottomBarSelectedColor = AccentGreen
    val bottomBarUnselectedColor = Color(0xFFE7EAF4)
    val bottomBarIndicatorColor = PrimaryDarkGreen
    val systemNavigationColor = if (selectedTab != 1) bottomBarContainerColor else Color.White
    val view = LocalView.current

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.navigationBarColor = systemNavigationColor.toArgb()
        WindowInsetsControllerCompat(window, view).isAppearanceLightNavigationBars = selectedTab == 1
    }

    Scaffold(
        bottomBar = {
            if (selectedTab != 1) {
                Surface(
                    color = bottomBarContainerColor,
                    tonalElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        NavigationBar(
                            containerColor = bottomBarContainerColor,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(55.dp)
                        ) {
                            NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((1).dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Message,
                                    contentDescription = "Chat",
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "Chat",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bottomBarSelectedColor,
                            selectedTextColor = bottomBarSelectedColor,
                            unselectedIconColor = bottomBarUnselectedColor,
                            unselectedTextColor = bottomBarUnselectedColor,
                            indicatorColor = bottomBarIndicatorColor
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((1).dp)
                            ) {
                            Icon(Icons.Default.BarChart,
                                contentDescription = "Reportes",
                                modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "Reportes",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bottomBarSelectedColor,
                            selectedTextColor = bottomBarSelectedColor,
                            unselectedIconColor = bottomBarUnselectedColor,
                            unselectedTextColor = bottomBarUnselectedColor,
                            indicatorColor = bottomBarIndicatorColor
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((1).dp)
                            )
                            {

                                Icon(Icons.Default.Notifications,
                                    contentDescription = "Avisos",
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "Avisos",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp
                                )
                            }
                       },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bottomBarSelectedColor,
                            selectedTextColor = bottomBarSelectedColor,
                            unselectedIconColor = bottomBarUnselectedColor,
                            unselectedTextColor = bottomBarUnselectedColor,
                            indicatorColor = bottomBarIndicatorColor
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((1).dp)
                            )
                            {

                                Icon(Icons.Default.Settings,
                                    contentDescription = "Configuracion",
                                            modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    "Configuracion",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        },
                        label = null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bottomBarSelectedColor,
                            selectedTextColor = bottomBarSelectedColor,
                            unselectedIconColor = bottomBarUnselectedColor,
                            unselectedTextColor = bottomBarUnselectedColor,
                            indicatorColor = bottomBarIndicatorColor
                        )
                    )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val fadeSpec = tween<Float>(durationMillis = 120)
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(durationMillis = 180)) { width -> width } + fadeIn(animationSpec = fadeSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(durationMillis = 180)) { width -> -width } + fadeOut(animationSpec = fadeSpec))
                    } else {
                        (slideInHorizontally(animationSpec = tween(durationMillis = 180)) { width -> -width } + fadeIn(animationSpec = fadeSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(durationMillis = 180)) { width -> width } + fadeOut(animationSpec = fadeSpec))
                    }
                },
                label = "MainTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> ReportsTab(
                        reports = reports,
                        daysBack = reportDaysBack,
                        isLoading = isLoadingReports,
                        loadingMessage = reportLoadingMessage,
                        onOpenRegister = {
                            updatePendingSharedVouchers(emptyList())
                            registerInitialDrafts = emptyList()
                            registerResetKey += 1
                            showRegisterSheet = true
                        },
                        onRegularize = { report ->
                            // La imagen del voucher no viene en el listado (ver
                            // DepositRepository.getReports); se pide bajo demanda
                            // justo antes de abrir el formulario de regularizacion.
                            registerScope.launch {
                                val enriched = depositRepository.enrichWithDetail(report)
                                updatePendingSharedVouchers(emptyList())
                                registerInitialDrafts = listOf(
                                    DepositDraft(
                                        empresa = enriched.empresa,
                                        banco = enriched.banco,
                                        cliente = enriched.cliente,
                                        imageUri = enriched.imageUrl.orEmpty(),
                                        // Marca este draft como una regularizacion del
                                        // deposito rechazado "enriched.id" -- onSubmit
                                        // (mas abajo) usa esto para llamar a
                                        // regularizeDepositDetailed en vez de crear un
                                        // deposito nuevo.
                                        regularizeDepositId = enriched.id
                                    )
                                )
                                registerResetKey += 1
                                showRegisterSheet = true
                            }
                        },
                        onLoadReportDetail = depositRepository::enrichWithDetail,
                        onLoadPreviousDays = {
                            val nextDaysBack = reportDaysBack + 1
                            reportDaysBack = nextDaysBack
                            saveReportDaysBack(context, nextDaysBack)
                            val searchMessage = reportSearchMessage(nextDaysBack)
                            reportLoadingMessage = searchMessage
                            Toast.makeText(context, searchMessage, Toast.LENGTH_SHORT).show()
                            registerScope.launch { refreshReportsFromApi(nextDaysBack) }
                        }
                    )
                    1 -> ChatTab(
                        wallpaper = chatWallpaper,
                        reports = reports,
                        messages = messages,
                        hasMoreOlderMessages = vendedorChatHasMore,
                        isLoadingOlderMessages = isLoadingOlderMessages,
                        onLoadOlderMessages = { registerScope.launch { loadOlderVendedorMessages() } },
                        onBack = { selectedTab = 0 },
                        onOpenRegister = {
                            showRegisterSheet = true
                        },
                        onSendMessage = { text ->
                            val cleanText = text.trim()
                            // Id temporal solo para poder ubicar y reemplazar este mensaje
                            // despues (por la respuesta del POST o por el eco de SignalR,
                            // lo que llegue primero) sin duplicarlo. El prefijo "pending-"
                            // es lo que usa el handler de tiempo real para reconciliar.
                            val tempId = "pending-${UUID.randomUUID()}"
                            val newMsg = ChatMessage(
                                id = tempId,
                                from = MessageFrom.USER,
                                text = cleanText,
                                date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                status = MessageStatus.SENT
                            )
                            messages = messages + newMsg
                            // El input libre de la barra inferior no responde a ningun
                            // deposito/tarjeta puntual (ChatTab no distingue "responder a
                            // X"), asi que siempre se manda por el canal general
                            // finanzas <-> vendedor, no colgado del primer deposito.
                            if (vendedorId.isNotBlank()) {
                                registerScope.launch {
                                    val sentMessage = chatRepository.sendVendedorMessage(
                                        vendedorId = vendedorId,
                                        content = cleanText
                                    )
                                    if (sentMessage != null) {
                                        // Si el eco de SignalR ya reconcilio este mismo
                                        // "pending-..." (ver RealtimeEvent.ChatMessageCreated
                                        // mas arriba), este map simplemente no encuentra nada
                                        // que reemplazar y no pasa nada.
                                        messages = messages.map { if (it.id == tempId) sentMessage else it }
                                    } else {
                                        Toast.makeText(context, "No se pudo guardar el mensaje en chat.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                    2 -> NoticesTab()
                    3 -> SettingsTab(
                        currentWallpaper = chatWallpaper,
                        onWallpaperChange = { chatWallpaper = it; saveChatWallpaper(context, it) },
                        onCheckForUpdates = onCheckForUpdates,
                        onLogout = onLogout
                    )
                }
            }
        }

        RegisterSheet(
            visible = showRegisterSheet,
            initialVoucherUris = registerSharedVoucherUriStrings.map(Uri::parse),
            initialDepositDrafts = registerInitialDrafts,
            resetKey = registerResetKey,
            onClose = {
                showRegisterSheet = false
            },
            onInitialVouchersConsumed = {
                // Keep the received vouchers in this registration session so
                // new WhatsApp shares append instead of replacing prior ones.
            },
            onInitialDepositDraftsConsumed = { consumed ->
                registerInitialDrafts = registerInitialDrafts.drop(consumed)
            },
            onSubmit = { solicitudes ->
                // FIX duplicados: el LaunchedEffect de RegisterSheet que llama a
                // onSubmit(pendingSubmit) puede volver a ejecutarse (recomposicion,
                // reset de "mode"/"pendingSubmit" via resetKey, etc.) y disparar
                // este bloque una segunda vez para el MISMO lote, creando
                // depositos duplicados en el backend con datos identicos. Este
                // guard hace que, mientras un envio siga en curso, cualquier
                // reentrada se ignore por completo.
                if (isSubmittingDeposit) {
                    return@RegisterSheet
                }
                isSubmittingDeposit = true
                registerScope.launch {
                  try {
                    val errors = mutableListOf<String>()

                    // Regularizaciones (drafts que vienen del boton "Regularizar" de un
                    // rechazado, ver onRegularize mas arriba): van por PUT /regularize
                    // sobre el deposito EXISTENTE, no crean uno nuevo. El backend ya
                    // agrega su propio mensaje de sistema al chat ("Regularizaste este
                    // deposito..."), asi que acá no se registra ninguna tarjeta nueva --
                    // alcanza con refrescar "reports" para que el estado vuelva a
                    // "Pendiente" en la lista.
                    val (regularizaciones, nuevos) = solicitudes.partition { it.regularizeDepositId != null }
                    var regularizadosOk = 0
                    regularizaciones.forEach { solicitud ->
                        val depositId = solicitud.regularizeDepositId!!
                        when (val result = depositRepository.regularizeDepositDetailed(depositId, solicitud)) {
                            is DepositCreateResult.Success -> regularizadosOk += 1
                            is DepositCreateResult.Error -> errors += result.message
                        }
                    }
                    if (regularizadosOk > 0) {
                        refreshReportsFromApi()
                        Toast.makeText(
                            context,
                            if (regularizadosOk == 1) "Depósito regularizado. Será procesado nuevamente."
                            else "$regularizadosOk depósitos regularizados. Serán procesados nuevamente.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val submitted = nuevos.mapNotNull { solicitud ->
                        when (val result = depositRepository.createDepositDetailed(solicitud)) {
                            is DepositCreateResult.Success -> solicitud to result.depositId
                            is DepositCreateResult.Error -> {
                                errors += result.message
                                null
                            }
                        }
                    }

                    if (submitted.isEmpty()) {
                        if (regularizadosOk == 0) {
                            Toast.makeText(
                                context,
                                errors.firstOrNull() ?: "No se pudo registrar el deposito en la API.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        if (nuevos.isEmpty()) {
                            // Todo lo que había en el lote era regularización y ya se
                            // procesó arriba (con éxito o con su propio toast de error) --
                            // no hay depósitos nuevos que registrar, se cierra el sheet
                            // igual que el flujo normal.
                            showRegisterSheet = false
                            updatePendingSharedVouchers(emptyList())
                            registerInitialDrafts = emptyList()
                            registerResetKey += 1
                        }
                        return@launch
                    }

                    if (submitted.size < nuevos.size) {
                        Toast.makeText(
                            context,
                            errors.firstOrNull() ?: "Algunos depositos no se pudieron registrar.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    val chatFailures = mutableListOf<String>()
                    // FIX duplicados: ya NO se pinta una tarjeta "optimista" aqui. Antes
                    // se agregaba un ChatMessage local con un id generado en el cliente,
                    // y por separado llegaba el eco real del backend (via SignalR, tras
                    // registerDepositMessage) con el id del servidor — como nunca
                    // coincidian, distintas condiciones de carrera terminaban mostrando
                    // la tarjeta dos veces (deduplicar por id, o incluso por
                    // solicitudId, no cubria todos los casos, p.ej. cuando "reports"
                    // aun no tenia el nuevo item y el evento en tiempo real caia en el
                    // fallback de refreshReportsFromApi()). Ahora la tarjeta del voucher
                    // se pinta UNA sola vez, cuando llega la confirmacion real del
                    // backend (RealtimeEvent.ChatMessageCreated mas abajo, o el refresh
                    // de historial si el evento en tiempo real no llega).
                    val newReports = submitted.mapIndexed { index, pair ->
                        val solicitud = pair.first
                        val depositId = pair.second
                        val sid = "#${(reports.size + index + 1).toString().padStart(3, '0')}"
                        val chatRegistered = chatRepository.registerDepositMessage(
                            depositId = depositId,
                            solicitudNum = sid,
                            draft = solicitud
                        )
                        if (!chatRegistered) chatFailures += sid
                        val voucherName = voucherFileName(sid, solicitud.imageUri)
                        Report(
                            id = depositId,
                            solicitudNum = sid,
                            empresa = solicitud.empresa,
                            cliente = solicitud.cliente,
                            banco = solicitud.banco,
                            fecha = date,
                            hora = time,
                            status = ReportStatus.PENDING,
                            imageUrl = solicitud.imageUri,
                            voucherName = voucherName
                        )
                    }

                    reports = newReports + reports
                    if (chatFailures.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            "Deposito registrado, pero no se pudo guardar en chat: ${chatFailures.joinToString()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    reportDaysBack = 0
                    saveReportDaysBack(context, 0)
                    showRegisterSheet = false
                    updatePendingSharedVouchers(emptyList())
                    registerInitialDrafts = emptyList()
                    registerResetKey += 1
                    selectedTab = 1
                  } finally {
                    isSubmittingDeposit = false
                  }
                }
            }
        )
    }
}

@Composable
fun ChatTab(
    wallpaper: String = "llanta",
    reports: List<Report> = emptyList(),
    messages: List<ChatMessage>,
    hasMoreOlderMessages: Boolean = false,
    isLoadingOlderMessages: Boolean = false,
    onLoadOlderMessages: () -> Unit = {},
    onBack: () -> Unit,
    onOpenRegister: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var currentMatch by remember { mutableStateOf(0) }
    var openedVoucher by remember { mutableStateOf<VoucherCard?>(null) }
    var inputFocused by remember { mutableStateOf(false) }
    var showOlderMessages by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val todayDate = remember { todayChatDate() }
    val visibleMessages = remember(messages, showOlderMessages, todayDate) {
        // FIX crash "Key ... was already used" en el LazyColumn del chat: el
        // item de cada mensaje usa item(key = msg.id), y Compose exige keys
        // unicas dentro de la misma lista. "messages" puede terminar con dos
        // entradas de igual id bajo condiciones de carrera (reconexion de
        // tiempo real durante el envio de un lote de vouchers, un evento
        // ChatMessageCreated que llega mas de una vez desde el backend,
        // etc.) — mergeChatMessages ya deduplica al traer historial, pero los
        // agregados en vivo (RealtimeEvent.ChatMessageCreated, envio
        // optimista) se hacen directo sobre "messages" y no pasan por ahi.
        // Se deduplica aca, como ultima linea de defensa antes de pintar,
        // quedandonos con la version MAS reciente de cada id: associateBy
        // sobreescribe el value en cada colision pero mantiene la posicion
        // original en el orden de iteracion (no reordena).
        val deduped = messages.associateBy { it.id }.values.toList()
        val filteredMessages = if (showOlderMessages) {
            deduped
        } else {
            deduped.filter { it.isTodayMessage(todayDate) }
        }
        filteredMessages.sortedWith(compareBy<ChatMessage> { it.chatSortDate(todayDate) }.thenBy { it.time })
    }
    val hiddenOlderCount = remember(messages, todayDate) {
        messages.count { !it.isTodayMessage(todayDate) }
    }
    val hasOlderMessagesToLoad = !showOlderMessages && hiddenOlderCount > 0
    val listIndexOffset = 1
    val inputBlinkTransition = rememberInfiniteTransition(label = "ChatInputBlink")
    val focusedBorderAlpha by inputBlinkTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ChatInputBorderAlpha"
    )
    val emojis = remember {
        listOf(
            "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE0A",
            "\uD83D\uDE0D", "\uD83D\uDC4D", "\uD83D\uDE4F", "\u2705",
            "\uD83D\uDCF7", "\uD83D\uDCB5", "\uD83C\uDFE6", "\uD83D\uDE97"
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val matches = remember(visibleMessages, searchText) {
        if (searchText.isBlank()) {
            emptyList()
        } else {
            visibleMessages.indices
                .filter { index -> visibleMessages[index].searchableText().contains(searchText, ignoreCase = true) }
                .asReversed()
        }
    }
    val activeMatchIndex = matches.getOrNull(currentMatch)

    LaunchedEffect(matches) {
        currentMatch = 0
        matches.firstOrNull()?.let {
            listState.animateScrollToItem(chatListIndexForMessage(it, visibleMessages, todayDate, listIndexOffset))
        }
    }

    LaunchedEffect(messages.size, searchOpen) {
        if (!searchOpen && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(
                chatListIndexForMessage(visibleMessages.lastIndex, visibleMessages, todayDate, listIndexOffset)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFF))) {
        // Chat Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(horizontal = 16.dp, vertical = 1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver a reportes", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = Color.White
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bot_tire2),
                        contentDescription = "TireBot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(-1.dp)
                ) {
                    Text(
                        "TireBot",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 14.sp
                    )
                    Text(
                        "en linea - respuesta automatica",
                        color = Color(0xFFFFF6B8),
                        fontSize = 11.sp,
                        lineHeight = 11.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (searchOpen) {
            SearchBar(
                query = searchText,
                onQueryChange = { searchText = it },
                matchLabel = if (searchText.isBlank()) "" else "${if (matches.isEmpty()) 0 else currentMatch + 1}/${matches.size}",
                onPrevious = {
                    if (matches.isNotEmpty()) {
                        val nextMatch = (currentMatch + 1).coerceAtMost(matches.lastIndex)
                        currentMatch = nextMatch
                        scope.launch {
                            listState.animateScrollToItem(
                                chatListIndexForMessage(matches[nextMatch], visibleMessages, todayDate, listIndexOffset)
                            )
                        }
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        val nextMatch = (currentMatch - 1).coerceAtLeast(0)
                        currentMatch = nextMatch
                        scope.launch {
                            listState.animateScrollToItem(
                                chatListIndexForMessage(matches[nextMatch], visibleMessages, todayDate, listIndexOffset)
                            )
                        }
                    }
                },
                onClose = {
                    searchOpen = false
                    searchText = ""
                    currentMatch = 0
                }
            )
        }

        // Messages List
        Box(modifier = Modifier.weight(1f)) {
            if (wallpaper == "llanta") {
                Image(
                    painter = painterResource(id = R.drawable.llanta),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (wallpaper.startsWith("content://")) {
                coil.compose.AsyncImage(
                    model = wallpaper,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    reverseLayout = false
                ) {
                    item(key = "show_older_messages") {
                        ShowOlderMessagesButton(
                            count = hiddenOlderCount,
                            isLoading = isLoadingOlderMessages,
                            onClick = {
                                when {
                                    hasOlderMessagesToLoad -> {
                                        // Ya hay mensajes viejos cargados en memoria (de la
                                        // carga inicial/cache): solo hay que revelarlos, sin
                                        // pegarle a la red.
                                        showOlderMessages = true
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                    hasMoreOlderMessages && !isLoadingOlderMessages -> {
                                        // Se agoto lo que ya se tenia local: pedir la
                                        // siguiente pagina real al backend (igual que el
                                        // panel), y revelar apenas llegue.
                                        showOlderMessages = true
                                        onLoadOlderMessages()
                                    }
                                    else -> {
                                        Toast.makeText(context, "No hay mas mensajes anteriores.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    visibleMessages.forEachIndexed { index, msg ->
                        val previousMessage = visibleMessages.getOrNull(index - 1)
                        val currentDate = msg.normalizedChatDate(todayDate)
                        val previousDate = previousMessage?.normalizedChatDate(todayDate)

                        if (currentDate != previousDate) {
                            item(key = "day_separator_$currentDate") {
                                ChatDaySeparator(label = chatDayLabel(currentDate, todayDate))
                            }
                        }

                        item(key = msg.id) {
                            MessageBubble(
                                message = msg,
                                reports = reports,
                                isSearchMatch = index == activeMatchIndex,
                                onVoucherClick = { openedVoucher = it },
                                // FIX citas cruzadas: onReplyClick ahora recibe el depositId
                                // (GUID estable) del voucher, no el solicitudNum posicional.
                                onReplyClick = { depositId ->
                                    openedVoucher = reports.find { it.id == depositId }?.toVoucherCard()
                                }
                            )
                        }
                    }
                }

                // Input Area
                // FIX: tenia un offset(y = 25.dp) fijo que se sumaba al
                // navigationBarsPadding() del Column padre. Modifier.offset no
                // reserva espacio, solo desplaza el dibujo, asi que ese
                // desplazamiento se comia el inset reservado para la barra de
                // navegacion. Con botones clasicos (inset mas alto, ~48dp)
                // todavia quedaba margen y se veia bien; con gestos (inset mas
                // chico, ~24dp) el offset lo consumia entero y la barra
                // quedaba pegada/atropellada contra el borde real de pantalla.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp, bottom = 0.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(28.dp),
                        border = if (inputFocused) {
                            BorderStroke(2.dp, Color(0xFF0A84FF).copy(alpha = focusedBorderAlpha))
                        } else {
                            null
                        },
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(
                                    onClick = { emojiPickerOpen = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.InsertEmoticon, contentDescription = "Emoji", tint = Color.Black, modifier = Modifier.size(22.dp))
                                }
                                DropdownMenu(
                                    expanded = emojiPickerOpen,
                                    onDismissRequest = { emojiPickerOpen = false },
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = Color.White,
                                    shadowElevation = 8.dp
                                ) {
                                    emojis.chunked(6).forEach { rowEmojis ->
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rowEmojis.forEach { emoji ->
                                                Text(
                                                    text = emoji,
                                                    fontSize = 22.sp,
                                                    fontFamily = FontFamily.Default,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clickable {
                                                            textInput += emoji
                                                            emojiPickerOpen = false
                                                        }
                                                        .padding(4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    fontFamily = FontFamily.Default
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(horizontal = 4.dp)
                                    .onFocusChanged { focusState ->
                                        inputFocused = focusState.isFocused
                                        if (focusState.isFocused) keyboardController?.show()
                                    },
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (textInput.isBlank()) {
                                            Text("Escribe un mensaje", fontSize = 14.sp, color = Color.Gray)
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            IconButton(
                                onClick = {
                                    if (textInput.isBlank()) {
                                        onOpenRegister()
                                    } else {
                                        onSendMessage(textInput)
                                        textInput = ""
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                val hasMessage = textInput.isNotBlank()
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = CircleShape,
                                    color = if (hasMessage) PrimaryGreen else Color(0xFFFFE500)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (hasMessage) Icons.AutoMirrored.Filled.Send else Icons.Default.Add,
                                            contentDescription = if (hasMessage) "Enviar" else "Registrar voucher",
                                            tint = if (hasMessage) Color.White else PrimaryDarkGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        openedVoucher?.let { voucher ->
            VoucherImageDialog(
                voucher = voucher,
                onDismiss = { openedVoucher = null }
            )
        }
    }
}

@Composable
private fun ShowOlderMessagesButton(
    count: Int,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.clickable(enabled = !isLoading, onClick = onClick),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, Color(0xFFE7EAF4))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = PrimaryGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cargando...",
                        color = Color(0xFF17265F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (count == 1) "Ver mensaje anterior" else "Ver mensajes anteriores",
                        color = Color(0xFF17265F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatDaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFE7EAF4).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                color = Color(0xFF344171),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Buscar en el chat", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(matchLabel, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(42.dp))
            IconButton(onClick = onPrevious, enabled = query.isNotBlank()) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Resultado anterior", tint = PrimaryGreen)
            }
            IconButton(onClick = onNext, enabled = query.isNotBlank()) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Resultado siguiente", tint = PrimaryGreen)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar busqueda", tint = Color.Gray)
            }
        }

    }
}

@Composable
private fun VoucherImageDialog(
    voucher: VoucherCard,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            color = Color(0xFF17265F),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(voucher.voucherName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(voucher.solicitudId, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar imagen", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (voucher.imageUrl.isPdfVoucher()) {
                        PdfPreview(
                            uriString = voucher.imageUrl,
                            depositId = voucher.depositId,
                            modifier = Modifier.fillMaxSize(),
                            label = "PDF adjunto"
                        )
                    } else {
                        // Antes usaba voucher.imageUrl (referencia cruda de GCS) directo
                        // en un AsyncImage normal, que no la sabe firmar. Con depositId
                        // se pide via el endpoint redirect, que siempre firma fresco.
                        SignedVoucherImage(
                            depositId = voucher.depositId,
                            contentDescription = "Voucher ${voucher.solicitudId}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsTab(
    reports: List<Report>,
    daysBack: Int,
    isLoading: Boolean,
    loadingMessage: String,
    onOpenRegister: () -> Unit,
    onRegularize: (Report) -> Unit,
    onLoadReportDetail: suspend (Report) -> Report = { it },
    onLoadPreviousDays: () -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    var currentPage by remember { mutableStateOf(0) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reportsListState = rememberLazyListState()
    // FIX defensivo (mismo patron que "visibleMessages" en el chat): esta
    // LazyColumn usa items(key = { it.id }), asi que dos Report con el mismo
    // id revientan con "Key ... was already used". El envio de un LISTADO de
    // vouchers agrega optimistamente "newReports + reports" (mas arriba) y
    // en paralelo puede llegar un refreshReportsFromApi() u otro evento en
    // tiempo real tocando "reports" -- se deduplica aca, quedandonos con la
    // version mas reciente de cada id, como ultima linea de defensa antes de
    // paginar/pintar.
    val dedupedReports = reports.associateBy { it.id }.values.toList()
    val filteredReports = when (filter) {
        "pending" -> dedupedReports.filter { it.status == ReportStatus.PENDING }
        "validated" -> dedupedReports.filter { it.status == ReportStatus.VALIDATED }
        "rejected" -> dedupedReports.filter { it.status == ReportStatus.REJECTED }
        else -> dedupedReports
    }
    val reportsPageSize = 6
    val totalPages = ((filteredReports.size + reportsPageSize - 1) / reportsPageSize).coerceAtLeast(1)
    val pagedReports = filteredReports
        .drop(currentPage * reportsPageSize)
        .take(reportsPageSize)
    val highlightedReportId by remember {
        derivedStateOf {
            val layoutInfo = reportsListState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> kotlin.math.abs((item.offset + item.size / 2) - viewportCenter) }
                ?.key as? String
        }
    }

    LaunchedEffect(filter, reports) {
        currentPage = 0
    }

    LaunchedEffect(filteredReports.size, totalPages) {
        if (currentPage > totalPages - 1) {
            currentPage = totalPages - 1
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFF))) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(16.dp,7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "Reportes",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = PlusJakartaSansFamily
                    )
                    Text(
                        if (daysBack == 0) {
                            "${reports.size} solicitudes de hoy"
                        } else {
                            "${reports.size} solicitudes de los ultimos ${daysBack + 1} dias"
                        },
                        color = Color(0xFFFFF6B8),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { showExportSheet = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    DropdownMenu(
                        expanded = showExportSheet,
                        onDismissRequest = { showExportSheet = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = Color.White,
                        shadowElevation = 10.dp
                    ) {
                        ExportDateRange.entries.forEach { range ->
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        exportReportsForExcel(context, reports.filterByExportRange(range))
                                        showExportSheet = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFFFF6B8)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.width(150.dp)) {
                                    Text(range.label, color = PrimaryDarkGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(range.description, color = Color(0xFF6A7394), fontSize = 10.sp, lineHeight = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isLoading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF6B8)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = PrimaryGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        loadingMessage.ifBlank { "Buscando depositos..." },
                        color = PrimaryDarkGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Filters
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterChip(selected = filter == "all", label = "Todas", onClick = { filter = "all" }) }
            item { FilterChip(selected = filter == "pending", label = "Pendientes", onClick = { filter = "pending" }) }
            item { FilterChip(selected = filter == "validated", label = "Validadas", onClick = { filter = "validated" }) }
            item { FilterChip(selected = filter == "rejected", label = "Rechazadas", onClick = { filter = "rejected" }) }
        }

        // List
        LazyColumn(
            state = reportsListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (pagedReports.isEmpty()) {
                item {
                    EmptyReportsState(daysBack = daysBack)
                }
            }
            items(
                items = pagedReports,
                key = { it.id }
            ) { report ->
                ReportItem(
                    report = report,
                    highlighted = highlightedReportId == report.id,
                    onClick = { selectedReport = report },
                    onRegularize = { onRegularize(report) }
                )
            }
        }

        ReportsPaginationBar(
            currentPage = currentPage,
            totalPages = totalPages,
            onPageSelected = { page -> currentPage = page },
            onPrevious = { currentPage = (currentPage - 1).coerceAtLeast(0) },
            onNext = {
                if (currentPage < totalPages - 1) {
                    currentPage += 1
                } else {
                    onLoadPreviousDays()
                }
            },
            onOpenRegister = onOpenRegister
        )

        selectedReport?.let { report ->
            ReportDetailSheet(
                report = report,
                onClose = { selectedReport = null },
                onLoadDetail = onLoadReportDetail
            )
        }

    }
}

private enum class ExportDateRange(val label: String, val description: String) {
    Today("Hoy", "Solo reportes registrados hoy"),
    Last7Days("Últimos 7 días", "Reportes de la última semana"),
    ThisMonth("Este mes", "Reportes del mes actual"),
    All("Todo", "Exportar todos los reportes")
}

@Composable
private fun EmptyReportsState(daysBack: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE7EAF4)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = Color(0xFFFFF6B8)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (daysBack == 0) "No hay depositos de hoy" else "No hay depositos en este rango",
                color = PrimaryDarkGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Presiona Sig. para consultar dias anteriores.",
                color = Color(0xFF6A7394),
                fontSize = 12.sp
            )
        }
    }
}
@Composable
private fun ReportsPaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenRegister: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaginationPillButton(
                        text = "Ant.",
                        icon = Icons.Default.ChevronLeft,
                        onClick = onPrevious,
                        enabled = currentPage > 0,
                        iconFirst = true,
                        contentDescription = "Pagina anterior"
                    )

                    repeat(totalPages) { page ->
                        PaginationPageButton(
                            page = page,
                            selected = page == currentPage,
                            onClick = { onPageSelected(page) }
                        )
                    }

                    PaginationPillButton(
                        text = "Sig.",
                        icon = Icons.Default.ChevronRight,
                        onClick = onNext,
                        enabled = true,
                        iconFirst = false,
                        contentDescription = "Pagina siguiente"
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            FloatingActionButton(
                onClick = onOpenRegister,
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp,
                    focusedElevation = 10.dp,
                    hoveredElevation = 10.dp
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo deposito", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PaginationPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    iconFirst: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val background = if (enabled) PrimaryGreen else Color(0xFFF6F7FB)
    val contentColor = if (enabled) Color.White else Color(0xFF9EA6C4)

    Surface(
        color = background,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .height(36.dp)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (iconFirst) {
                Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (!iconFirst) {
                Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PaginationPageButton(
    page: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) PrimaryGreen else Color(0xFFE7EAF4),
        shape = CircleShape,
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${page + 1}",
                color = if (selected) Color.White else Color(0xFF6A7394),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NoticesTab() {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    "Avisos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = PlusJakartaSansFamily
                )
                Text("Notificaciones y comunicados", color = Color(0xFFFFF6B8), fontSize = 12.sp, modifier = Modifier.height(20.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(86.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFF6B8),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.75f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Construction,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                Text(
                    text = "Panel de avisos en construccion",
                    color = Color(0xFF17265F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFamily
                )
                Text(
                    text = "Los comunicados apareceran aqui cuando el modulo este disponible.",
                    color = Color(0xFF6A7394),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        color = if (selected) PrimaryGreen else Color(0xFFE0E0E0),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ReportItem(
    report: Report,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRegularize: () -> Unit
) {
    val statusColor = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF166534)
        ReportStatus.REJECTED -> Color(0xFF991B1B)
        ReportStatus.PENDING -> Color(0xFF17265F)
    }
    val statusBg = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFFDCFCE7)
        ReportStatus.REJECTED -> Color(0xFFFEE2E2)
        ReportStatus.PENDING -> Color(0xFFFFF6B8)
    }
    val cardBg = Color.White
    val statusAccent = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF22C55E)
        ReportStatus.REJECTED -> Color(0xFFEF4444)
        ReportStatus.PENDING -> Color(0xFFFFE500)
    }
    val cardBorder = if (highlighted) statusAccent.copy(alpha = 0.58f) else Color(0xFFE7EAF4)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 10.dp else 3.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusAccent)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = report.solicitudNum, fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 11.sp)
                    if (report.status == ReportStatus.REJECTED) {
                        Surface(
                            color = PrimaryGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable(onClick = onRegularize)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regularizar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                        Text(report.fecha, color = Color(0xFF344171), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(report.hora, color = Color(0xFF344171), fontSize = 10.sp)

                    Surface(color = statusBg, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = report.status.spanishLabel(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = Color(0xFFF8FAFF),
                    border = BorderStroke(2.dp, PrimaryGreen.copy(alpha = 0.35f)),
                    shadowElevation = 3.dp
                ) {
                    Image(
                        painter = painterResource(id = report.companyLogoRes()),
                        contentDescription = report.empresa,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = report.cliente.ifBlank { "Sin nombre" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF17265F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (report.status == ReportStatus.VALIDATED) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Anexo: ${report.anexo ?: "RECAU MN"}",
                                color = Color(0xFF17265F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF344171), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = report.banco,
                            color = Color(0xFF344171),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (report.status == ReportStatus.VALIDATED) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Importe: ${report.importe ?: "-"}",
                                color = Color(0xFF17265F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private fun Report.companyLogoRes(): Int {
    return if (empresa.contains("EVOLUTION", ignoreCase = true)) {
        R.drawable.evo_logo
    } else {
        R.drawable.jch_logo
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDetailSheet(
    report: Report,
    onClose: () -> Unit,
    onLoadDetail: suspend (Report) -> Report = { it }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.7f
    // report (de la lista) no trae imagen de voucher ni motivo de rechazo a
    // proposito (ver DepositRepository.getReports). Se completan aca, una
    // sola vez, al abrir este detalle puntual.
    var enrichedReport by remember(report.id) { mutableStateOf(report) }
    var isLoadingDetail by remember(report.id) { mutableStateOf(true) }
    LaunchedEffect(report.id) {
        isLoadingDetail = true
        enrichedReport = onLoadDetail(report)
        isLoadingDetail = false
    }
    val statusColor = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF166534)
        ReportStatus.REJECTED -> Color(0xFF991B1B)
        ReportStatus.PENDING -> Color(0xFF17265F)
    }
    val statusBg = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFFDCFCE7)
        ReportStatus.REJECTED -> Color(0xFFFEE2E2)
        ReportStatus.PENDING -> Color(0xFFFFF6B8)
    }
    val statusLabel = report.status.spanishLabel()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color(0xFFF8FAFF),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = PrimaryGreen.copy(alpha = 0.45f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Solicitud ${report.solicitudNum}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFamily,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = statusBg, shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PrimaryDarkGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            StatusBanner(report.status)
            Spacer(modifier = Modifier.height(14.dp))
            DetailRows(
                rows = when (report.status) {
                    ReportStatus.VALIDATED -> listOf(
                        "Empresa" to report.empresa,
                        "Solicitado por" to (report.solicitadoPor ?: "-"),
                        "Sucursal" to (report.sucursal ?: "-"),
                        "Banco" to report.banco,
                        "Anexo" to (report.anexo ?: "RECAU MN"),
                        "Fecha Deposito" to report.fecha,
                        "Operacion" to (report.operacion ?: "-"),
                        "Importe" to (report.importe ?: "-")
                    )
                    else -> listOf(
                        "Solicitado por" to (report.solicitadoPor ?: "-"),
                        "Sucursal" to (report.sucursal ?: "-"),
                        "Empresa" to report.empresa,
                        "Cliente" to report.cliente.ifBlank { "-" },
                        "Banco" to report.banco,
                        "Fecha" to report.fecha,
                        "Hora" to report.hora
                    )
                }
            )

            if (report.status == ReportStatus.REJECTED && (isLoadingDetail || !enrichedReport.mensajeValidacion.isNullOrBlank())) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("Motivo del rechazo", fontSize = 11.sp, color = PrimaryDarkGreen.copy(alpha = 0.72f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFF),
                    border = BorderStroke(1.dp, Color(0xFFB91C1C).copy(alpha = 0.32f))
                ) {
                    Text(
                        text = if (isLoadingDetail) "Cargando..." else enrichedReport.mensajeValidacion.orEmpty(),
                        color = Color(0xFFB71C1C),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            // Se usa "report" (no "enrichedReport"): la referencia del voucher
            // ya viene gratis en el listado y SignedVoucherImage la resuelve
            // por su cuenta (con su propio loading interno) — no hace falta
            // esperar el fetch de detalle (que aca solo trae el motivo de
            // rechazo) para poder mostrar la imagen.
            ReportVoucherSection(report)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportVoucherSection(report: Report) {
    val imageUrl = report.imageUrl

    Text("Voucher adjunto", fontSize = 11.sp, color = PrimaryDarkGreen.copy(alpha = 0.72f), fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFF)),
        border = BorderStroke(1.dp, Color(0xFFE7EAF4))
    ) {
        Column {
            when {
                imageUrl.isNullOrBlank() -> MissingVoucherPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )

                imageUrl.isPdfVoucher() -> PdfReportPreview(
                    uriString = imageUrl,
                    depositId = report.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )

                else -> SignedVoucherImage(
                    depositId = report.id,
                    contentDescription = "Voucher ${report.solicitudNum}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .background(Color(0xFFF8FAFF))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryGreen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        imageUrl.isNullOrBlank() -> Icons.Default.ImageNotSupported
                        imageUrl.isPdfVoucher() -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.CameraAlt
                    },
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(report.voucherName ?: "Voucher", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PdfReportPreview(uriString: String, depositId: String, modifier: Modifier = Modifier) {
    PdfPreview(uriString = uriString, depositId = depositId, modifier = modifier, label = "Documento PDF adjunto")
}

@Composable
private fun MissingVoucherPreview(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFF8FAFF)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sin voucher adjunto", color = PrimaryDarkGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBanner(status: ReportStatus) {
    val bg: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val title: String
    val subtitle: String
    val color: Color

    when (status) {
        ReportStatus.VALIDATED -> {
            bg = Color(0xFFDCFCE7)
            icon = Icons.Default.CheckCircle
            title = "DEPOSITO CONFIRMADO"
            subtitle = "Validado por el sistema"
            color = Color(0xFF166534)
        }
        ReportStatus.REJECTED -> {
            bg = Color(0xFFFFF3F3)
            icon = Icons.Default.Error
            title = "DEPOSITO RECHAZADO"
            subtitle = "Requiere correccion"
            color = Color(0xFFB71C1C)
        }
        ReportStatus.PENDING -> {
            bg = Color(0xFFFFF9D6)
            icon = Icons.Default.Schedule
            title = "EN PROCESO"
            subtitle = "Pendiente de validacion por el sistema"
            color = Color(0xFF17265F)
        }
    }

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = bg) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = color.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetailRows(rows: List<Pair<String, String>>) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFFF8FAFF)) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.first, color = PrimaryGreen.copy(alpha = 0.72f), fontSize = 12.sp, modifier = Modifier.width(112.dp))
                    Text(
                        row.second,
                        color = PrimaryDarkGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = Color(0xFFE7EAF4))
                }
            }
        }
    }
}

private fun exportReportsForExcel(context: Context, reports: List<Report>) {
    val exportable = reports.filter { it.status == ReportStatus.VALIDATED || it.status == ReportStatus.REJECTED }
    val headers = listOf(
        "Solicitud", "Estado", "Solicitado por", "Sucursal", "Empresa", "Cliente", "Banco",
        "Anexo", "Fecha Deposito", "Hora", "Operacion", "Importe", "Motivo Rechazo"
    )
    val rows = exportable.map { report ->
        listOf(
            report.solicitudNum,
            when (report.status) {
                ReportStatus.VALIDATED -> "Validado"
                ReportStatus.REJECTED -> "Rechazado"
                ReportStatus.PENDING -> "Pendiente"
            },
            report.solicitadoPor.orEmpty(),
            report.sucursal.orEmpty(),
            report.empresa,
            report.cliente,
            report.banco,
            report.anexo.orEmpty(),
            report.fecha,
            report.hora,
            report.operacion.orEmpty(),
            report.importe.orEmpty(),
            if (report.status == ReportStatus.REJECTED) report.mensajeValidacion.orEmpty() else ""
        )
    }
    val csv = buildString {
        appendLine(headers.joinToString(",") { it.csvCell() })
        rows.forEach { row -> appendLine(row.joinToString(",") { it.csvCell() }) }
    }
    val file = File(context.cacheDir, "CONFIRMO_Reporte_${SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())}.csv")
    file.writeText(csv, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar reporte"))
}

private fun List<Report>.filterByExportRange(range: ExportDateRange): List<Report> {
    if (range == ExportDateRange.All) return this

    val parser = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = when (range) {
        ExportDateRange.Today -> today.clone() as Calendar
        ExportDateRange.Last7Days -> (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
        ExportDateRange.ThisMonth -> (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        ExportDateRange.All -> today
    }

    return filter { report ->
        val reportDate = runCatching { parser.parse(report.fecha) }.getOrNull() ?: return@filter false
        !reportDate.before(start.time) && !reportDate.after(today.time)
    }
}

private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

@Composable
fun SettingsTab(
    currentWallpaper: String = "llanta",
    onWallpaperChange: (String) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val depositRepository = remember { DepositRepository(context.applicationContext, sessionManager) }
    val authRepository = remember {
        AuthRepository(
            authApi = ApiClient.authApi,
            sessionManager = sessionManager
        )
    }
    val fullName = sessionManager.getFullName().orEmpty()
    val phoneNumber = sessionManager.getPhoneNumber().orEmpty()
    val empresaId = sessionManager.getEmpresaId().orEmpty()
    val sucursalId = sessionManager.getSucursalId().orEmpty()
    var empresaName by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onWallpaperChange(uri.toString())
            showWallpaperDialog = false
        }
    }

    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("Fondo del chat", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf(
                        "llanta" to "Textura Llantas (Predeterminado)",
                        "default" to "Blanco humo",
                        "dark" to "Oscuro carbón",
                        "green_soft" to "Verde suave",
                        "blue_night" to "Azul noche",
                        "sand" to "Arena cálida",
                        "slate" to "Pizarra moderna"
                    )
                    options.forEach { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onWallpaperChange(id)
                                    showWallpaperDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentWallpaper == id,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(name)
                        }
                    }
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Elegir foto de la galería...", color = PrimaryDarkGreen)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Cerrar", color = PrimaryGreen)
                }
            }
        )
    }
    LaunchedEffect(empresaId) {
        empresaName = depositRepository
            .getCompanies()
            .firstOrNull { it.id == empresaId }
            ?.nombre
            .orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    "Configuracion",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = PlusJakartaSansFamily
                )
                Text("Perfil y preferences", color = Color(0xFFFFF6B8), fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            // Profile Card
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE7EAF4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = PrimaryGreen) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(fullName.userInitials(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(fullName.ifBlank { "Usuario" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryDarkGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow(Icons.Default.Business, "Empresa", empresaName.ifBlank { "No disponible" })
                    SettingsRow(Icons.Default.Store, "Sucursal", if (sucursalId.isBlank()) "No disponible" else "Asignada")
                    SettingsRow(Icons.Default.Phone, "Celular", phoneNumber.displayPhone())
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("CUENTA", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE7EAF4)),
                shadowElevation = 4.dp
            ) {
                Column {
                    SettingsActionRow(
                        Icons.Default.Key,
                        "Cambiar contrasena",
                        "Actualiza tu acceso",
                        onClick = {
                            showPasswordDialog = true
                        }
                    )
                    SettingsActionRow(
                        Icons.Default.ExitToApp,
                        "Cerrar sesion",
                        "Salir de la aplicacion",
                        isDestructive = true,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Spacer(modifier = Modifier.height(18.dp))
            Text("APARIENCIA", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE7EAF4)),
                shadowElevation = 4.dp
            ) {
                Column {
                    SettingsActionRow(
                        Icons.Default.Palette,
                        "Fondo del chat",
                        "Personaliza el fondo de tus conversaciones",
                        onClick = { showWallpaperDialog = true }
                    )
                }
            }
            Text("APLICACION", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE7EAF4)),
                shadowElevation = 4.dp
            ) {
                Column {
                    SettingsRow(Icons.Default.Info, "Version", BuildConfig.VERSION_NAME)
                    HorizontalDivider(color = Color(0xFFE7EAF4))
                    SettingsActionRow(
                        Icons.Default.SystemUpdate,
                        "Actualizar version",
                        "Buscar nueva version en GitHub",
                        onClick = onCheckForUpdates
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onChangePassword = { currentPassword, newPassword ->
                authRepository.changePassword(currentPassword, newPassword)
            },
            onPasswordChanged = {
                Toast.makeText(context, "Contrasena actualizada. Inicia sesion nuevamente.", Toast.LENGTH_LONG).show()
                showPasswordDialog = false
                onLogout()
            }
        )
    }

    if (showLogoutDialog) {
        LogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            }
        )
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(60.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun String.userInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "U" }
}

private fun String.displayPhone(): String {
    val digits = filter { it.isDigit() }
    return when {
        digits.length == 11 && digits.startsWith("51") -> digits.drop(2)
        digits.isNotBlank() -> digits
        else -> "No disponible"
    }
}

@Composable
fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    desc: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (isDestructive) Color(0xFFFFF3F3) else Color(0xFFFFF6B8)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (isDestructive) Color.Red else PrimaryGreen, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isDestructive) Color.Red else Color.Black)
            Text(desc, color = Color.Gray, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: suspend (String, String) -> AuthResult,
    onPasswordChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSave = !isLoading && currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFFF8FAFF),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Cambiar contrasena",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFamily,
                color = PrimaryDarkGreen
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ingresa tu contrasena actual y define una nueva.",
                    color = Color(0xFF344171),
                    fontSize = 12.sp
                )
                PasswordField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    label = "Contrasena actual",
                    visible = currentVisible,
                    onVisibilityChange = { currentVisible = !currentVisible }
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = "Nueva contrasena",
                    visible = newVisible,
                    onVisibilityChange = { newVisible = !newVisible }
                )
                PasswordField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = "Confirmar contrasena",
                    visible = confirmVisible,
                    onVisibilityChange = { confirmVisible = !confirmVisible }
                )
                errorMessage?.let {
                    Text(it, color = Color(0xFFB71C1C), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMessage = when {
                        currentPassword.isBlank() -> "Ingresa tu contrasena actual."
                        newPassword.length < 8 -> "La nueva contrasena debe tener al menos 8 caracteres."
                        newPassword != confirmPassword -> "Las contrasenas nuevas no coinciden."
                        else -> null
                    }
                    if (errorMessage == null) {
                        isLoading = true
                        scope.launch {
                            when (val result = onChangePassword(currentPassword, newPassword)) {
                                AuthResult.Success -> onPasswordChanged()
                                is AuthResult.Error -> errorMessage = result.message
                            }
                            isLoading = false
                        }
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar", color = PrimaryGreen)
            }
        }
    )
}

@Composable
private fun LogoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFF3F3)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red, modifier = Modifier.size(26.dp))
                }
            }
        },
        title = {
            Text("Cerrar sesion", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFamily)
        },
        text = {
            Text(
                "Se cerrara tu sesion guardada. La proxima vez deberas ingresar nuevamente.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Cerrar sesion", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) keyboardController?.show()
            },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = PrimaryDarkGreen,
            unfocusedTextColor = PrimaryDarkGreen,
            focusedLabelColor = PrimaryGreen,
            unfocusedLabelColor = Color(0xFF344171),
            focusedIndicatorColor = AccentGreen,
            unfocusedIndicatorColor = Color(0xFFE7EAF4),
            cursorColor = PrimaryGreen
        )
    )
}


private fun mergeChatMessages(
    currentMessages: List<ChatMessage>,
    incomingMessages: List<ChatMessage>
): List<ChatMessage> {
    return (currentMessages + incomingMessages)
        .distinctBy { it.id }
        .sortedWith(compareBy<ChatMessage> { it.chatSortDate(todayChatDate()) }.thenBy { it.time })
}

private fun ChatMessage.searchableText(): String {
    return buildString {
        append(text.orEmpty())
        voucherCard?.let {
            append(' ')
            append(it.solicitudId)
            append(' ')
            append(it.voucherName)
            append(' ')
            append(it.empresa)
            append(' ')
            append(it.banco)
            append(' ')
            append(it.cliente)
            append(' ')
            append(it.status.spanishLabel())
        }
        structuredData?.let {
            append(' ')
            append(it.title)
            append(' ')
            append(it.footer)
            it.rows.forEach { row ->
                append(' ')
                append(row.first)
                append(' ')
                append(row.second)
            }
        }
    }
}

private fun todayChatDate(): String {
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
}

private fun ChatMessage.isTodayMessage(todayDate: String): Boolean {
    return date.isBlank() || date == todayDate
}

private fun ChatMessage.normalizedChatDate(todayDate: String): String {
    return date.ifBlank { todayDate }
}

private fun ChatMessage.chatSortDate(todayDate: String): String {
    val parts = normalizedChatDate(todayDate).split("/")
    if (parts.size != 3) return normalizedChatDate(todayDate)
    return "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
}

private fun chatDayLabel(date: String, todayDate: String): String {
    return if (date == todayDate) "Hoy" else date
}

private fun chatListIndexForMessage(
    messageIndex: Int,
    visibleMessages: List<ChatMessage>,
    todayDate: String,
    leadingItems: Int
): Int {
    val separatorCount = visibleMessages
        .take(messageIndex + 1)
        .map { it.normalizedChatDate(todayDate) }
        .distinct()
        .size
    return leadingItems + separatorCount + messageIndex
}

private fun voucherFileName(solicitudId: String, imageUri: String): String {
    val extension = voucherExtension(imageUri)
    return "Voucher_${solicitudId.replace("#", "")}.$extension"
}

private fun Report.toVoucherCard(): VoucherCard {
    return VoucherCard(
        solicitudId = solicitudNum,
        depositId = id,
        voucherName = voucherName ?: "Voucher_${solicitudNum.replace("#", "")}.jpg",
        imageUrl = imageUrl.orEmpty(),
        empresa = empresa,
        banco = banco,
        cliente = cliente,
        status = status
    )
}

private fun copySharedVoucherToSessionCache(context: Context, uri: Uri): String {
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val extension = sharedVoucherExtension(uri, mimeType)
    val file = File(context.cacheDir, "shared_voucher_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: return uri.toString()
        Uri.fromFile(file).toString()
    }.getOrElse {
        uri.toString()
    }
}

private fun sharedVoucherExtension(uri: Uri, mimeType: String?): String {
    if (mimeType == "application/pdf") return "pdf"
    val mimeExtension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!mimeExtension.isNullOrBlank()) return if (mimeExtension == "jpeg") "jpg" else mimeExtension
    return voucherExtension(uri.toString())
}

private const val KEY_CHAT_WALLPAPER = "chat_wallpaper"

private fun loadChatWallpaper(context: Context): String {
    val prefs = context.getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CHAT_WALLPAPER, "llanta") ?: "llanta"
}

private fun saveChatWallpaper(context: Context, wallpaper: String) {
    val prefs = context.getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_CHAT_WALLPAPER, wallpaper).apply()
}

private fun loadPendingSharedVoucherUriStrings(context: Context): List<String> {
    val raw = context
        .getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_PENDING_SHARED_VOUCHERS, null)
        ?: return emptyList()

    return runCatching {
        val json = JSONArray(raw)
        List(json.length()) { index -> json.getString(index) }
    }.getOrDefault(emptyList())
}

private fun loadSavedReportDaysBack(context: Context): Int {
    return context
        .getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_REPORT_DAYS_BACK, 0)
        .coerceAtLeast(0)
}

private fun saveReportDaysBack(context: Context, daysBack: Int) {
    context
        .getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_REPORT_DAYS_BACK, daysBack.coerceAtLeast(0))
        .apply()
}

private fun reportSearchMessage(daysBack: Int): String {
    if (daysBack <= 0) return "Buscando depositos de hoy"
    val start = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysBack)
    }.time
    val startLabel = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(start)
    return "Buscando depositos desde $startLabel hasta hoy"
}

private fun savePendingSharedVoucherUriStrings(context: Context, values: List<String>) {
    val json = JSONArray().apply {
        values.forEach { put(it) }
    }
    context
        .getSharedPreferences(REGISTER_SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PENDING_SHARED_VOUCHERS, json.toString())
        .apply()
}

private fun voucherExtension(imageUri: String): String {
    val cleanUri = imageUri.substringBefore('?').lowercase()
    return when {
        cleanUri.endsWith(".pdf") -> "pdf"
        cleanUri.endsWith(".png") -> "png"
        cleanUri.endsWith(".jpeg") -> "jpg"
        cleanUri.endsWith(".jpg") -> "jpg"
        else -> "jpg"
    }
}

private fun String?.isPdfVoucher(): Boolean {
    return this?.substringBefore('?')?.endsWith(".pdf", ignoreCase = true) == true
}

private fun ReportStatus.spanishLabel(): String {
    return when (this) {
        ReportStatus.VALIDATED -> "Validado"
        ReportStatus.PENDING -> "Pendiente"
        ReportStatus.REJECTED -> "Rechazado"
    }
}

private fun String?.toMessageFrom(): MessageFrom {
    return when (this?.trim()?.lowercase(Locale.ROOT)) {
        "user", "mobile", "cliente", "client", "vendedor" -> MessageFrom.USER
        else -> MessageFrom.BOT
    }
}

private fun String?.toReportStatus(): ReportStatus? {
    return when (this?.trim()?.uppercase(Locale.ROOT)) {
        "PENDING", "PENDIENTE" -> ReportStatus.PENDING
        "RECEIVED", "RECIBIDO", "PROCESSING", "PROCESANDO", "EN_PROCESO", "OBSERVADO" -> ReportStatus.PENDING
        "VALIDATED", "VALIDADO", "CONFIRMED", "CONFIRMADO", "CONFIRMADO_EXITOSO" -> ReportStatus.VALIDATED
        "REJECTED", "RECHAZADO", "QUALITY_REJECTED", "ERROR_VALIDACION" -> ReportStatus.REJECTED
        else -> null
    }
}

private fun String?.toChatDate(): String {
    return toFormattedBackendDate("dd/MM/yyyy") ?: todayChatDate()
}

private fun String?.toChatTime(): String {
    return toFormattedBackendDate("HH:mm")
        ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun String?.toFormattedBackendDate(outputPattern: String): String? {
    if (isNullOrBlank()) return null
    val rawValue = this
    val inputPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    val parsedDate = inputPatterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(rawValue)
        }.getOrNull()
    } ?: return null
    return SimpleDateFormat(outputPattern, Locale.getDefault()).format(parsedDate)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TConfirmoTheme {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenChatPreview() {
    TConfirmoTheme {
        MainScreen(initialSelectedTab = 1)
    }
}
