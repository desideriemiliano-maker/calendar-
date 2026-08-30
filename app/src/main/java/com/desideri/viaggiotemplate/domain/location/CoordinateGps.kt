package com.desideri.viaggiotemplate.domain.location

import java.util.Locale

/** Range valido per una latitudine WGS84. */
val RANGE_LATITUDINE = -90.0..90.0

/** Range valido per una longitudine WGS84. */
val RANGE_LONGITUDINE = -180.0..180.0

/**
 * Interpreta in modo tollerante una stringa di coordinate incollata da Google Maps o digitata a
 * mano, accettando sia il punto sia la virgola come separatore decimale (alcuni telefoni/locale
 * usano la virgola). Non valida i range: quello è compito del chiamante, per poter distinguere
 * "testo illeggibile" da "numeri fuori range" nel messaggio d'errore mostrato all'utente.
 *
 * La virgola è intrinsecamente ambigua: separa sia i due numeri (lat, lng) sia le cifre
 * decimali. La si risolve spezzando il testo su virgola, punto e virgola o spazio
 * indifferentemente (uno qualunque di questi è sempre un separatore esplicito valido) e contando i
 * gruppi ottenuti:
 * - esattamente 2 gruppi → nessuna virgola era decimale (es. "45.4642, 9.1900" o "45, 9"): i due
 *   gruppi si interpretano così come sono, con il punto come decimale;
 * - esattamente 4 gruppi → ogni coordinata era spezzata a metà da una virgola decimale (es.
 *   "45,4642, 9,1900" oppure con `;` o spazio al posto della virgola di separazione): si
 *   ricompone unendo 1°+2° gruppo per la latitudine e 3°+4° per la longitudine;
 * - qualunque altro numero di gruppi (1, 3, 5+) resta ambiguo o incompleto — niente da indovinare,
 *   si ritorna null perché il chiamante mostri un errore invece di salvare coordinate sbagliate.
 */
fun parseCoordinateGps(testo: String): Pair<Double, Double>? {
    val pulito = testo.trim()
    if (pulito.isEmpty()) return null
    val gruppi = pulito.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
    return when (gruppi.size) {
        2 -> {
            val lat = gruppi[0].toDoubleOrNull() ?: return null
            val lng = gruppi[1].toDoubleOrNull() ?: return null
            lat to lng
        }
        4 -> {
            val lat = "${gruppi[0]}.${gruppi[1]}".toDoubleOrNull() ?: return null
            val lng = "${gruppi[2]}.${gruppi[3]}".toDoubleOrNull() ?: return null
            lat to lng
        }
        else -> null
    }
}

/**
 * Formatta una coordinata con al più 6 decimali (~11 cm di precisione), senza zeri finali
 * superflui. Locale.ROOT è deliberato: il testo prodotto finisce nel marcatore "(GPS: lat, lng)"
 * dell'EVENT_LOCATION, ri-analizzato da un punto decimale letterale — su un dispositivo con
 * lingua italiana (o altra locale che usa la virgola come separatore decimale) "%.6f".format
 * senza locale esplicita produrrebbe "45,464200" invece di "45.464200", rompendo quel parsing.
 */
fun formattaCoordinata(valore: Double): String {
    val testo = "%.6f".format(Locale.ROOT, valore).trimEnd('0').trimEnd('.')
    return testo.ifEmpty { "0" }
}

/** Formatta una coppia di coordinate nel formato "lat, lng" usato sia nel campo di modifica sia nell'EVENT_LOCATION del calendario. */
fun formattaCoordinateGps(lat: Double, lng: Double): String = "${formattaCoordinata(lat)}, ${formattaCoordinata(lng)}"
