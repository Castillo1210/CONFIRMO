package com.example.tconfirmo.data.vouchers

import com.google.gson.annotations.SerializedName

data class SignedVoucherUrlResponseDto(
    @SerializedName(value = "signedUrl", alternate = ["url", "signed_url"])
    val signedUrl: String?,
    val expiresAt: String? = null
)
