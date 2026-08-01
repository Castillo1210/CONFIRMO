package com.example.tconfirmo.data.avisos

import com.example.tconfirmo.data.remote.ApiClient

// Mismo patron defensivo que ChatRepository/DepositRepository: nunca lanza,
// devuelve lista vacia ante cualquier falla de red o respuesta no exitosa.
class AvisosRepository(
    private val avisosApi: AvisosApi = ApiClient.avisosApi
) {
    suspend fun getMisAvisos(): List<AvisoResponseDto> {
        val response = runCatching { avisosApi.getMisAvisos() }.getOrNull()
            ?: return emptyList()
        return if (response.isSuccessful) response.body().orEmpty() else emptyList()
    }
}
