package com.example.tconfirmo.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// Antes esto SOLO sabia abrir PDFs locales (content://, file://, o un path
// plano) via ContentResolver/ParcelFileDescriptor -- no tenia forma de leer
// una URL remota. Para un PDF ya subido (voucher de un deposito real), lo
// unico que se le pasaba era la referencia de GCS o (con el fix anterior) la
// URL del endpoint redirect, y ninguna de las dos es algo que
// ContentResolver/ParcelFileDescriptor sepan abrir directo -- por eso el
// preview de PDF nunca cargaba para vouchers ya persistidos, con o sin firma.
//
// Ahora, cuando se pasa "depositId" (deposito ya persistido), primero se
// descarga el PDF (siguiendo el mismo endpoint redirect que ya usan las
// imagenes, GET /api/v1/deposits/{id}/image) a un archivo temporal en cache,
// y de ahi en adelante se renderiza igual que un PDF local. Cuando no hay
// depositId (ej. RegisterSheet mostrando un PDF recien elegido en el celular,
// todavia no subido), se sigue usando uriString tal cual, sin descargar nada.
@Composable
fun PdfPreview(
    uriString: String,
    depositId: String? = null,
    modifier: Modifier = Modifier,
    label: String = "PDF adjunto"
) {
    val context = LocalContext.current
    var preview by remember(uriString, depositId) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(uriString, depositId) { mutableStateOf(false) }

    LaunchedEffect(uriString, depositId) {
        preview = null
        isLoading = true
        preview = withContext(Dispatchers.IO) {
            if (!depositId.isNullOrBlank()) {
                val localFile = downloadVoucherPdfToCache(context, depositId)
                localFile?.let { renderFirstPdfPage(context, it.absolutePath) }
            } else {
                renderFirstPdfPage(context, uriString)
            }
        }
        isLoading = false
    }

    Box(
        modifier = modifier.background(Color(0xFFF6F7FB)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = preview
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
            isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFB71C1C), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Cargando PDF...", color = Color(0xFF17265F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFB71C1C),
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(label, color = Color(0xFF17265F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Cliente minimo, separado del de ApiClient: el endpoint redirect ya lleva el
// token en la propia URL (?access_token=...), no necesita el interceptor que
// agrega el header Authorization, y sigue el 302 hacia GCS por defecto.
private val voucherDownloadClient by lazy {
    OkHttpClient.Builder().followRedirects(true).build()
}

private fun downloadVoucherPdfToCache(context: Context, depositId: String): File? {
    return runCatching {
        val accessToken = SessionManager(context).getAccessToken()
        if (accessToken.isNullOrBlank()) return null

        val url = "${BuildConfig.API_BASE_URL}api/v1/deposits/$depositId/image?access_token=${Uri.encode(accessToken)}"
        val request = Request.Builder().url(url).build()

        voucherDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val file = File(context.cacheDir, "voucher_preview_$depositId.pdf")
            file.outputStream().use { output -> body.byteStream().copyTo(output) }
            file
        }
    }.getOrNull()
}

private fun renderFirstPdfPage(context: Context, uriString: String): Bitmap? {
    val descriptor = openPdfDescriptor(context, uriString) ?: return null
    // PdfRenderer lanza IOException si el archivo no es un PDF valido (por
    // ejemplo, si la descarga fallo y lo que quedo en cache es una pagina de
    // error en vez del PDF real). Antes esto no estaba protegido: la
    // excepcion se propagaba sin capturar dentro de la corrutina de
    // LaunchedEffect y tumbaba la app entera al intentar abrir el PDF.
    return runCatching {
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    // Acota el bitmap a un tamano razonable: algunas paginas de PDF
                    // tienen una resolucion nativa enorme, y crear un
                    // ARGB_8888 sin acotar puede agotar la memoria si varias
                    // tarjetas de voucher entran en composicion a la vez (ej.
                    // scroll rapido del chat) -- mismo riesgo que ya se evita
                    // en las fotos de camara con compressVoucherFile.
                    val maxDimension = 1600
                    val largestSide = maxOf(page.width, page.height).coerceAtLeast(1)
                    val scale = (maxDimension.toFloat() / largestSide.toFloat()).coerceAtMost(1f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(AndroidColor.WHITE)
                    val matrix = android.graphics.Matrix().apply { setScale(scale, scale) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()
}

private fun openPdfDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
    val uri = Uri.parse(uriString)
    return runCatching {
        when (uri.scheme) {
            "content", "file" -> context.contentResolver.openFileDescriptor(uri, "r")
            null, "" -> ParcelFileDescriptor.open(File(uriString), ParcelFileDescriptor.MODE_READ_ONLY)
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
    }.getOrNull()
}
