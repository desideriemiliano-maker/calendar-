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

    /** Fattore di zoom (pinch-to-zoom) applicato a tutta la UI, vedi [com.desideri.viaggiotemplate.ui.common.ZoomableRoot]. 1f = nessuno zoom. */
    var fattoreZoomUi: Float
        get() = prefs.getFloat(CHIAVE_FATTORE_ZOOM, 1f)
        set(value) = prefs.edit().putFloat(CHIAVE_FATTORE_ZOOM, value).apply()

    /** True se il gesto di pinch-to-zoom è attivo: disattivato di default (voce nel menu principale). */
    var pinchZoomAbilitato: Boolean
        get() = prefs.getBoolean(CHIAVE_PINCH_ZOOM_ABILITATO, false)
        set(value) = prefs.edit().putBoolean(CHIAVE_PINCH_ZOOM_ABILITATO, value).apply()

    private companion object {
        const val CHIAVE_CALENDARIO_ID = "calendario_selezionato_id"
        const val CHIAVE_FATTORE_ZOOM = "fattore_zoom_ui"
        const val CHIAVE_PINCH_ZOOM_ABILITATO = "pinch_zoom_abilitato"
    }
}
