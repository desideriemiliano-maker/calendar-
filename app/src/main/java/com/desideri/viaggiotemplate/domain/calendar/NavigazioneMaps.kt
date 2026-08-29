package com.desideri.viaggiotemplate.domain.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Avvia la navigazione Google Maps in auto dalla posizione attuale verso [indirizzo]: prova prima
 * l'intent nativo dell'app Maps (`google.navigation:`, apre direttamente in modalità
 * turn-by-turn), e se l'app non è installata ricade sul link web di Maps (apre nel browser o in
 * un'altra app che gestisce link Maps, con la sola preview del percorso).
 */
fun avviaNavigazioneAuto(context: Context, indirizzo: String) {
    val intentNativo = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(indirizzo)}&mode=d"))
    try {
        context.startActivity(intentNativo)
    } catch (_: ActivityNotFoundException) {
        val uriWeb = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(indirizzo)}&travelmode=driving"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, uriWeb))
    }
}
