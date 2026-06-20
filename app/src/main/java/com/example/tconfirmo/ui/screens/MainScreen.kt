package com.example.tconfirmo.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.sp
import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.*
import com.example.tconfirmo.ui.components.MessageBubble
import com.example.tconfirmo.ui.components.PdfPreview
import com.example.tconfirmo.ui.components.RegisterSheet
import com.example.tconfirmo.ui.theme.PrimaryGreen
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlinx.coroutines.launch

import com.example.tconfirmo.ui.theme.PlusJakartaSansFamily

private const val REGISTER_SESSION_PREFS = "tconfirmo_register_session"
private const val KEY_PENDING_SHARED_VOUCHERS = "pending_shared_vouchers"

@Composable
fun MainScreen(
    sharedVoucherUris: List<Uri> = emptyList(),
    onSharedVouchersConsumed: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showRegisterSheet by remember { mutableStateOf(false) }
    var registerSharedVoucherUriStrings by rememberSaveable {
        mutableStateOf(loadPendingSharedVoucherUriStrings(context))
    }
    var registerInitialDrafts by remember { mutableStateOf<List<DepositDraft>>(emptyList()) }
    var registerResetKey by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf(getInitialMessages()) }
    var reports by remember { mutableStateOf(getInitialReports()) }

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

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Chat") },
                    label = { Text("Chat", fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryGreen,
                        selectedTextColor = PrimaryGreen,
                        indicatorColor = Color(0xFFE8F5E9)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Reportes") },
                    label = { Text("Reportes", fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryGreen,
                        selectedTextColor = PrimaryGreen,
                        indicatorColor = Color(0xFFE8F5E9)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuracion") },
                    label = { Text("Ajustes", fontSize = 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryGreen,
                        selectedTextColor = PrimaryGreen,
                        indicatorColor = Color(0xFFE8F5E9)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ChatTab(
                    messages = messages,
                    onOpenRegister = {
                        showRegisterSheet = true
                    },
                    onSendMessage = { text ->
                        val newMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            from = MessageFrom.USER,
                            text = text,
                            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                            status = MessageStatus.SENT
                        )
                        messages = messages + newMsg
                    }
                )
                1 -> ReportsTab(
                    reports = reports,
                    onRegularize = { report ->
                        updatePendingSharedVouchers(emptyList())
                        registerInitialDrafts = listOf(
                            DepositDraft(
                                empresa = report.empresa,
                                banco = report.banco,
                                cliente = report.cliente,
                                imageUri = report.imageUrl.orEmpty()
                            )
                        )
                        registerResetKey += 1
                        showRegisterSheet = true
                    }
                )
                2 -> SettingsTab(
                    onCheckForUpdates = onCheckForUpdates,
                    onLogout = onLogout
                )
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
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                    val newMessages = solicitudes.mapIndexed { index, solicitud ->
                        val sid = "#${(reports.size + index + 1).toString().padStart(3, '0')}"
                        val voucherName = voucherFileName(sid, solicitud.imageUri)
                        val card = VoucherCard(
                            solicitudId = sid,
                            voucherName = voucherName,
                            imageUrl = solicitud.imageUri,
                            empresa = solicitud.empresa,
                            banco = solicitud.banco,
                            cliente = solicitud.cliente,
                            status = ReportStatus.PENDING
                        )
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            from = MessageFrom.USER,
                            voucherCard = card,
                            time = time,
                            status = MessageStatus.SENT
                        )
                    }
                    val newReports = solicitudes.mapIndexed { index, solicitud ->
                        val sid = "#${(reports.size + index + 1).toString().padStart(3, '0')}"
                        val voucherName = voucherFileName(sid, solicitud.imageUri)
                        Report(
                            id = UUID.randomUUID().toString(),
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

                    messages = messages + newMessages
                    reports = newReports + reports
                    showRegisterSheet = false
                    updatePendingSharedVouchers(emptyList())
                    registerInitialDrafts = emptyList()
                    registerResetKey += 1
                    selectedTab = 0
            }
        )
    }
}

@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    onOpenRegister: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var currentMatch by remember { mutableStateOf(0) }
    var openedVoucher by remember { mutableStateOf<VoucherCard?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val matches = remember(messages, searchText) {
        if (searchText.isBlank()) {
            emptyList()
        } else {
            messages.indices
                .filter { index -> messages[index].searchableText().contains(searchText, ignoreCase = true) }
                .asReversed()
        }
    }
    val activeMatchIndex = matches.getOrNull(currentMatch)

    LaunchedEffect(matches) {
        currentMatch = 0
        matches.firstOrNull()?.let { listState.animateScrollToItem(it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F4EE))) {
        // Chat Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Bot Confirmaciones", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("en linea - respuesta automatica", color = Color(0xFFD1FAE5), fontSize = 11.sp)
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
                        scope.launch { listState.animateScrollToItem(matches[nextMatch]) }
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        val nextMatch = (currentMatch - 1).coerceAtLeast(0)
                        currentMatch = nextMatch
                        scope.launch { listState.animateScrollToItem(matches[nextMatch]) }
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
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            reverseLayout = false
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { msg ->
                val index = messages.indexOf(msg)
                MessageBubble(
                    message = msg,
                    isSearchMatch = index == activeMatchIndex,
                    onVoucherClick = { openedVoucher = it }
                )
            }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(PrimaryGreen, CircleShape)
                        .clickable(onClick = onOpenRegister),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Registrar voucher", tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Mensaje", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F1F1),
                        unfocusedContainerColor = Color(0xFFF1F1F1),
                        disabledContainerColor = Color(0xFFF1F1F1),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                if (textInput.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onSendMessage(textInput)
                            textInput = ""
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(PrimaryGreen, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                    focusedContainerColor = Color(0xFFF1F1F1),
                    unfocusedContainerColor = Color(0xFFF1F1F1),
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
            color = Color(0xFF11100E),
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
                            modifier = Modifier.fillMaxSize(),
                            label = "PDF adjunto"
                        )
                    } else {
                        coil.compose.AsyncImage(
                            model = voucher.imageUrl,
                            contentDescription = "Voucher ${voucher.solicitudId}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
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
    onRegularize: (Report) -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    var currentPage by remember { mutableStateOf(0) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    val context = LocalContext.current
    val filteredReports = when (filter) {
        "pending" -> reports.filter { it.status == ReportStatus.PENDING }
        "validated" -> reports.filter { it.status == ReportStatus.VALIDATED }
        "rejected" -> reports.filter { it.status == ReportStatus.REJECTED }
        else -> reports
    }
    val reportsPageSize = 4
    val totalPages = ((filteredReports.size + reportsPageSize - 1) / reportsPageSize).coerceAtLeast(1)
    val pagedReports = filteredReports
        .drop(currentPage * reportsPageSize)
        .take(reportsPageSize)

    LaunchedEffect(filter, reports) {
        currentPage = 0
    }

    LaunchedEffect(filteredReports.size, totalPages) {
        if (currentPage > totalPages - 1) {
            currentPage = totalPages - 1
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F4EE))) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(16.dp)
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
                    Text("${reports.size} solicitudes registradas", color = Color(0xFFD1FAE5), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { exportReportsForExcel(context, reports) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Filters
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = pagedReports,
                key = { it.id }
            ) { report ->
                ReportItem(
                    report = report,
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
            onNext = { currentPage = (currentPage + 1).coerceAtMost(totalPages - 1) }
        )

        selectedReport?.let { report ->
            ReportDetailSheet(
                report = report,
                onClose = { selectedReport = null }
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
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                    enabled = currentPage < totalPages - 1,
                    iconFirst = false,
                    contentDescription = "Pagina siguiente"
                )
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
    val background = if (enabled) PrimaryGreen else Color(0xFFF7F5F1)
    val contentColor = if (enabled) Color.White else Color(0xFFD9D3CA)

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
        color = if (selected) PrimaryGreen else Color(0xFFECE7DF),
        shape = CircleShape,
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${page + 1}",
                color = if (selected) Color.White else Color(0xFF8D877D),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
    onClick: () -> Unit,
    onRegularize: () -> Unit
) {
    val statusColor = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF065F46)
        ReportStatus.REJECTED -> Color(0xFF991B1B)
        ReportStatus.PENDING -> Color(0xFF92400E)
    }
    val statusBg = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFFD1FAE5)
        ReportStatus.REJECTED -> Color(0xFFFEE2E2)
        ReportStatus.PENDING -> Color(0xFFFEF3C7)
    }
    val cardBg = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFFF0FDF4)
        ReportStatus.REJECTED -> Color(0xFFFFF1F2)
        ReportStatus.PENDING -> Color(0xFFFFFBEB)
    }
    val cardBorder = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF86EFAC)
        ReportStatus.REJECTED -> Color(0xFFFCA5A5)
        ReportStatus.PENDING -> Color(0xFFFCD34D)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = report.solicitudNum, fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 12.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (report.status == ReportStatus.VALIDATED) {
                        Text(report.fecha, color = Color(0xFF6B6258), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(report.hora, color = Color(0xFF6B6258), fontSize = 10.sp)
                    }
                    Surface(color = statusBg, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = report.status.spanishLabel(),
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (report.status == ReportStatus.REJECTED) {
                        Surface(
                            color = PrimaryGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable(onClick = onRegularize)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regularizar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = report.empresa, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF252321))
            Text(text = report.cliente, color = Color(0xFF6B6258), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = cardBorder.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF6B6258), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = report.banco, color = Color(0xFF6B6258), fontSize = 12.sp)
                if (report.status == ReportStatus.VALIDATED) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Anexo: ${report.anexo ?: "RECAU MN"}", color = Color(0xFF252321), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importe: ${report.importe ?: "-"}", color = Color(0xFF252321), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Op: ${report.operacion ?: "-"}", color = Color(0xFF252321), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = report.hora, color = Color(0xFF6B6258), fontSize = 10.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDetailSheet(report: Report, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    val statusColor = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFF065F46)
        ReportStatus.REJECTED -> Color(0xFF991B1B)
        ReportStatus.PENDING -> Color(0xFF92400E)
    }
    val statusBg = when (report.status) {
        ReportStatus.VALIDATED -> Color(0xFFD1FAE5)
        ReportStatus.REJECTED -> Color(0xFFFEE2E2)
        ReportStatus.PENDING -> Color(0xFFFEF3C7)
    }
    val statusLabel = report.status.spanishLabel()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
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
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
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

            if (report.status == ReportStatus.REJECTED && !report.mensajeValidacion.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("Motivo del rechazo", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF3F3),
                    border = BorderStroke(1.dp, Color(0xFFFFCDD2))
                ) {
                    Text(
                        text = report.mensajeValidacion,
                        color = Color(0xFFB71C1C),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ReportVoucherSection(report)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportVoucherSection(report: Report) {
    val imageUrl = report.imageUrl

    Text("Voucher adjunto", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE6E0D8))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )

                else -> coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = "Voucher ${report.solicitudNum}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .background(Color(0xFFF7F0E8)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1EEE9))
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
                    tint = Color.Gray,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(report.voucherName ?: "Voucher", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PdfReportPreview(uriString: String, modifier: Modifier = Modifier) {
    PdfPreview(uriString = uriString, modifier = modifier, label = "Documento PDF adjunto")
}

@Composable
private fun MissingVoucherPreview(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFF7F0E8)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = Color(0xFF81776E),
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sin voucher adjunto", color = Color(0xFF4C463F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            bg = Color(0xFFE8F5E9)
            icon = Icons.Default.CheckCircle
            title = "DEPOSITO CONFIRMADO"
            subtitle = "Validado por el sistema"
            color = Color(0xFF1B5E20)
        }
        ReportStatus.REJECTED -> {
            bg = Color(0xFFFFF3F3)
            icon = Icons.Default.Error
            title = "DEPOSITO RECHAZADO"
            subtitle = "Requiere correccion"
            color = Color(0xFFB71C1C)
        }
        ReportStatus.PENDING -> {
            bg = Color(0xFFFFF8E1)
            icon = Icons.Default.Schedule
            title = "EN PROCESO"
            subtitle = "Pendiente de validacion por el sistema"
            color = Color(0xFF795900)
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFFF7F4EE)) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.first, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(112.dp))
                    Text(
                        row.second,
                        color = Color(0xFF1C1B1F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = Color(0xFFE6E0D8))
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

private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

@Composable
fun SettingsTab(
    onCheckForUpdates: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F4EE))) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryGreen)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    "Configuracion",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = PlusJakartaSansFamily
                )
                Text("Perfil y preferences", color = Color(0xFFD1FAE5), fontSize = 12.sp)
            }
        }

        // Profile Card
        Card(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = PrimaryGreen) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("JP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Juan Perez Garcia", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Personal de Caja", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                SettingsRow(Icons.Default.Store, "Tienda", "PIURA AV. SANCHEZ CERRO 1222")
                SettingsRow(Icons.Default.Phone, "Celular", "987654321")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("CUENTA", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column {
                SettingsActionRow(
                    Icons.Default.Key,
                    "Cambiar contrasena",
                    "Actualiza tu acceso",
                    onClick = { showPasswordDialog = true }
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
        Text("APLICACION", modifier = Modifier.padding(horizontal = 24.dp), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(modifier = Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column {
                SettingsRow(Icons.Default.Info, "Version", BuildConfig.VERSION_NAME)
                HorizontalDivider(color = Color(0xFFF1F1F1))
                SettingsActionRow(
                    Icons.Default.SystemUpdate,
                    "Actualizar version",
                    "Buscar nueva version en GitHub",
                    onClick = onCheckForUpdates
                )
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(onDismiss = { showPasswordDialog = false })
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
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(60.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
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
            color = if (isDestructive) Color(0xFFFFF3F3) else Color(0xFFE8F5E9)
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
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSave = currentPassword.isNotBlank() && newPassword.length >= 6 && newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Cambiar contrasena",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFamily
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ingresa tu contrasena actual y define una nueva.",
                    color = Color.Gray,
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
                        newPassword.length < 6 -> "La nueva contrasena debe tener al menos 6 caracteres."
                        newPassword != confirmPassword -> "Las contrasenas nuevas no coinciden."
                        else -> null
                    }
                    if (errorMessage == null) onDismiss()
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
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
    TextField(
        value = value,
        onValueChange = onValueChange,
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
            focusedContainerColor = Color(0xFFF7F4EE),
            unfocusedContainerColor = Color(0xFFF7F4EE),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

fun getInitialMessages(): List<ChatMessage> {
    return listOf(
        ChatMessage(
            id = "m1", from = MessageFrom.BOT,
            text = "👋 Hola Juan. Estoy listo para recibir tus depositos. Usa el boton de camara 📷 para registrar un voucher.",
            time = "08:00"
        ),
        ChatMessage(
            id = "m2", from = MessageFrom.USER,
            voucherCard = VoucherCard(
                solicitudId = "#001", voucherName = "Voucher_001.jpg",
                imageUrl = "https://images.unsplash.com/photo-1554224155-6726b3ff858f?w=280&h=180&fit=crop&auto=format",
                empresa = "JCH COMERCIAL SA", banco = "BCP", cliente = "Carlos Mendoza", status = ReportStatus.VALIDATED
            ),
            time = "14:22",
            status = MessageStatus.READ
        ),
        ChatMessage(
            id = "m3", from = MessageFrom.BOT,
            structuredData = StructuredBotData(
                type = BotMessageType.CONFIRMATION,
                title = "DEPOSITO CONFIRMADO",
                rows = listOf(
                    "Empresa" to "JCH COMERCIAL SA",
                    "Sucursal" to "PIURA AV. SANCHEZ CERRO 1222",
                    "Banco" to "BCP",
                    "Anexo" to "RECAU MN",
                    "Fecha" to "17/06/2026",
                    "Operacion" to "2545539",
                    "Importe" to "PEN 800.00"
                ),
                footer = "✅ Validado y registrado exitosamente."
            ),
            time = "14:23"
        ),
        ChatMessage(
            id = "m4", from = MessageFrom.USER,
            voucherCard = VoucherCard(
                solicitudId = "#002", voucherName = "Voucher_002.jpg",
                imageUrl = "https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=280&h=180&fit=crop&auto=format",
                empresa = "EVOLUTION CAR SERVICE", banco = "INTERBANK", cliente = "Diana Flores", status = ReportStatus.PENDING
            ),
            time = "15:10",
            status = MessageStatus.READ
        ),
        ChatMessage(
            id = "m5", from = MessageFrom.BOT,
            text = "⏳ Solicitud #002 recibida. En proceso de validacion.",
            time = "15:11"
        )
    )
}

fun getInitialReports(): List<Report> {
    return listOf(
        Report(
            id = "r1", solicitudNum = "#001",
            empresa = "JCH COMERCIAL SA", cliente = "Carlos Mendoza", banco = "BCP",
            fecha = "17/06/2026", hora = "14:22", status = ReportStatus.VALIDATED,
            importe = "PEN 800.00", operacion = "2545539", sucursal = "PIURA AV. SANCHEZ CERRO 1222"
        ),
        Report(
            id = "r2", solicitudNum = "#002",
            empresa = "EVOLUTION CAR SERVICE", cliente = "Diana Flores", banco = "INTERBANK",
            fecha = "17/06/2026", hora = "15:10", status = ReportStatus.PENDING,
            sucursal = "PIURA AV. SANCHEZ CERRO 1222"
        ),
        Report(
            id = "r3", solicitudNum = "#003",
            empresa = "JCH COMERCIAL SA", cliente = "Pedro Ruiz", banco = "SCOTIABANK",
            fecha = "16/06/2026", hora = "09:45", status = ReportStatus.REJECTED,
            mensajeValidacion = "Voucher ilegible. Reenvie con mejor calidad de imagen.",
            sucursal = "PIURA AV. SANCHEZ CERRO 1222"
        )
    )
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

private fun voucherFileName(solicitudId: String, imageUri: String): String {
    val extension = voucherExtension(imageUri)
    return "Voucher_${solicitudId.replace("#", "")}.$extension"
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




