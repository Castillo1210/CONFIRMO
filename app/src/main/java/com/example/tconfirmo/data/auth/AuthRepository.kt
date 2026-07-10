package com.example.tconfirmo.data.auth

import com.example.tconfirmo.data.session.SessionManager
import com.google.gson.Gson
import java.io.IOException

class AuthRepository(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {
    private val gson = Gson()

    suspend fun login(
        phoneNumber: String,
        password: String,
        fcmToken: String? = null
    ): AuthResult {
        return try {
            val response = authApi.login(
                LoginRequestDto(
                    phoneNumber = phoneNumber.toPeruPhoneNumber(),
                    password = password,
                    fcmToken = fcmToken
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

    /**
     * Intenta renovar el accessToken usando el refreshToken almacenado en sesión.
     * @return true si la renovación fue exitosa y el nuevo token ya está en SessionManager;
     *         false si el refreshToken también expiró (se debe forzar logout).
     */
    suspend fun refreshToken(): Boolean {
        val storedRefreshToken = sessionManager.getRefreshToken() ?: return false
        return try {
            val response = authApi.refresh(RefreshRequestDto(storedRefreshToken))
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                sessionManager.updateAccessToken(body.accessToken, body.expiresInSeconds)
                true
            } else {
                // 401 aquí significa que el refreshToken también expiró.
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateFcmToken(token: String): Boolean {
        if (token.isBlank()) return false
        return runCatching {
            val response = authApi.updateFcmToken(FcmTokenRequestDto(token = token))
            response.isSuccessful
        }.getOrDefault(false)
    }

    private fun retrofit2.Response<ChangePasswordResponseDto>.changePasswordErrorMessage(defaultMessage: String): String {
        body()?.message?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            gson.fromJson(errorBody()?.string(), ChangePasswordResponseDto::class.java)
                ?.message
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: defaultMessage
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
