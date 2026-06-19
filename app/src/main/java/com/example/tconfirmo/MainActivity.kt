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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tconfirmo.ui.screens.LoginScreen
import com.example.tconfirmo.ui.screens.MainScreen
import com.example.tconfirmo.ui.theme.TConfirmoTheme

private const val SESSION_PREFS = "tconfirmo_session"
private const val KEY_LOGGED_IN = "logged_in"

class MainActivity : ComponentActivity() {
    private var sharedVoucherUris by mutableStateOf<List<Uri>>(emptyList())
    private var isLoggedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedVoucherUris = intent.extractSharedVoucherUris()
    }

    private fun setSessionLoggedIn(value: Boolean) {
        getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, value)
            .apply()
    }
}

@Composable
fun AppNavigation(
    isLoggedIn: Boolean = false,
    sharedVoucherUris: List<Uri> = emptyList(),
    onSharedVouchersConsumed: () -> Unit = {},
    onLoginSuccess: () -> Unit = {},
    onLogout: () -> Unit = {}
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
