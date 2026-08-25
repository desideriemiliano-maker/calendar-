package com.desideri.viaggiotemplate.data.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Una corsa cosi' come restituita dall'API, pronta per essere proposta come Orario fisso. */
data class CorsaScaricata(
    val partenza: LocalTime,
    val arrivo: LocalTime,
    val etichetta: String
)

/**
 * Scarica gli orari delle corse tra due stazioni da transport.opendata.ch, l'API pubblica e
 * gratuita (nessuna chiave richiesta) dei trasporti pubblici svizzeri: copre anche le tratte
 * SBB. Non e' un servizio ufficiale SBB, ma e' quello comunemente usato per questo tipo di
 * integrazione in assenza di credenziali per l'API ufficiale (che richiede registrazione).
 */
class OrariTrasportiSvizzeriClient {

    private val formatoData = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /** @throws java.io.IOException se la richiesta di rete fallisce. */
    fun cercaCorse(daStazione: String, aStazione: String, data: LocalDate, limite: Int = 16): List<CorsaScaricata> {
        val url = "https://transport.opendata.ch/v1/connections" +
            "?from=${URLEncoder.encode(daStazione, "UTF-8")}" +
            "&to=${URLEncoder.encode(aStazione, "UTF-8")}" +
            "&date=${data.format(formatoData)}" +
            "&limit=$limite"
        val connessione = URL(url).openConnection() as HttpURLConnection
        connessione.connectTimeout = 10_000
        connessione.readTimeout = 10_000
        return try {
            val corpo = connessione.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(corpo)
            val connessioni = json.optJSONArray("connections") ?: return emptyList()
            (0 until connessioni.length())
                .mapNotNull { indice -> connessioni.getJSONObject(indice).toCorsaOppureNull() }
        } finally {
            connessione.disconnect()
        }
    }

    private fun JSONObject.toCorsaOppureNull(): CorsaScaricata? {
        val partenzaIso = optJSONObject("from")?.optString("departure")?.takeIf { it.isNotBlank() } ?: return null
        val arrivoIso = optJSONObject("to")?.optString("arrival")?.takeIf { it.isNotBlank() } ?: return null
        val partenza = partenzaIso.oraLocaleOppureNull() ?: return null
        val arrivo = arrivoIso.oraLocaleOppureNull() ?: return null
        val prodotti = optJSONArray("products")
        val categorie = if (prodotti != null) {
            (0 until prodotti.length()).map { prodotti.getString(it) }.distinct().joinToString("/")
        } else ""
        return CorsaScaricata(partenza = partenza, arrivo = arrivo, etichetta = categorie)
    }

    /**
     * L'API restituisce datetime tipo "2026-08-26T01:26:00+0200": l'offset senza i due punti
     * non e' nel formato ISO esteso richiesto da `OffsetDateTime.parse` (che lancerebbe
     * un'eccezione). Dato che l'orario e' gia' quello locale della stazione, basta leggere
     * direttamente la porzione HH:mm:ss senza toccare l'offset.
     */
    private fun String.oraLocaleOppureNull(): LocalTime? =
        if (length >= 19) runCatching { LocalTime.parse(substring(11, 19)) }.getOrNull() else null
}
