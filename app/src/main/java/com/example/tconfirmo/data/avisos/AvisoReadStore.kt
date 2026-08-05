package com.example.tconfirmo.data.avisos

import android.content.Context

// Guarda localmente (SharedPreferences) los IDs de avisos que el usuario ya
// abrio. Decision explicita: no se trackea en el backend (no hace falta
// sincronizar "leido" entre dispositivos ni que el admin sepa quien lo leyo,
// ver conversacion) -- si mas adelante hiciera falta, esto se puede migrar a
// un endpoint sin tocar el resto de NoticesTab. Se marca leido recien cuando
// se abre el detalle del aviso, no con que aparezca en la lista (mismo
// criterio que Gmail/WhatsApp).
class AvisoReadStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReadIds(): Set<String> =
        prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()

    fun markRead(avisoId: String) {
        // SharedPreferences.getStringSet() devuelve la instancia interna --
        // el doc de Android advierte no mutarla directo, por eso se arma un
        // set nuevo antes de guardar.
        val current = getReadIds()
        if (avisoId in current) return
        prefs.edit().putStringSet(KEY_READ_IDS, current + avisoId).apply()
    }

    companion object {
        private const val PREFS_NAME = "tconfirmo_avisos_leidos"
        private const val KEY_READ_IDS = "read_ids"
    }
}
