package com.desideri.viaggiotemplate.data.remote

import com.desideri.viaggiotemplate.data.local.CredenzialiItalo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Scarica gli orari reali delle corse tra due stazioni dal motore di prenotazione di Italo
 * (big.ntvspa.it), individuato per reverse engineering (documentato da terzi su
 * github.com/SimoDax/Italo-API): non e' un'API ufficiale ne' documentata da Italo, puo' quindi
 * cambiare o smettere di funzionare senza preavviso, esattamente come gia' evidenziato per gli
 * analoghi client SBB e Trenitalia (vedi [OrariTrasportiSvizzeriClient], [OrariTrenitaliaClient]).
 * Ogni ricerca richiede prima un login "guest": le credenziali usate sono quelle incorporate
 * pubblicamente nel frontend del sito Italo stesso, non credenziali di un utente reale. A
 * differenza degli altri due client, Italo copre solo poche stazioni (le principali della rete
 * alta velocita') e non ha un endpoint di ricerca stazioni: la conversione nome -> sigla usa
 * quindi una tabella statica nota.
 *
 * Le credenziali guest sono bloccate dal gateway anti-bot di Italo (Layer7/Akamai, HTTP 403)
 * indipendentemente dall'header o dalla versione dell'API usata: se [credenziali] e' null si
 * tenta comunque il login guest (per se in futuro tornasse a funzionare), altrimenti si usa
 * l'account reale fornito dall'utente (impostazioni > "Accedi a Italo").
 */
class OrariItaloClient(private val credenziali: CredenzialiItalo? = null) : ClientOrariTreno {

    private val baseUrl = "https://big.ntvspa.it/BIG/v7/Rest"
    private val zonaRoma = ZoneId.of("Europe/Rome")

    private val stazioni = mapOf(
        "bologna centrale" to "BC_",
        "brescia" to "BSC",
        "ferrara" to "F__",
        "firenze s. m. novella" to "SMN",
        "milano centrale" to "MC_",
        "rho-fiera milano" to "RRO",
        "milano rogoredo" to "RG_",
        "napoli centrale" to "NAC",
        "padova" to "PD_",
        "reggio emilia av" to "AAV",
        "roma termini" to "RMT",
        "roma tiburtina" to "RTB",
        "salerno" to "SAL",
        "torino porta nuova" to "TOP",
        "torino porta susa" to "OUE",
        "venezia mestre" to "VEM",
        "venezia s. lucia" to "VSL",
        "verona porta nuova" to "VPN",
        "milano (tutte le stazioni)" to "MI0",
        "roma (tutte le stazioni)" to "RM0"
    )

    override fun cercaCorse(
        daStazione: String,
        aStazione: String,
        data: LocalDate,
        oraRiferimento: LocalTime,
        limite: Int
    ): List<CorsaScaricata> {
        val siglaPartenza = risolviStazione(daStazione) ?: return emptyList()
        val siglaArrivo = risolviStazione(aStazione) ?: return emptyList()
        val signature = login() ?: return emptyList()

        val corpo = JSONObject().apply {
            put(
                "GetAvailableTrains",
                JSONObject().apply {
                    put("RoundTrip", false)
                    put("DepartureStation", siglaPartenza)
                    put("ArrivalStation", siglaArrivo)
                    put("IntervalStartDateTime", data.atTime(oraRiferimento).aWcfDate())
                    put("IntervalEndDateTime", data.atTime(LocalTime.MAX).aWcfDate())
                    put("AdultNumber", 1)
                    put("ChildNumber", 0)
                    put("InfantNumber", 0)
                    put("SeniorNumber", 0)
                    put("CurrencyCode", "EUR")
                    put("IsGuest", credenziali == null)
                    put("OverrideIntervalTimeRestriction", true)
                    put("AvailabilityFilter", 1)
                }
            )
            put("Signature", signature)
            put("SourceSystem", 2)
        }

        val risposta = eseguiPost("$baseUrl/BookingManager.svc/GetAvailableTrains", corpo)
        val mercati = risposta.optJSONArray("JourneyDateMarkets") ?: return emptyList()
        return (0 until mercati.length())
            .flatMap { i -> mercati.getJSONObject(i).optJSONArray("Journeys")?.let { journeys ->
                (0 until journeys.length()).map { journeys.getJSONObject(it) }
            } ?: emptyList() }
            .mapNotNull { it.toCorsaOppureNull() }
            .take(limite)
    }

    /**
     * Una Tratta di questa app rappresenta un singolo segno in treno: le soluzioni con cambio
     * (piu' di un elemento in `Segments`) non sono utilizzabili come singolo "orario fisso" di
     * una Tratta, esattamente come per Trenitalia (che le esclude a monte con `noChanges: true`).
     */
    private fun JSONObject.toCorsaOppureNull(): CorsaScaricata? {
        val segmenti = optJSONArray("Segments") ?: return null
        if (segmenti.length() != 1) return null
        val segmento = segmenti.getJSONObject(0)
        val partenza = segmento.optString("STD").oraLocaleOppureNull() ?: return null
        val arrivo = segmento.optString("STA").oraLocaleOppureNull() ?: return null
        val numeroTreno = segmento.optString("TrainNumber")
        val etichetta = if (numeroTreno.isNotBlank()) "Italo $numeroTreno" else "Italo"
        return CorsaScaricata(partenza = partenza, arrivo = arrivo, etichetta = etichetta)
    }

    private fun risolviStazione(nome: String): String? = stazioni[nome.trim().lowercase()]

    private fun login(): String? {
        val corpo = JSONObject().apply {
            put(
                "Login",
                JSONObject().apply {
                    put("Domain", "WWW")
                    put("Username", credenziali?.username ?: "WWW_Anonymous")
                    put("Password", credenziali?.password ?: "Accenture$1")
                }
            )
            put("SourceSystem", 1)
        }
        val risposta = eseguiPost("$baseUrl/SessionManager.svc/Login", corpo)
        return risposta.optString("Signature").takeIf { it.isNotBlank() }
    }

    /** Converte in formato data WCF/.NET `"/Date(<millisecondi epoch>+0000)/"`, atteso dall'API. */
    private fun java.time.LocalDateTime.aWcfDate(): String {
        val millis = atZone(zonaRoma).toInstant().toEpochMilli()
        return "/Date($millis+0000)/"
    }

    /**
     * L'API restituisce datetime tipo "/Date(1538374500000+0200)/": a differenza degli altri
     * client (stringhe ISO), qui e' un timestamp epoch assoluto, quindi basta convertirlo con
     * [Instant] senza dover interpretare l'offset incluso nella stringa.
     */
    private fun String.oraLocaleOppureNull(): LocalTime? {
        val millis = substringAfter("(", "").substringBefore("+").substringBefore(")").toLongOrNull()
            ?: return null
        return Instant.ofEpochMilli(millis).atZone(zonaRoma).toLocalTime()
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
