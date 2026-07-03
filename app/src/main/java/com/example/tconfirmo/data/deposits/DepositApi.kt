package com.example.tconfirmo.data.deposits

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface DepositApi {
    @GET("api/v1/deposits")
    suspend fun getDeposits(
        @Query("cliente") cliente: String? = null,
        @Query("montoMin") montoMin: Double? = null,
        @Query("montoMax") montoMax: Double? = null,
        @Query("estado") estado: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<DepositListPagedResponseDto>

    @GET("api/v1/deposits/{id}")
    suspend fun getDeposit(@Path("id") id: String): Response<DepositResponseDto>

    @GET("api/v1/deposits/bancos")
    suspend fun getBanks(): Response<List<BancoResponseDto>>

    @Multipart
    @POST("api/v1/deposits/")
    suspend fun createDeposit(
        @Part("Cliente") cliente: RequestBody?,
        @Part("EmpresaId") empresaId: RequestBody?,
        @Part("BancoId") bancoId: RequestBody?,
        @Part("ImagenBase64") imagenBase64: RequestBody
    ): Response<DepositCreateResponseDto>
}
