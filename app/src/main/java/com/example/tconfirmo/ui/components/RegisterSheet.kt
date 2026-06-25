package com.example.tconfirmo.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tconfirmo.R
import coil.compose.rememberAsyncImagePainter
import com.example.tconfirmo.data.DepositDraft
import com.example.tconfirmo.ui.components.PdfPreview
import com.example.tconfirmo.ui.theme.PlusJakartaSansFamily
import com.example.tconfirmo.ui.theme.PrimaryGreen
import com.example.tconfirmo.ui.theme.TConfirmoTheme
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RegisterMode { Cart, Form, Success }
private enum class PickerTarget { Empresa, Banco }

private data class DepositCartItem(
    val id: String = UUID.randomUUID().toString(),
    val image: VoucherImage,
    val empresa: String,
    val banco: String,
    val cliente: String
)

private sealed interface VoucherImage {
    data class Camera(val bitmap: Bitmap, val uri: String) : VoucherImage
    data class Gallery(val uri: Uri) : VoucherImage
    data class Pdf(val uri: Uri) : VoucherImage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterSheet(
    visible: Boolean = true,
    initialVoucherUris: List<Uri> = emptyList(),
    initialDepositDrafts: List<DepositDraft> = emptyList(),
    onInitialVouchersConsumed: (Int) -> Unit = {},
    onInitialDepositDraftsConsumed: (Int) -> Unit = {},
    resetKey: Int = 0,
    onClose: () -> Unit,
    onSubmit: (solicitudes: List<DepositDraft>) -> Unit
) {
    var mode by remember { mutableStateOf(RegisterMode.Form) }
    var items by remember { mutableStateOf<List<DepositCartItem>>(emptyList()) }
    var draftImage by remember { mutableStateOf<VoucherImage?>(null) }
    var draftEmpresa by remember { mutableStateOf("") }
    var draftBanco by remember { mutableStateOf("") }
    var draftCliente by remember { mutableStateOf("") }
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var pendingSubmit by remember { mutableStateOf<List<DepositDraft>>(emptyList()) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var processedInitialVoucherCount by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val empresas = listOf("JCH COMERCIAL SA", "EVOLUTION CAR SERVICE")
    val bancos = listOf("BCP", "INTERBANK", "SCOTIABANK", "BBVA", "BANBIF", "PICHINCHA")
    val canAddDraft = draftImage != null && draftEmpresa.isNotBlank() && draftBanco.isNotBlank()
    val canSubmit = items.isNotEmpty() && items.all { it.isComplete() }

    fun resetDraft() {
        draftImage = null
        draftEmpresa = ""
        draftBanco = ""
        draftCliente = ""
        editingItemId = null
    }

    fun resetSession() {
        items = emptyList()
        pendingSubmit = emptyList()
        pickerTarget = null
        processedInitialVoucherCount = 0
        mode = RegisterMode.Form
        resetDraft()
    }

    fun openNewDeposit() {
        resetDraft()
        mode = RegisterMode.Form
    }

    fun openEditDeposit(item: DepositCartItem) {
        draftImage = item.image
        draftEmpresa = item.empresa
        draftBanco = item.banco
        draftCliente = item.cliente
        editingItemId = item.id
        mode = RegisterMode.Form
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            draftImage = VoucherImage.Camera(bitmap = bitmap, uri = saveBitmapToCache(context, bitmap))
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }
    fun addIncomingVoucherUris(
        uris: List<Uri>,
        empresa: String = "",
        banco: String = "",
        cliente: String = ""
    ) {
        if (uris.isEmpty()) return
        val incomingItems = uris.map { uri ->
            DepositCartItem(
                image = uri.toVoucherImage(context),
                empresa = empresa,
                banco = banco,
                cliente = cliente
            )
        }
        items = items + incomingItems
        resetDraft()
        mode = RegisterMode.Cart
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        when (uris.size) {
            0 -> Unit
            1 -> draftImage = uris.first().toVoucherImage(context)
            else -> addIncomingVoucherUris(
                uris = uris,
                empresa = draftEmpresa,
                banco = draftBanco,
                cliente = draftCliente
            )
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(Unit) {
        if (visible) sheetState.expand()
    }

    LaunchedEffect(visible) {
        if (visible) sheetState.expand()
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) resetSession()
    }

    LaunchedEffect(initialVoucherUris) {
        val newVoucherUris = initialVoucherUris.drop(processedInitialVoucherCount)
        if (newVoucherUris.isNotEmpty()) {
            addIncomingVoucherUris(newVoucherUris)
            processedInitialVoucherCount += newVoucherUris.size
            onInitialVouchersConsumed(newVoucherUris.size)
        }
    }

    LaunchedEffect(initialDepositDrafts) {
        if (initialDepositDrafts.isNotEmpty()) {
            val draftsWithVoucher = initialDepositDrafts.filter { it.imageUri.isNotBlank() }
            val incomingItems = draftsWithVoucher.map { draft ->
                DepositCartItem(
                    image = draft.imageUri.toVoucherImage(),
                    empresa = draft.empresa,
                    banco = draft.banco,
                    cliente = draft.cliente
                )
            }
            if (incomingItems.isNotEmpty()) {
                items = items + incomingItems
                mode = RegisterMode.Cart
            } else {
                val draft = initialDepositDrafts.first()
                draftImage = null
                draftEmpresa = draft.empresa
                draftBanco = draft.banco
                draftCliente = draft.cliente
                editingItemId = null
                mode = RegisterMode.Form
            }
            onInitialDepositDraftsConsumed(initialDepositDrafts.size)
        }
    }

    LaunchedEffect(mode, pendingSubmit) {
        if (mode == RegisterMode.Success && pendingSubmit.isNotEmpty()) {
            kotlinx.coroutines.delay(1200)
            onSubmit(pendingSubmit)
        }
    }

    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(Color(0xFFE7EAF4), RoundedCornerShape(99.dp))
            )
        }
    ) {
        when (mode) {
            RegisterMode.Cart -> CartContent(
                items = items,
                onClose = onClose,
                onAddAnother = ::openNewDeposit,
                onEdit = ::openEditDeposit,
                onRemove = { id -> items = items.filterNot { it.id == id } },
                onSubmit = {
                    pendingSubmit = items.map {
                        DepositDraft(
                            empresa = it.empresa,
                            banco = it.banco,
                            cliente = it.cliente,
                            imageUri = it.image.asModelString()
                        )
                    }
                    mode = RegisterMode.Success
                },
                canSubmit = canSubmit
            )

            RegisterMode.Form -> NewDepositContent(
                image = draftImage,
                empresa = draftEmpresa,
                banco = draftBanco,
                cliente = draftCliente,
                empresas = empresas,
                bancos = bancos,
                canAdd = canAddDraft,
                isEditing = editingItemId != null,
                onBack = { mode = RegisterMode.Cart },
                onCamera = ::openCamera,
                onGallery = ::openGallery,
                onRemoveImage = { draftImage = null },
                onOpenEmpresaPicker = { pickerTarget = PickerTarget.Empresa },
                onOpenBancoPicker = { pickerTarget = PickerTarget.Banco },
                onClienteChange = { draftCliente = it },
                onAdd = {
                    val image = draftImage ?: return@NewDepositContent
                    val editingId = editingItemId
                    items = if (editingId == null) {
                        items + DepositCartItem(
                            image = image,
                            empresa = draftEmpresa,
                            banco = draftBanco,
                            cliente = draftCliente
                        )
                    } else {
                        items.map {
                            if (it.id == editingId) {
                                it.copy(
                                    image = image,
                                    empresa = draftEmpresa,
                                    banco = draftBanco,
                                    cliente = draftCliente
                                )
                            } else {
                                it
                            }
                        }
                    }
                    resetDraft()
                    mode = RegisterMode.Cart
                },
                onClose = onClose
            )

            RegisterMode.Success -> SuccessContent(count = pendingSubmit.size)
        }

        pickerTarget?.let { target ->
            OptionPickerSheet(
                title = if (target == PickerTarget.Empresa) "Seleccionar Empresa" else "Seleccionar Banco",
                options = if (target == PickerTarget.Empresa) empresas else bancos,
                selected = if (target == PickerTarget.Empresa) draftEmpresa else draftBanco,
                icon = if (target == PickerTarget.Empresa) Icons.Default.Business else Icons.Default.AccountBalance,
                onDismiss = { pickerTarget = null },
                onSelected = { option ->
                    if (target == PickerTarget.Empresa) {
                        draftEmpresa = option
                    } else {
                        draftBanco = option
                    }
                    pickerTarget = null
                }
            )
        }
    }
}

@Composable
private fun CartContent(
    items: List<DepositCartItem>,
    onClose: () -> Unit,
    onAddAnother: () -> Unit,
    onEdit: (DepositCartItem) -> Unit,
    onRemove: (String) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.98f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Registro de depósitos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17265F),
                    fontFamily = PlusJakartaSansFamily
                )
                Text(
                    text = "${items.size} depósito${if (items.size == 1) "" else "s"} en el registro",
                    fontSize = 12.sp,
                    color = Color(0xFF6A7394)
                )
            }
            CircleIconButton(icon = Icons.Default.Close, onClick = onClose, contentDescription = "Cerrar")
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (items.isEmpty()) {
                EmptyCart()
            } else {
                items.forEachIndexed { index, item ->
                    DepositSummaryCard(
                        index = index,
                        item = item,
                        onEdit = { onEdit(item) },
                        onRemove = { onRemove(item.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, Color(0xFFE7EAF4)))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAddAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = PrimaryGreen
                ),
                border = BorderStroke(1.dp, PrimaryGreen)
            ) {
                Text("+  Agregar otro depósito", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSubmit) PrimaryGreen else Color(0xFFE7EAF4),
                    contentColor = if (canSubmit) Color.White else Color(0xFF6A7394)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when {
                        items.isEmpty() -> "Agrega depósitos"
                        !canSubmit -> "Completa los datos"
                        else -> "Enviar ${items.size} depósito${if (items.size > 1) "s" else ""}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun NewDepositContent(
    image: VoucherImage?,
    empresa: String,
    banco: String,
    cliente: String,
    empresas: List<String>,
    bancos: List<String>,
    canAdd: Boolean,
    isEditing: Boolean,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemoveImage: () -> Unit,
    onOpenEmpresaPicker: () -> Unit,
    onOpenBancoPicker: () -> Unit,
    onClienteChange: (String) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = 24.dp)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack, contentDescription = "Volver")
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEditing) "Modificar depósito" else "Nuevo depósito",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF17265F),
                    fontFamily = PlusJakartaSansFamily
                )
                Text(
                    if (isEditing) "Actualiza los datos del depósito" else "Completa los datos y adjunta el voucher",
                    fontSize = 11.sp,
                    color = Color(0xFF6A7394)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            CircleIconButton(icon = Icons.Default.Close, onClick = onClose, contentDescription = "Cerrar")
        }

        Spacer(modifier = Modifier.height(16.dp))
        VoucherPicker(
            image = image,
            onCamera = onCamera,
            onGallery = onGallery,
            onRemoveImage = onRemoveImage,
            imageEditable = !isEditing
        )
        Spacer(modifier = Modifier.height(14.dp))
        SelectorButton("Empresa", empresa, "Seleccionar Empresa", Icons.Default.Business, onOpenEmpresaPicker)
        Spacer(modifier = Modifier.height(12.dp))
        SelectorButton("Banco", banco, "Seleccionar Banco", Icons.Default.AccountBalance, onOpenBancoPicker)
        Spacer(modifier = Modifier.height(12.dp))
        ClientField(value = cliente, onValueChange = onClienteChange)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = canAdd,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canAdd) PrimaryGreen else Color(0xFFE7EAF4),
                contentColor = if (canAdd) Color.White else Color(0xFF6A7394)
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                if (isEditing) "Guardar cambios" else "Agregar al registro",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SuccessContent(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.98f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(40.dp),
                color = Color(0xFFFFF6B8)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (count == 1) "¡Solicitud enviada!" else "¡Solicitudes enviadas!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17265F),
                fontFamily = PlusJakartaSansFamily
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aparecerán en Chat y en Reportes.",
                fontSize = 14.sp,
                color = Color(0xFF6A7394)
            )
        }
    }
}

@Composable
private fun DepositSummaryCard(
    index: Int,
    item: DepositCartItem,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF6F7FB),
        border = BorderStroke(1.dp, Color(0xFFE7EAF4))
    ) {
        Row(modifier = Modifier.padding(0.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 82.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                VoucherImageView(item.image, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.58f)
                ) {
                    Text(
                        text = "#${(index + 1).toString().padStart(3, '0')}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    item.empresa.ifBlank { "Seleccionar empresa" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.empresa.isBlank()) Color(0xFF17265F) else Color(0xFF17265F)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.cliente.ifBlank { "Sin cliente" },
                    fontSize = 12.sp,
                    color = Color(0xFF6A7394)
                )
                Spacer(modifier = Modifier.height(6.dp))
                val hasBank = item.banco.isNotBlank()
                Surface(shape = RoundedCornerShape(8.dp), color = if (hasBank) Color(0xFFFFF6B8) else Color(0xFFFFF6B8)) {
                    Text(
                        item.banco.ifBlank { "Seleccionar banco" },
                        color = if (hasBank) Color(0xFF17265F) else Color(0xFF17265F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Color(0xFF6A7394), modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun VoucherPicker(
    image: VoucherImage?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemoveImage: () -> Unit,
    imageEditable: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(465.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE7EAF4)),
        contentAlignment = Alignment.Center
    ) {
        if (image == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.InsertPhoto, contentDescription = null, tint = Color(0xFF9EA6C4), modifier = Modifier.size(42.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sin voucher adjunto", fontSize = 13.sp, color = Color(0xFF6A7394))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallActionButton("Camara", Icons.Default.CameraAlt, onCamera, filled = true)
                    SmallActionButton("Galeria", Icons.Default.Image, onGallery, filled = false)
                }
            }
        } else {
            VoucherImageView(image, Modifier.fillMaxSize())
            if (imageEditable) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    color = Color(0xFFD64545),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    IconButton(onClick = onRemoveImage, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar voucher", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onCamera)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCart() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF6F7FB),
        border = BorderStroke(1.dp, Color(0xFFE7EAF4))
    ) {
        Text(
            text = "Aún no hay depósitos. Agrega una foto del voucher y completa sus datos.",
            color = Color(0xFF6A7394),
            fontSize = 13.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SelectorButton(
    label: String,
    value: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    if (value.isBlank()) 0.dp else 1.dp,
                    if (value.isBlank()) Color.Transparent else PrimaryGreen
                ),
                RoundedCornerShape(16.dp)
            )
            .background(Color(0xFFE7EAF4))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (value.isBlank()) Color(0xFF6A7394) else PrimaryGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value.ifBlank { placeholder },
            color = if (value.isBlank()) Color(0xFF6A7394) else Color(0xFF17265F),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color(0xFF6A7394), modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val isEmpresaPicker = title.contains("Empresa", ignoreCase = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(Color(0xFFE7EAF4), RoundedCornerShape(99.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = Color(0xFF17265F),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFamily,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(icon = Icons.Default.Close, onClick = onDismiss, contentDescription = "Cerrar")
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .background(if (isSelected) Color(0xFFFFF6B8) else Color.White)
                            .clickable { onSelected(option) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = if (isEmpresaPicker) Color.White else if (isSelected) PrimaryGreen else Color(0xFFE7EAF4),
                            border = if (isEmpresaPicker) BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PrimaryGreen else Color(0xFFE7EAF4)
                            ) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isEmpresaPicker) {
                                    Image(
                                        painter = painterResource(id = option.companyLogoRes()),
                                        contentDescription = option,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = option.take(2).uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF9EA6C4)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = option,
                            color = Color(0xFF17265F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Surface(
                                modifier = Modifier.size(22.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = PrimaryGreen
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFF6F7FB))
                    )
                }
            }
        }
    }
}

private fun String.companyLogoRes(): Int {
    return if (contains("EVOLUTION", ignoreCase = true)) {
        R.drawable.evo_logo
    } else {
        R.drawable.jch_logo
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientField(value: String, onValueChange: (String) -> Unit) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    keyboardController?.show()
                    scope.launch {
                        delay(250)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null, tint = if (value.isBlank()) Color(0xFF6A7394) else PrimaryGreen, modifier = Modifier.size(18.dp))
        },
        placeholder = { Text("Nombre del cliente", color = Color(0xFF6A7394), fontSize = 14.sp) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFE7EAF4),
            unfocusedContainerColor = Color(0xFFE7EAF4),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    filled: Boolean
) {
    val horizontalPadding = if (filled) 12.dp else 16.dp
    val verticalPadding = if (filled) 7.dp else 9.dp
    val iconSize = if (filled) 13.dp else 15.dp
    val textSize = if (filled) 11.sp else 12.sp

    Surface(
        shape = RoundedCornerShape(if (filled) 16.dp else 18.dp),
        color = if (filled) PrimaryGreen else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (filled) Color.White else Color(0xFF17265F), modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = if (filled) Color.White else Color(0xFF17265F), fontSize = textSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String
) {
    Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFE7EAF4)) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = Color(0xFF17265F), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VoucherImageView(image: VoucherImage, modifier: Modifier = Modifier) {
    when (image) {
        is VoucherImage.Camera -> Image(image.bitmap.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
        is VoucherImage.Gallery -> Image(rememberAsyncImagePainter(image.uri), null, modifier, contentScale = ContentScale.Crop)
        is VoucherImage.Pdf -> PdfPreview(
            uriString = image.uri.toString(),
            modifier = modifier,
            label = "Voucher PDF adjunto"
        )
    }
}

@Composable
private fun PdfVoucherView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFFF6F7FB)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFE2E2)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFB71C1C),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Voucher PDF adjunto", color = Color(0xFF17265F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Se registrará como documento", color = Color(0xFF6A7394), fontSize = 12.sp)
        }
    }
}

private fun VoucherImage.asModelString(): String {
    return when (this) {
        is VoucherImage.Camera -> uri
        is VoucherImage.Gallery -> uri.toString()
        is VoucherImage.Pdf -> uri.toString()
    }
}

private fun DepositCartItem.isComplete(): Boolean {
    return empresa.isNotBlank() && banco.isNotBlank()
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): String {
    val file = File(context.cacheDir, "voucher_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
    }
    return file.absolutePath
}

private fun Uri.toVoucherImage(context: Context): VoucherImage {
    val mimeType = context.contentResolver.getType(this)
    val localUri = copySharedVoucherToCache(context, this, mimeType)
    return if (isPdfVoucher(this, mimeType)) {
        VoucherImage.Pdf(localUri)
    } else {
        VoucherImage.Gallery(localUri)
    }
}

private fun String.toVoucherImage(): VoucherImage {
    val uri = Uri.parse(this)
    return if (this.substringBefore('?').endsWith(".pdf", ignoreCase = true)) {
        VoucherImage.Pdf(uri)
    } else {
        VoucherImage.Gallery(uri)
    }
}

private fun copySharedVoucherToCache(context: Context, uri: Uri, mimeType: String?): Uri {
    val extension = voucherExtension(uri, mimeType)
    val file = File(context.cacheDir, "voucher_${System.currentTimeMillis()}.$extension")
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        } ?: return uri
        Uri.fromFile(file)
    }.getOrElse { uri }
}

private fun isPdfVoucher(uri: Uri, mimeType: String?): Boolean {
    return mimeType == "application/pdf" || uri.toString().substringBefore('?').endsWith(".pdf", ignoreCase = true)
}

private fun voucherExtension(uri: Uri, mimeType: String?): String {
    if (mimeType == "application/pdf") return "pdf"
    val mimeExtension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!mimeExtension.isNullOrBlank()) return if (mimeExtension == "jpeg") "jpg" else mimeExtension
    val pathExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    return when (pathExtension.lowercase()) {
        "png" -> "png"
        "jpeg" -> "jpg"
        "jpg" -> "jpg"
        "pdf" -> "pdf"
        else -> "jpg"
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterSheetPreview() {
    TConfirmoTheme {
        RegisterSheet(
            onClose = {},
            onSubmit = {}
        )
    }
}
