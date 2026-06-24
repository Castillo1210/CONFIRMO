package com.example.tconfirmo.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.tconfirmo.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RemoteUpdate(
    val versionCode: Int,
    val versionName: String,
    val updatedAt: String,
    val apkUrl: String,
    val releaseNotes: String,
    val required: Boolean
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val update: RemoteUpdate) : UpdateUiState
    data object NoUpdate : UpdateUiState
    data class Downloading(val update: RemoteUpdate, val progress: Float) : UpdateUiState
    data class Installing(val update: RemoteUpdate) : UpdateUiState
    data class Error(val message: String, val update: RemoteUpdate? = null) : UpdateUiState
}

class AppUpdateManager(private val context: Context) {
    suspend fun checkForUpdate(): RemoteUpdate? = withContext(Dispatchers.IO) {
        val metadataUrl = BuildConfig.UPDATE_METADATA_URL
        if (metadataUrl.contains("OWNER/REPO")) return@withContext null

        val json = URL(metadataUrl).readTextWithTimeout()
        val update = JSONObject(json).toRemoteUpdate()
        update.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    suspend fun downloadApk(
        update: RemoteUpdate,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "confirmo-${update.versionName}.apk")

        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("No se pudo descargar el archivo de actualizacion.")
            }
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        totalBytes?.let { onProgress((copiedBytes.toFloat() / it).coerceIn(0f, 1f)) }
                        read = input.read(buffer)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        apkFile
    }

    fun installApk(activity: Activity, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(settingsIntent)
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
        return true
    }

    private fun URL.readTextWithTimeout(): String {
        val connection = (openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("No se pudo consultar la informacion de actualizacion.")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toRemoteUpdate(): RemoteUpdate {
        return RemoteUpdate(
            versionCode = getInt("versionCode"),
            versionName = getString("versionName"),
            updatedAt = optString("updatedAt"),
            apkUrl = getString("apkUrl"),
            releaseNotes = optString("releaseNotes"),
            required = optBoolean("required", false)
        )
    }
}
