package com.example.tconfirmo.data.auth

import com.example.tconfirmo.BuildConfig
import com.example.tconfirmo.data.session.SessionManager
import com.google.gson.Gson
import java.io.IOException

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {
    private val gson = Gson()

    suspend fun login(phoneNumber: String, password: String, useTestMode: Boolean = BuildConfig.USE_MOCK_LOGIN): AuthResult {
        if (useTestMode) {
            return loginWithMockData(phoneNumber, password)
        }

        return try {
            val response = authApi.login(
                LoginRequestDto(
                    phoneNumber = phoneNumber.toPeruPhoneNumber(),
                    password = password
                )
            )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body == null) {
                        AuthResult.Error("La API no devolvio datos de sesion.")
                    } else {
                        sessionManager.saveSession(body, isTestMode = false)
                        AuthResult.Success
                    }
                }
                response.code() == 401 -> AuthResult.Error("Numero o contrasena incorrectos.")
                response.code() in 500..599 -> AuthResult.Error("Servicio no disponible. Intenta nuevamente.")
                else -> AuthResult.Error("No se pudo iniciar sesion. Intenta nuevamente.")
            }
        } catch (_: IOException) {
            AuthResult.Error("Error de conexion. Revisa tu internet e intenta nuevamente.")
        } catch (error: Exception) {
            AuthResult.Error("No se pudo iniciar sesion. Intenta nuevamente.")
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResult {
        return try {
            val response = authApi.changePassword(
                ChangePasswordRequestDto(
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
            )

            when {
                response.isSuccessful && response.body()?.success == true -> AuthResult.Success
                response.code() == 400 -> AuthResult.Error(response.changePasswordErrorMessage("No se pudo cambiar la contrasena."))
                response.code() == 401 -> AuthResult.Error("Tu sesion vencio. Inicia sesion nuevamente.")
                response.code() in 500..599 -> AuthResult.Error("Servicio no disponible. Intenta nuevamente.")
                else -> AuthResult.Error(response.changePasswordErrorMessage("No se pudo cambiar la contrasena."))
            }
        } catch (_: IOException) {
            AuthResult.Error("Error de conexion. Revisa tu internet e intenta nuevamente.")
        } catch (_: Exception) {
            AuthResult.Error("No se pudo cambiar la contrasena. Intenta nuevamente.")
        }
    }

    private fun retrofit2.Response<ChangePasswordResponseDto>.changePasswordErrorMessage(defaultMessage: String): String {
        body()?.message?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            gson.fromJson(errorBody()?.string(), ChangePasswordResponseDto::class.java)
                ?.message
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: defaultMessage
    }

    private fun loginWithMockData(phoneNumber: String, password: String): AuthResult {
        val normalizedPhone = phoneNumber.trim()
        if (normalizedPhone != MOCK_PHONE || password != MOCK_PASSWORD) {
            return AuthResult.Error("Numero o contrasena incorrectos.")
        }

        sessionManager.saveSession(
            LoginResponseDto(
                accessToken = "mock-access-token",
                refreshToken = "mock-refresh-token",
                expiresInSeconds = 8 * 60 * 60,
                user = UserInfoDto(
                    id = "00000000-0000-0000-0000-000000000001",
                    phoneNumber = normalizedPhone,
                    fullName = "Usuario de Prueba",
                    empresaId = "00000000-0000-0000-0000-000000000101",
                    sucursalId = "00000000-0000-0000-0000-000000000201",
                    fcmToken = null
                )
            ),
            isTestMode = true
        )
        return AuthResult.Success
    }

    companion object {
        const val MOCK_PHONE = "987654321"
        const val MOCK_PASSWORD = "clave"
    }
}

private fun String.toPeruPhoneNumber(): String {
    val digits = filter { it.isDigit() }
    return when {
        digits.startsWith("51") -> digits
        digits.length == 9 -> "51$digits"
        else -> digits
    }
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}
