package com.desideri.viaggiotemplate.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Scarica gli orari reali delle corse tra due stazioni dall'endpoint di ricerca biglietti di
 * trenitalia.com/lefrecce.it (host `Channels.Website.BFF.WEB`), individuato per reverse
 * engineering: non e' un'API ufficiale ne' documentata da Trenitalia (che non ne pubblica una),
 * puo' quindi cambiare o smettere di funzionare senza preavviso, esattamente come gia' evidenziato
 * per l'analogo client SBB (vedi [OrariTrasportiSvizzeriClient]). A differenza di SBB, qui il nome
 * stazione va prima risolto a un id numerico tramite l'endpoint di ricerca stazioni.
 */
class OrariTrenitaliaClient : ClientOrariTreno {

    private val baseUrl = "https://www.lefrecce.it/Channels.Website.BFF.WEB/website"
    private val zonaRoma = ZoneId.of("Europe/Rome")
    private val formatoIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    override fun cercaCorse(
        daStazione: String,
        aStazione: String,
        data: LocalDate,
        oraRiferimento: LocalTime,
        limite: Int
    ): List<CorsaScaricata> {
        val idPartenza = risolviStazione(daStazione) ?: return emptyList()
        val idArrivo = risolviStazione(aStazione) ?: return emptyList()
        val partenzaIso = data.atTime(oraRiferimento).atZone(zonaRoma).format(formatoIso)

        val corpo = JSONObject().apply {
            put("departureLocationId", idPartenza)
            put("arrivalLocationId", idArrivo)
            put("departureTime", partenzaIso)
            put("adults", 1)
            put("children", 0)
            put(
                "criteria",
                JSONObject().apply {
                    put("frecceOnly", false)
                    put("regionalOnly", false)
                    // Una Tratta di questa app rappresenta un singolo segno in treno: le soluzioni
                    // con cambio non sono utilizzabili come singolo "orario fisso" di una Tratta.
                    put("noChanges", true)
                    put("order", "DEPARTURE_DATE")
                    put("limit", limite)
                    put("offset", 0)
                }
            )
            put("advancedSearchRequest", JSONObject().apply { put("bestFare", false) })
        }

        val risposta = eseguiPost("$baseUrl/ticket/solutions", corpo)
        val soluzioni = risposta.optJSONArray("solutions") ?: return emptyList()
        return (0 until soluzioni.length())
            .mapNotNull { indice -> soluzioni.getJSONObject(indice).optJSONObject("solution")?.toCorsaOppureNull() }
    }

    private fun JSONObject.toCorsaOppureNull(): CorsaScaricata? {
        val partenzaIso = optString("departureTime").takeIf { it.isNotBlank() } ?: return null
        val arrivoIso = optString("arrivalTime").takeIf { it.isNotBlank() } ?: return null
        val partenza = partenzaIso.oraLocaleOppureNull() ?: return null
        val arrivo = arrivoIso.oraLocaleOppureNull() ?: return null
        val treni = optJSONArray("trains")
        val etichetta = if (treni != null) {
            (0 until treni.length())
                .map { i ->
                    val treno = treni.getJSONObject(i)
                    val sigla = treno.optString("acronym").ifBlank { treno.optString("trainCategory") }
                    val numero = treno.optString("name")
                    if (numero.isNotBlank()) "$sigla $numero" else sigla
                }
                .distinct()
                .joinToString("/")
        } else ""
        return CorsaScaricata(partenza = partenza, arrivo = arrivo, etichetta = etichetta)
    }

    /**
     * L'API restituisce datetime tipo "2026-09-02T08:10:00.000+02:00", gia' in ora locale
     * italiana: basta leggere direttamente la porzione HH:mm:ss senza toccare l'offset.
     */
    private fun String.oraLocaleOppureNull(): LocalTime? =
        if (length >= 19) runCatching { LocalTime.parse(substring(11, 19)) }.getOrNull() else null

    /** Risolve un nome stazione al suo id numerico tramite la ricerca stazioni; sceglie il primo risultato. */
    private fun risolviStazione(nome: String): Long? {
        val url = "$baseUrl/locations/search?name=${URLEncoder.encode(nome, "UTF-8")}&limit=1"
        val risultati = eseguiGet(url)
        if (risultati.length() == 0) return null
        val id = risultati.getJSONObject(0).optLong("id", -1)
        return if (id > 0) id else null
    }

    private fun eseguiGet(url: String): JSONArray {
        val connessione = URL(url).openConnection() as HttpURLConnection
        // Stessa soglia (e stesso motivo) di OrariTrasportiSvizzeriClient: 10s di lettura si sono
        // rivelati troppo stretti per fonti non ufficiali di questo tipo.
        connessione.connectTimeout = 8_000
        connessione.readTimeout = 20_000
        connessione.setRequestProperty("Accept", "application/json")
        return try {
            val corpo = connessione.inputStream.bufferedReader().use { it.readText() }
            JSONArray(corpo)
        } finally {
            connessione.disconnect()
        }
    }

    private fun eseguiPost(url: String, corpo: JSONObject): JSONObject {
        val connessione = URL(url).openConnection() as HttpURLConnection
        // Stessa soglia (e stesso motivo) di OrariTrasportiSvizzeriClient: 10s di lettura si sono
        // rivelati troppo stretti per fonti non ufficiali di questo tipo.
        connessione.connectTimeout = 8_000
        connessione.readTimeout = 20_000
        connessione.requestMethod = "POST"
        connessione.doOutput = true
        connessione.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connessione.setRequestProperty("Accept", "application/json")
        return try {
            connessione.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            val testo = connessione.inputStream.bufferedReader().use { it.readText() }
            JSONObject(testo)
        } finally {
            connessione.disconnect()
        }
    }
}
