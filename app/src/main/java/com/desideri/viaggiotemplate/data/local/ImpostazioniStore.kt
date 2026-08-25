package com.desideri.viaggiotemplate.data.local

import android.content.Context

/** Persiste le preferenze dell'app (es. il calendario di destinazione) in SharedPreferences. */
class ImpostazioniStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    var calendarioSelezionatoId: Long?
        get() = prefs.getLong(CHIAVE_CALENDARIO_ID, -1L).takeIf { it != -1L }
        set(value) {
            if (value == null) {
                prefs.edit().remove(CHIAVE_CALENDARIO_ID).apply()
            } else {
                prefs.edit().putLong(CHIAVE_CALENDARIO_ID, value).apply()
            }
        }

    private companion object {
        const val CHIAVE_CALENDARIO_ID = "calendario_selezionato_id"
    }
}
