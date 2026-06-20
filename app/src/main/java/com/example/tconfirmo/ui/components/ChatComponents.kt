package com.example.tconfirmo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.ui.text.font.FontWeight
import com.example.tconfirmo.data.BotMessageType
import com.example.tconfirmo.data.MessageStatus
import com.example.tconfirmo.data.StructuredBotData
import com.example.tconfirmo.ui.theme.DestructiveRed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tconfirmo.data.ChatMessage
import com.example.tconfirmo.data.MessageFrom
import com.example.tconfirmo.data.ReportStatus
import com.example.tconfirmo.data.VoucherCard
import com.example.tconfirmo.ui.theme.PrimaryDarkGreen
import com.example.tconfirmo.ui.theme.PrimaryGreen

@Composable
fun VoucherCardBubble(card: VoucherCard, onClick: () -> Unit) {
    val statusColor = when (card.status) {
        ReportStatus.VALIDATED -> Color(0xFF065F46)
        ReportStatus.REJECTED -> Color(0xFF991B1B)
        ReportStatus.PENDING -> Color(0xFF92400E)
    }
    val statusBg = when (card.status) {
        ReportStatus.VALIDATED -> Color(0xFFD1FAE5)
        ReportStatus.REJECTED -> Color(0xFFFEE2E2)
        ReportStatus.PENDING -> Color(0xFFFEF3C7)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                if (card.imageUrl.isPdfVoucher()) {
                    PdfVoucherPreview(
                        uriString = card.imageUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )
                } else {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .background(Color(0xFFF7F0E8)),
                        contentScale = ContentScale.Fit
                    )
                }
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (card.imageUrl.isPdfVoucher()) Icons.Default.PictureAsPdf else Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = card.voucherName,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.empresa.split(" ")[0],
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = card.status.spanishLabel(),
                            color = statusColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = card.cliente, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = card.banco, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun PdfVoucherPreview(uriString: String, modifier: Modifier = Modifier) {
    PdfPreview(uriString = uriString, modifier = modifier, label = "PDF adjunto")
}

private fun String.isPdfVoucher(): Boolean {
    return substringBefore('?').endsWith(".pdf", ignoreCase = true)
}

private fun ReportStatus.spanishLabel(): String {
    return when (this) {
        ReportStatus.VALIDATED -> "Validado"
        ReportStatus.PENDING -> "Pendiente"
        ReportStatus.REJECTED -> "Rechazado"
    }
}

@Composable
fun StructuredBotBubble(data: StructuredBotData, time: String) {
    val isConfirm = data.type == BotMessageType.CONFIRMATION
    val headerBg = if (isConfirm) {
        Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF0C7753)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C)))
    }
    val footerBg = if (isConfirm) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
    val footerTextColor = if (isConfirm) Color(0xFF065F46) else Color(0xFF991B1B)

    Card(
        modifier = Modifier
            .width(280.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConfirm) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Body (Rows)
            Column(modifier = Modifier.background(Color.White)) {
                data.rows.forEachIndexed { index, pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = pair.first,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            text = pair.second,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pair.first == "Importe") footerTextColor else Color.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (index < data.rows.size - 1) {
                        HorizontalDivider(color = Color(0xFFF1F1F1), thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.footer,
                        color = footerTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        lineHeight = 14.sp
                    )
                    Text(
                        text = time,
                        color = Color.Gray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isSearchMatch: Boolean = false,
    onVoucherClick: (VoucherCard) -> Unit = {}
) {
    val isUser = message.from == MessageFrom.USER
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 20.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isSearchMatch) Color(0xFFFFF3B0) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .padding(if (isSearchMatch) 4.dp else 0.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        when {
            message.voucherCard != null -> {
                VoucherCardBubble(
                    card = message.voucherCard,
                    onClick = { onVoucherClick(message.voucherCard) }
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = message.time, color = Color.Gray, fontSize = 10.sp)
                    if (isUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (message.status == MessageStatus.READ) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            tint = if (message.status == MessageStatus.READ) Color(0xFF2196F3) else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            message.structuredData != null -> {
                StructuredBotBubble(data = message.structuredData, time = message.time)
            }
            else -> {
                Surface(
                    color = if (isUser) PrimaryGreen else Color.White,
                    shape = bubbleShape,
                    shadowElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = message.text ?: "",
                            color = if (isUser) Color.White else Color.Black,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.time,
                                color = if (isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                fontSize = 10.sp
                            )
                            if (isUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (message.status == MessageStatus.READ) Icons.Default.DoneAll else Icons.Default.Done,
                                    contentDescription = null,
                                    tint = if (message.status == MessageStatus.READ) Color(0xFF90CAF9) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


