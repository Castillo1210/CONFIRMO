package com.example.tconfirmo.data.auth

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    val phoneNumber: String,
    val password: String,
    val fcmToken: String? = null,
    // Id propio de esta instalacion (no cambia entre logins). El backend lo
    // usa para bloquear el login del mismo vendedor desde un segundo celular
    // mientras el primero sigue con sesion activa.
    val deviceId: String? = null
)

data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val user: UserInfoDto
)

data class UserInfoDto(
    @SerializedName(value = "id", alternate = ["Id"])
    val id: String,
    @SerializedName(value = "phoneNumber", alternate = ["PhoneNumber"])
    val phoneNumber: String,
    @SerializedName(value = "fullName", alternate = ["FullName"])
    val fullName: String,
    @SerializedName(value = "empresaId", alternate = ["EmpresaId"])
    val empresaId: String,
    @SerializedName(value = "sucursalId", alternate = ["SucursalId", "branchId", "BranchId"])
    val sucursalId: String?,
    @SerializedName(
        value = "sucursalNombre",
        alternate = ["SucursalNombre", "nombreSucursal", "NombreSucursal", "branchName", "BranchName"]
    )
    val sucursalNombre: String? = null,
    @SerializedName(value = "sucursal", alternate = ["Sucursal"])
    val sucursal: Any? = null,
    @SerializedName(value = "fcmToken", alternate = ["FcmToken"])
    val fcmToken: String?
)

fun UserInfoDto.sucursalDisplayName(): String? {
    val directName = sucursalNombre?.takeIf { it.isNotBlank() }
    if (directName != null) return directName

    return when (val value = sucursal) {
        is String -> value.takeIf { it.isNotBlank() }
        is Map<*, *> -> listOf("nombre", "Nombre", "name", "Name", "codigo", "Codigo")
            .firstNotNullOfOrNull { key -> value[key]?.toString()?.takeIf { it.isNotBlank() } }
        else -> null
    }
}

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

data class FcmTokenRequestDto(
    val token: String
)
