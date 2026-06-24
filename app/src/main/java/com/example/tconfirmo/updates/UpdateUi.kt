package com.example.tconfirmo.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.ui.theme.PrimaryGreen

@Composable
fun UpdateUi(
    state: UpdateUiState,
    onUpdate: (RemoteUpdate) -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Available -> UpdateAvailableDialog(
            update = state.update,
            onUpdate = { onUpdate(state.update) },
            onDismiss = onDismiss
        )

        is UpdateUiState.Downloading -> UpdateWaitingScreen(
            title = "Actualizando Confirmo",
            message = "Descargando version ${state.update.versionName}. No cierres la aplicacion.",
            progress = state.progress
        )

        is UpdateUiState.Installing -> UpdateWaitingScreen(
            title = "Preparando instalacion",
            message = "Se abrira el instalador de Android para completar la actualizacion.",
            progress = null
        )

        is UpdateUiState.Error -> UpdateErrorDialog(
            message = state.message,
            update = state.update,
            onRetry = { update -> onUpdate(update) },
            onDismiss = onDismiss
        )

        UpdateUiState.NoUpdate -> NoUpdateDialog(onDismiss = onDismiss)

        UpdateUiState.Checking,
        UpdateUiState.Idle -> Unit
    }
}

@Composable
private fun UpdateAvailableDialog(
    update: RemoteUpdate,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!update.required) onDismiss() },
        title = { Text("Actualizacion disponible") },
        text = {
            Column {
                Text("Version instalada: ${BuildConfig.VERSION_NAME}")
                Text("Nueva version: ${update.versionName}")
                if (update.updatedAt.isNotBlank()) {
                    Text("Fecha: ${update.updatedAt}")
                }
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(update.releaseNotes, fontSize = 13.sp, color = Color(0xFF344171))
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            if (!update.required) {
                TextButton(onClick = onDismiss) {
                    Text("Despues")
                }
            }
        }
    )
}

@Composable
private fun NoUpdateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmo esta actualizado") },
        text = { Text("Ya tienes instalada la ultima version disponible.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Aceptar")
            }
        }
    )
}

@Composable
private fun UpdateWaitingScreen(
    title: String,
    message: String,
    progress: Float?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = PrimaryGreen)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF17265F)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = Color(0xFF344171),
                    fontSize = 13.sp
                )
                if (progress != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryGreen,
                        trackColor = Color(0xFFE7EAF4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    update: RemoteUpdate?,
    onRetry: (RemoteUpdate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No se pudo actualizar") },
        text = { Text(message) },
        confirmButton = {
            if (update != null) {
                Button(onClick = { onRetry(update) }) {
                    Text("Reintentar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
