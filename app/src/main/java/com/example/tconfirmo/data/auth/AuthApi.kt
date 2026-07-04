package com.example.tconfirmo.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): Response<RefreshResponseDto>

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): Response<ChangePasswordResponseDto>

    @PUT("api/v1/auth/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequestDto): Response<Unit>
}
