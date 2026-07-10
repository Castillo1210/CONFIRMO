package com.example.tconfirmo.data.remote

import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.auth.AuthApi
import com.example.tconfirmo.data.auth.RefreshRequestDto
import com.example.tconfirmo.data.auth.RefreshResponseDto
import com.example.tconfirmo.data.chat.ChatApi
import com.example.tconfirmo.data.deposits.DepositApi
import com.example.tconfirmo.data.session.SessionManager
import com.example.tconfirmo.data.vouchers.SignedVoucherApi
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

// ── Interfaz síncrona (Call<>) usada únicamente por TokenAuthenticator. ────────
// Vive al nivel de archivo (no dentro de ApiClient) para que TokenAuthenticator
// la pueda referenciar sin problemas de visibilidad.
// OkHttp llama a authenticate() en un hilo de IO — blocking aquí es correcto.
private interface AuthRefreshApi {
    @POST("api/v1/auth/refresh")
    fun refresh(@Body request: RefreshRequestDto): Call<RefreshResponseDto>
}

object ApiClient {
    private var sessionManager: SessionManager? = null

    fun initialize(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }

    // ── Interceptor: adjunta Bearer token a cada request (excepto login/refresh) ─
    private val authInterceptor = Interceptor { chain ->
        val token = sessionManager?.getAccessToken()
        val path = chain.request().url.encodedPath
        val skipAuth = token.isNullOrBlank()
            || path == "/api/v1/auth/login"
            || path == "/api/v1/auth/refresh"
        val request = if (skipAuth) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(request)
    }

    // ── Cliente mínimo SIN autenticador, usado solo para el refresh ──────────────
    // Evita bucles infinitos: el Authenticator principal no se dispara cuando
    // este cliente llama a /auth/refresh.
    private val refreshHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authRefreshApi: AuthRefreshApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(refreshHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthRefreshApi::class.java)

    // ── Authenticator: se invoca automáticamente cuando cualquier REST recibe 401 ─
    private val tokenAuthenticator = TokenAuthenticator(
        getSessionManager = { sessionManager },
        authRefreshApi = authRefreshApi
    )

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val depositApi: DepositApi = retrofit.create(DepositApi::class.java)
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
    val signedVoucherApi: SignedVoucherApi = retrofit.create(SignedVoucherApi::class.java)
}

/**
 * OkHttp Authenticator que renueva el JWT automáticamente cuando cualquier
 * request REST recibe HTTP 401.
 *
 * Garantías:
 *  - Usa [ReentrantLock] para que múltiples requests concurrentes que fallen a la
 *    vez sólo hagan UNA llamada al endpoint de refresh.
 *  - Limita los reintentos a 1 para evitar bucles infinitos.
 *  - Si otro hilo ya renovó el token antes, reusa el token nuevo sin volver a llamar refresh.
 */
private class TokenAuthenticator(
    private val getSessionManager: () -> SessionManager?,
    private val authRefreshApi: AuthRefreshApi
) : Authenticator {

    private val lock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Si ya reintentamos una vez, no seguir — evita bucle infinito.
        if (retryCount(response) >= 2) return null

        lock.lock()
        try {
            val sm = getSessionManager() ?: return null

            // Otro hilo puede haber renovado el token mientras esperábamos el lock.
            // Si el token en SessionManager ya es distinto al que falló, úsalo directamente.
            val latestToken = sm.getAccessToken()
            val failedToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")?.trim()
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            // Realizar el refresh de forma síncrona (estamos en hilo IO de OkHttp).
            val refreshToken = sm.getRefreshToken() ?: return null
            val refreshResponse = authRefreshApi
                .refresh(RefreshRequestDto(refreshToken))
                .execute()

            return if (refreshResponse.isSuccessful) {
                val body = refreshResponse.body() ?: return null
                sm.updateAccessToken(body.accessToken, body.expiresInSeconds)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${body.accessToken}")
                    .build()
            } else {
                // refreshToken también expiró — propagar el 401 al caller.
                null
            }
        } finally {
            lock.unlock()
        }
    }

    /** Cuenta cuántos reintentos previos hubo en esta cadena de respuestas. */
    private fun retryCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) { count++; prior = prior.priorResponse }
        return count
    }
}
