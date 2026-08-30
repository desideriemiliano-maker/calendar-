package com.desideri.viaggiotemplate.domain.location

import java.util.Locale

/** Range valido per una latitudine WGS84. */
val RANGE_LATITUDINE = -90.0..90.0

/** Range valido per una longitudine WGS84. */
val RANGE_LONGITUDINE = -180.0..180.0

/**
 * Interpreta in modo tollerante una stringa di coordinate incollata da Google Maps (es.
 * "45.4642, 9.1900", ma anche senza spazio o con `;`/spazio come separatore). Non valida i range:
 * quello è compito del chiamante, per poter distinguere "testo illeggibile" da "numeri fuori
 * range" nel messaggio d'errore mostrato all'utente. Ritorna null se il testo non contiene
 * esattamente due numeri.
 */
fun parseCoordinateGps(testo: String): Pair<Double, Double>? {
    val pulito = testo.trim()
    if (pulito.isEmpty()) return null
    val parti = pulito.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
    if (parti.size != 2) return null
    val lat = parti[0].toDoubleOrNull() ?: return null
    val lng = parti[1].toDoubleOrNull() ?: return null
    return lat to lng
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
