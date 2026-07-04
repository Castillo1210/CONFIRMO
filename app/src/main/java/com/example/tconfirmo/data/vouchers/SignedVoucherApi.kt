package com.example.tconfirmo.data.vouchers

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SignedVoucherApi {
    @GET("vouchers/{voucherObjectName}")
    suspend fun getSignedVoucherUrl(
        @Path(value = "voucherObjectName", encoded = true) voucherObjectName: String
    ): Response<SignedVoucherUrlResponseDto>
}
