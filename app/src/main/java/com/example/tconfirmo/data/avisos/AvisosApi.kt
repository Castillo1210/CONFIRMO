package com.example.tconfirmo.data.avisos

import retrofit2.Response
import retrofit2.http.GET

interface AvisosApi {
    // GET /api/v1/avisos/mios — filtrado server-side por el rol del usuario
    // autenticado (Confirmo.Api, Endpoints/AvisoEndpoints.cs). Solo trae avisos
    // con enviarApp=true, activos, y que ya se hayan despachado al menos una
    // vez (UltimaEjecucion != null) para el rol de este usuario.
    @GET("api/v1/avisos/mios")
    suspend fun getMisAvisos(): Response<List<AvisoResponseDto>>
}
