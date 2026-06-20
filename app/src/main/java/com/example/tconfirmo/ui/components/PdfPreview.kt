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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfPreview(
    uriString: String,
    modifier: Modifier = Modifier,
    label: String = "PDF adjunto"
) {
    val context = LocalContext.current
    var preview by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        preview = withContext(Dispatchers.IO) {
            renderFirstPdfPage(context, uriString)
        }
    }

    Box(
        modifier = modifier.background(Color(0xFFF7F0E8)),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFB71C1C),
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(label, color = Color(0xFF4C463F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun renderFirstPdfPage(context: Context, uriString: String): Bitmap? {
    val descriptor = openPdfDescriptor(context, uriString) ?: return null
    return descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount == 0) return@use null
            renderer.openPage(0).use { page ->
                val width = page.width.coerceAtLeast(1)
                val height = page.height.coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
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
