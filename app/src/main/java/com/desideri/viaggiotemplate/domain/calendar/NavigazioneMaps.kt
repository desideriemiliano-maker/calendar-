package com.desideri.viaggiotemplate.domain.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Marcatore "(GPS: lat, lng)" appeso da [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter]
 * al nome del luogo quando l'EVENT_LOCATION è costruito da coordinate GPS invece che da un indirizzo
 * testuale (vedi [com.desideri.viaggiotemplate.domain.location.formattaCoordinateGps]). Il prefisso
 * "GPS:" (invece di un generico "(lat, lng)") è deliberato: rende la corrispondenza specifica
 * abbastanza da non confondersi con un nome di luogo che contenga per conto suo dei numeri tra
 * parentesi, e da restare innocua anche se l'utente modifica a mano la posizione dell'evento
 * direttamente in Google Calendar (semplicemente non c'è più match, niente da estrarre).
 */
private val PATTERN_COORDINATE_IN_LOCATION = Regex("""\(GPS:\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*\)\s*$""")

/**
 * Estrae "lat,lng" da [destinazione] se termina col marcatore scritto da CalendarWriter, solo se i
 * due numeri catturati sono coordinate plausibili (lat in -90..90, lng in -180..180). Ritorna null
 * — mai un'eccezione — sia quando il marcatore non c'è (testo libero, o location modificata a mano
 * nel calendario) sia quando i numeri sono fuori range: in entrambi i casi il chiamante deve
 * degradare passando l'intera stringa come destinazione testuale invece di navigare su coordinate
 * potenzialmente sbagliate.
 */
private fun estraiCoordinateNavigazione(destinazione: String): String? {
    val match = PATTERN_COORDINATE_IN_LOCATION.find(destinazione) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return "$lat,$lng"
}

/**
 * Avvia la navigazione Google Maps in auto dalla posizione attuale verso [destinazione]: prova prima
 * l'intent nativo dell'app Maps (`google.navigation:`, apre direttamente in modalità
 * turn-by-turn), e se l'app non è installata ricade sul link web di Maps (apre nel browser o in
 * un'altra app che gestisce link Maps, con la sola preview del percorso).
 *
 * Se [destinazione] termina col marcatore di coordinate scritto da CalendarWriter, naviga verso
 * quelle invece che verso il testo per intero (più preciso di una ricerca testuale); altrimenti
 * (testo libero, o location modificata a mano nel calendario) usa [destinazione] così com'è.
 */
fun avviaNavigazioneAuto(context: Context, destinazione: String) {
    val query = estraiCoordinateNavigazione(destinazione) ?: destinazione
    val intentNativo = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(query)}&mode=d"))
    try {
        context.startActivity(intentNativo)
    } catch (_: ActivityNotFoundException) {
        val uriWeb = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(query)}&travelmode=driving"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, uriWeb))
    }
}

/**
 * Apre Google Maps sul punto [lat]/[lng] (senza avviare la navigazione), con [etichetta] come nome
 * del pin quando fornita. Prova prima l'intent nativo `geo:`, e se nessuna app lo gestisce ricade
 * sul link web di Maps.
 */
fun apriPosizioneSuMaps(context: Context, lat: Double, lng: Double, etichetta: String? = null) {
    val query = if (etichetta.isNullOrBlank()) "$lat,$lng" else "$lat,$lng(${etichetta})"
    val intentNativo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=${Uri.encode(query)}"))
    try {
        context.startActivity(intentNativo)
    } catch (_: ActivityNotFoundException) {
        val uriWeb = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        context.startActivity(Intent(Intent.ACTION_VIEW, uriWeb))
    }
}
