package com.example.tconfirmo

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Handler de crashes propio. En algunos celulares (confirmado con un Honor)
// "adb logcat" no devuelve NADA para procesos de apps de terceros -- el
// fabricante restringe el acceso a los logs a nivel de sistema, incluso con
// el dispositivo en modo debug y adb correctamente conectado. Sin log real,
// no hay forma de diagnosticar un crash a distancia.
//
// Esto evita depender de logcat por completo: instala un
// UncaughtExceptionHandler propio que, ANTES de que el proceso muera, guarda
// el stack trace completo en un archivo dentro del almacenamiento privado de
// la app (filesDir -- no requiere permisos ni acceso a logs del sistema).
// MainActivity lo lee al abrir y lo muestra en un dialogo con boton
// "Copiar", para poder sacar el error exacto sin adb ni Android Studio.
object CrashHandler {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stringWriter = StringWriter()
                throwable.printStackTrace(PrintWriter(stringWriter))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val content = "Crash el $timestamp (hilo: ${thread.name})\n\n$stringWriter"
                File(appContext.filesDir, FILE_NAME).writeText(content)
            }
            // Deja que el manejador original siga su curso (el sistema
            // muestra "la app dejo de funcionar" y cierra el proceso como
            // siempre) -- esto solo GUARDA el error antes, no cambia en nada
            // el comportamiento real del crash.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clearLastCrash(context: Context) {
        runCatching {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        }
    }
}
