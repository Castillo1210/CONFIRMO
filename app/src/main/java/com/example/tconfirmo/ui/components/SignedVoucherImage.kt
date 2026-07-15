package com.example.tconfirmo.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.session.SessionManager
import com.example.tconfirmo.ui.theme.PrimaryDarkGreen
import com.example.tconfirmo.ui.theme.PrimaryGreen

// Componente compartido para mostrar el voucher de un deposito ya persistido
// (detalle de reporte en MainScreen o burbuja de chat en ChatComponents).
//
// Antes esto llamaba a un endpoint (GET vouchers/{objectName}, via
// SignedVoucherApi/SignedVoucherRepository) para pedir una URL firmada de GCS
// en formato JSON. Se descubrio que ese endpoint NUNCA existio del lado de
// api-bridge (no hay ninguna ruta registrada para el) -- por eso el voucher
// jamas cargaba, ni en Reportes ni en el chat, mas alla del bug de N+1.
//
// Ahora se apunta directo a GET /api/v1/deposits/{depositId}/image, un
// endpoint "redirect": el backend genera una firma de GCS fresca en cada
// visita y responde con un 302 hacia ella. Coil (via OkHttp) sigue ese
// redirect solo, sin ningun paso intermedio en la app -- y como la firma se
// genera al momento de cada visita, la URL nunca "expira" del lado del
// cliente (nunca se guarda una firma vieja, solo esta URL estable con el
// depositId).
//
// IMPORTANTE: se usa SubcomposeAsyncImage (no Image + rememberAsyncImagePainter
// a secas). rememberAsyncImagePainter, sin un tamano de destino explicito,
// puede terminar decodificando la imagen a su resolucion ORIGINAL completa en
// vez de al tamano real en pantalla -- con varias tarjetas de voucher
// entrando en composicion a la vez durante un scroll rapido del chat, eso
// puede agotar la memoria y tumbar la app. SubcomposeAsyncImage mide el
// tamano real del composable antes de pedirle la imagen a Coil, asi que Coil
// la decodifica ya reducida a ese tamano.
@Composable
fun SignedVoucherImage(
    depositId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = remember(depositId) {
        val accessToken = SessionManager(context).getAccessToken()
        if (depositId.isNullOrBlank() || accessToken.isNullOrBlank()) {
            null
        } else {
            // El JWT va como query param (?access_token=...) porque un <img>
            // no puede mandar el header Authorization -- el middleware de
            // api-bridge ya acepta el token asi (mismo mecanismo que usa
            // SignalR para las conexiones de tiempo real).
            "${BuildConfig.API_BASE_URL}api/v1/deposits/$depositId/image?access_token=${Uri.encode(accessToken)}"
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            VoucherLoadErrorPreview("No se pudo identificar el voucher.")
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = PrimaryGreen,
                        strokeWidth = 2.dp
                    )
                },
                error = {
                    VoucherLoadErrorPreview("No se pudo leer el archivo del voucher.")
                },
                success = {
                    SubcomposeAsyncImageContent()
                }
            )
        }
    }
}

@Composable
fun VoucherLoadErrorPreview(message: String = "No se pudo cargar el voucher") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(18.dp)
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = Color(0xFFB71C1C),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No se pudo cargar el voucher",
            color = PrimaryDarkGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            message,
            color = Color(0xFF6A7394),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}
