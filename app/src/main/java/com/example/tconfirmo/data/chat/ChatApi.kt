package com.example.tconfirmo.data.chat

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("api/v1/deposits/{depositId}/chat/")
    suspend fun getChatHistory(
        @Path("depositId") depositId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<ChatHistoryResponseDto>

    @POST("api/v1/deposits/{depositId}/chat/")
    suspend fun sendMessage(
        @Path("depositId") depositId: String,
        @Body request: SendUserMessageRequestDto
    ): Response<Unit>
}
