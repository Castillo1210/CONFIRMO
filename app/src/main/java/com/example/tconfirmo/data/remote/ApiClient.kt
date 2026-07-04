package com.example.tconfirmo.data.remote

import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.auth.AuthApi
import com.example.tconfirmo.data.chat.ChatApi
import com.example.tconfirmo.data.deposits.DepositApi
import com.example.tconfirmo.data.session.SessionManager
import com.example.tconfirmo.data.vouchers.SignedVoucherApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var sessionManager: SessionManager? = null

    fun initialize(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }

    private val authInterceptor = Interceptor { chain ->
        val token = sessionManager?.getAccessToken()
        val path = chain.request().url.encodedPath
        val request = if (token.isNullOrBlank() || path == "/api/v1/auth/login") {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(request)
    }

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
