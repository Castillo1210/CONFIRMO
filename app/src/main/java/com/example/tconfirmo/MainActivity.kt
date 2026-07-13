package com.example.tconfirmo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tconfirmo.data.auth.AuthRepository
import com.example.tconfirmo.data.fcm.FcmTokenProvider
import com.example.tconfirmo.data.offline.FirebaseOfflineClient
import com.example.tconfirmo.data.realtime.RealtimeClient
import com.example.tconfirmo.data.remote.ApiClient
import com.example.tconfirmo.data.session.SessionManager
import com.example.tconfirmo.ui.screens.LoginScreen
import com.example.tconfirmo.ui.screens.MainScreen
import com.example.tconfirmo.ui.theme.TConfirmoTheme
import com.example.tconfirmo.updates.AppUpdateManager
import com.example.tconfirmo.updates.RemoteUpdate
import com.example.tconfirmo.updates.UpdateUi
import com.example.tconfirmo.updates.UpdateUiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var sharedVoucherUris by mutableStateOf<List<Uri>>(emptyList())
    private var isLoggedIn by mutableStateOf(false)
    private var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var sessionManager: SessionManager
    private lateinit var realtimeClient: RealtimeClient
    private lateinit var fcmTokenProvider: FcmTokenProvider

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseOfflineClient.initialize(applicationContext)
        appUpdateManager = AppUpdateManager(applicationContext)
        sessionManager = SessionManager(applicationContext)
        ApiClient.initialize(sessionManager)
        realtimeClient = RealtimeClient(
            sessionManager = sessionManager,
            refreshToken = {
                AuthRepository(
                    authApi = ApiClient.authApi,
                    sessionManager = sessionManager
                ).refreshToken()
            }
        )
        fcmTokenProvider = FcmTokenProvider(applicationContext)
        isLoggedIn = sessionManager.isLoggedIn()
        if (isLoggedIn) {
            requestNotificationPermissionIfNeeded()
            lifecycleScope.launch { connectRealtimeAndRegisterFcm() }
        }
        sharedVoucherUris = intent.extractSharedVoucherUris()
        setContent {
            TConfirmoTheme {
                AppNavigation(
                    isLoggedIn = isLoggedIn,
                    realtimeClient = realtimeClient,
                    sharedVoucherUris = sharedVoucherUris,
                    onSharedVouchersConsumed = { sharedVoucherUris = emptyList() },
                    onLoginSuccess = {
                        isLoggedIn = true
                        requestNotificationPermissionIfNeeded()
                        lifecycleScope.launch { connectRealtimeAndRegisterFcm() }
                    },
                    onLogout = {
                        isLoggedIn = false
                        sessionManager.clearSession()
                        lifecycleScope.launch { realtimeClient.disconnect() }
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

    override fun onDestroy() {
        lifecycleScope.launch { realtimeClient.disconnect() }
        super.onDestroy()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private suspend fun connectRealtimeAndRegisterFcm() {
        realtimeClient.connect()
        if (sessionManager.isTestMode()) return
        val token = fcmTokenProvider.getCurrentToken()
        if (!token.isNullOrBlank()) {
            AuthRepository(
                authApi = ApiClient.authApi,
                sessionManager = sessionManager
            ).updateFcmToken(token)
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
    realtimeClient: RealtimeClient? = null,
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
                    realtimeClient = realtimeClient,
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
