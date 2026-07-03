package com.example.tconfirmo.data.auth

data class LoginRequestDto(
    val phoneNumber: String,
    val password: String
)

data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val user: UserInfoDto
)

data class UserInfoDto(
    val id: String,
    val phoneNumber: String,
    val fullName: String,
    val empresaId: String,
    val sucursalId: String?,
    val fcmToken: String?
)

data class RefreshRequestDto(
    val refreshToken: String
)

data class RefreshResponseDto(
    val accessToken: String,
    val expiresInSeconds: Int
)

data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

data class ChangePasswordResponseDto(
    val success: Boolean,
    val message: String
)
