package com.example.tconfirmo.data.avisos

import com.google.gson.annotations.SerializedName

// Espejo de AvisoResponse (Confirmo.Api, Models/DTOs/AvisoDtos.cs). El backend
// ya devuelve todo en camelCase, pero se anota @SerializedName explicito en
// cada campo para seguir la convencion del resto de DTOs del proyecto
// (ver DepositDtos.kt) y evitar sorpresas si algun dia cambia el serializador.
data class AvisoResponseDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("titulo")
    val titulo: String,
    @SerializedName("mensajeTexto")
    val mensajeTexto: String,
    @SerializedName("mediaUrl")
    val mediaUrl: String?,
    @SerializedName("tipoMedia")
    val tipoMedia: String?,
    @SerializedName("rolesDestino")
    val rolesDestino: List<String>?,
    @SerializedName("enviarApp")
    val enviarApp: Boolean,
    @SerializedName("enviarWhatsapp")
    val enviarWhatsapp: Boolean,
    @SerializedName("enviarEmail")
    val enviarEmail: Boolean,
    @SerializedName("asuntoEmail")
    val asuntoEmail: String?,
    @SerializedName("esRecurrente")
    val esRecurrente: Boolean,
    @SerializedName("frecuencia")
    val frecuencia: String?,
    @SerializedName("proximaEjecucion")
    val proximaEjecucion: String?,
    @SerializedName("ultimaEjecucion")
    val ultimaEjecucion: String?,
    @SerializedName("estado")
    val estado: String,
    @SerializedName("activo")
    val activo: Boolean,
    @SerializedName("creadoPorNombre")
    val creadoPorNombre: String?,
    @SerializedName("createdAt")
    val createdAt: String
)
