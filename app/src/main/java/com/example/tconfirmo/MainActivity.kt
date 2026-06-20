package com.example.tconfirmo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tconfirmo.ui.screens.LoginScreen
import com.example.tconfirmo.ui.screens.MainScreen
import com.example.tconfirmo.ui.theme.TConfirmoTheme
import com.example.tconfirmo.updates.AppUpdateManager
import com.example.tconfirmo.updates.RemoteUpdate
import com.example.tconfirmo.updates.UpdateUi
import com.example.tconfirmo.updates.UpdateUiState
import kotlinx.coroutines.launch

private const val SESSION_PREFS = "tconfirmo_session"
private const val KEY_LOGGED_IN = "logged_in"

class MainActivity : ComponentActivity() {
    private var sharedVoucherUris by mutableStateOf<List<Uri>>(emptyList())
    private var isLoggedIn by mutableStateOf(false)
    private var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
    private lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appUpdateManager = AppUpdateManager(applicationContext)
        isLoggedIn = getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOGGED_IN, false)
        sharedVoucherUris = intent.extractSharedVoucherUris()
        setContent {
            TConfirmoTheme {
                AppNavigation(
                    isLoggedIn = isLoggedIn,
                    sharedVoucherUris = sharedVoucherUris,
                    onSharedVouchersConsumed = { sharedVoucherUris = emptyList() },
                    onLoginSuccess = {
                        isLoggedIn = true
                        setSessionLoggedIn(true)
                    },
                    onLogout = {
                        isLoggedIn = false
                        setSessionLoggedIn(false)
                    },
                    onCheckForUpdates = { checkForUpdates(showNoUpdate = true) }
                )
                UpdateUi(
                    state = updateState,
                    onUpdate = ::downloadAndInstallUpdate,
                    onDismiss = { updateState = UpdateUiState.Idle }
                )
            }
        }
        checkForUpdates(showNoUpdate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedVoucherUris = sharedVoucherUris + intent.extractSharedVoucherUris()
    }

    private fun setSessionLoggedIn(value: Boolean) {
        getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, value)
            .apply()
    }

    private fun checkForUpdates(showNoUpdate: Boolean) {
        lifecycleScope.launch {
            updateState = UpdateUiState.Checking
            runCatching { appUpdateManager.checkForUpdate() }
                .onSuccess { update ->
                    updateState = when {
                        update != null -> UpdateUiState.Available(update)
                        showNoUpdate -> UpdateUiState.NoUpdate
                        else -> UpdateUiState.Idle
                    }
                }
                .onFailure {
                    updateState = if (showNoUpdate) {
                        UpdateUiState.Error(it.message ?: "No se pudo consultar GitHub.")
                    } else {
                        UpdateUiState.Idle
                    }
                }
        }
    }

    private fun downloadAndInstallUpdate(update: RemoteUpdate) {
        lifecycleScope.launch {
            updateState = UpdateUiState.Downloading(update, 0f)
            runCatching {
                appUpdateManager.downloadApk(update) { progress ->
                    updateState = UpdateUiState.Downloading(update, progress)
                }
            }.onSuccess { apkFile ->
                updateState = UpdateUiState.Installing(update)
                val installerOpened = appUpdateManager.installApk(this@MainActivity, apkFile)
                if (!installerOpened) {
                    updateState = UpdateUiState.Error(
                        message = "Activa el permiso para instalar aplicaciones desde Confirmo y vuelve a presionar Actualizar.",
                        update = update
                    )
                }
            }.onFailure { error ->
                updateState = UpdateUiState.Error(
                    message = error.message ?: "Error desconocido al descargar la actualizacion.",
                    update = update
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    isLoggedIn: Boolean = false,
    sharedVoucherUris: List<Uri> = emptyList(),
    onSharedVouchersConsumed: () -> Unit = {},
    onLoginSuccess: () -> Unit = {},
    onLogout: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startDestination = if (isLoggedIn) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(onLoginSuccess = {
                onLoginSuccess()
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("main") {
                MainScreen(
                    sharedVoucherUris = sharedVoucherUris,
                    onSharedVouchersConsumed = onSharedVouchersConsumed,
                    onCheckForUpdates = onCheckForUpdates,
                    onLogout = {
                    onLogout()
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun Intent.extractSharedVoucherUris(): List<Uri> {
    if (!isSupportedVoucherType()) return emptyList()
    return when (action) {
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> getAllSharedUris()
        else -> emptyList()
    }
}

private fun Intent.isSupportedVoucherType(): Boolean {
    val mimeType = type.orEmpty()
    return mimeType.startsWith("image/") || mimeType == "application/pdf"
}

private fun Intent.getStreamUri(): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

private fun Intent.getStreamUris(): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
}

private fun Intent.getAllSharedUris(): List<Uri> {
    val uris = buildList {
        getStreamUri()?.let(::add)
        addAll(getStreamUris())
        val clips = clipData
        if (clips != null) {
            for (index in 0 until clips.itemCount) {
                clips.getItemAt(index)?.uri?.let(::add)
            }
        }
    }
    return uris.distinctBy { it.toString() }
}
