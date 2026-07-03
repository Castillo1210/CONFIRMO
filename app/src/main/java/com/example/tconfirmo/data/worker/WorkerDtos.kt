package com.example.tconfirmo.data.worker

data class WorkerResultDto(
    val depositId: String,
    val status: String,
    val errorIds: List<String>,
    val warningIds: List<String>,
    val errorType: String?,
    val errorMessage: String?
)

data class ProcessDepositMessageDto(
    val depositId: String,
    val objectName: String,
    val bancoId: String?,
    val empresaId: String,
    val cliente: String?,
    val retryCount: Int = 0
)
