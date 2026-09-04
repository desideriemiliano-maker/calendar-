package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.LuogoCongelato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Test di [calcolaPosizioneAttuale]: la richiesta che ha portato a scriverli era "non vedo
 * l'indicatore sulla mappa" - questi test verificano che il confronto temporale al cuore della
 * funzione sia corretto (in particolare: mai un falso positivo tra istanti sulla stessa fascia
 * oraria ma giorni diversi, l'ipotesi di bug più temuta) più i casi limite già documentati nel
 * commento della funzione stessa (fuori finestra, coordinate mancanti).
 *
 * [mappaCoordinate] simula [RisoluzioneMappa.tappe] (le coordinate RISOLTE che disegnano i pin,
 * comprensive dell'eventuale fallback di geocoding - vedi TemplateMappaScreen.kt): deriva le
 * coordinate direttamente dai campi congelati dei [LuogoCongelato] passati, escludendo quelli
 * senza coordinate (esattamente come farebbe risolviMappa se il geocoding NON fosse disponibile
 * o non fosse ancora completato). I test che vogliono simulare un geocoding riuscito nonostante
 * coordinate congelate assenti costruiscono la mappa a mano invece di usare questo helper.
 */
class TappeMappaTest {

    private val zona = ZoneOffset.UTC

    private fun istante(giorno: Int, ora: Int, minuto: Int = 0) =
        ZonedDateTime.of(2026, 6, giorno, ora, minuto, 0, 0, zona)

    private fun luogo(id: String, nome: String = id, lat: Double? = 10.0, lng: Double? = 20.0) = LuogoCongelato(
        luogoId = id, nome = nome, indirizzo = null, latitudine = lat, longitudine = lng, colore = null, icona = null
    )

    private fun evento(id: Long, inizio: ZonedDateTime, fine: ZonedDateTime, titolo: String = "evento-$id") = EventoCreato(
        eventoId = id, inizio = inizio, fine = fine, titolo = titolo, descrizione = "", colore = null, indirizzoNavigazione = null
    )

    private fun posizione(
        id: Long,
        partenza: LuogoCongelato?,
        arrivo: LuogoCongelato?
    ) = PosizioneEventoCreato(calendarEventId = id, tipoTratta = TipoTratta.AUTO, partenza = partenza, arrivo = arrivo)

    private fun mappaCoordinate(posizioni: List<PosizioneEventoCreato>): Map<String, Pair<Double, Double>> =
        posizioni.flatMap { listOfNotNull(it.partenza, it.arrivo) }
            .mapNotNull { luogo ->
                val lat = luogo.latitudine
                val lng = luogo.longitudine
                if (lat != null && lng != null) luogo.luogoId to (lat to lng) else null
            }
            .toMap()

    @Test
    fun `adesso a meta di una tratta interpola linearmente tra partenza e arrivo`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivo = luogo("B", lat = 10.0, lng = 20.0)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30) // esattamente a metà dell'ora di durata
        val posizioni = listOf(posizione(1, partenza, arrivo))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata, ottenuto $risultato", trovata != null)
        val posizioneAttuale = trovata!!.posizione as PosizioneAttualeEsecuzione.SuSegmento
        assertEquals(5.0, posizioneAttuale.lat, 0.0001)
        assertEquals(10.0, posizioneAttuale.lng, 0.0001)
    }

    /**
     * Riproduce il secondo scenario reale segnalato dall'utente (stessa esecuzione Lugano/Ariccia,
     * screenshot delle 18:14): adesso è DENTRO il terzo blocco (17:30-21:00, Milano Centrale ->
     * Roma Termini), non più in un'attesa - il ramo da verificare è l'interpolazione, non quello
     * dell'attesa già coperto sopra. Conferma che la funzione produce comunque una posizione
     * corretta: il problema riportato ("non vedo indicatore né pulsante") non era qui.
     */
    @Test
    fun `adesso dentro il terzo blocco (17-30 a 21-00, verificato alle 18-14) interpola correttamente`() {
        val partenza = luogo("milano-centrale", "Milano Centrale", lat = 0.0, lng = 0.0)
        val arrivo = luogo("roma-termini", "Roma Termini", lat = 100.0, lng = 200.0)
        val ev = evento(3, istante(4, 17, 30), istante(4, 21, 0), titolo = "Milano Centrale -> Roma Termini")
        val adesso = istante(4, 18, 14)
        val posizioni = listOf(posizione(3, partenza, arrivo))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata, ottenuto $risultato", trovata != null)
        val posizioneAttuale = trovata!!.posizione as PosizioneAttualeEsecuzione.SuSegmento
        val frazioneAttesa = 44.0 / 210.0 // 17:30 -> 18:14 = 44 minuti, su una tratta di 3h30m = 210 minuti
        assertEquals(100.0 * frazioneAttesa, posizioneAttuale.lat, 0.01)
        assertEquals(200.0 * frazioneAttesa, posizioneAttuale.lng, 0.01)
    }

    /**
     * Stesso scenario, ma con Roma Termini SENZA coordinate né congelate né risolte (nessun
     * geocoding riuscito nemmeno lui, dato che mappaCoordinate esclude i luoghi senza coordinate
     * congelate): il motivo deve nominare esplicitamente "Roma Termini" - non un generico
     * "partenza o arrivo" che lascerebbe l'utente a controllare entrambi i luoghi a mano.
     */
    @Test
    fun `Roma Termini senza coordinate produce un motivo che lo nomina esplicitamente`() {
        val milanoCentrale = luogo("milano-centrale", "Milano Centrale", lat = 45.4841, lng = 9.2039)
        val romaTerminiSenzaCoordinate = luogo("roma-termini", "Roma Termini", lat = null, lng = null)
        val ev = evento(3, istante(4, 17, 30), istante(4, 21, 0), titolo = "Milano Centrale -> Roma Termini")
        val adesso = istante(4, 18, 14)
        val posizioni = listOf(posizione(3, milanoCentrale, romaTerminiSenzaCoordinate))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        val nonDisponibile = risultato as? RisultatoPosizioneAttuale.NonDisponibile
        assertTrue("atteso NonDisponibile, ottenuto $risultato", nonDisponibile != null)
        // "Coordinate mancanti per Roma Termini:" e non "...per Milano Centrale, Roma Termini:" -
        // il titolo dell'evento (usato più avanti nello stesso motivo) contiene naturalmente
        // entrambi i nomi, quindi la sola presenza di "Roma Termini" non basterebbe a verificare
        // che SOLO lei sia elencata tra i luoghi senza coordinate.
        assertTrue(
            "motivo atteso con solo 'Roma Termini' tra i luoghi senza coordinate, ottenuto '${nonDisponibile!!.motivo}'",
            nonDisponibile.motivo.contains("Coordinate mancanti per Roma Termini:")
        )
        assertTrue("non deve essere marcato come 'fuori finestra'", !nonDisponibile.fuoriFinestra)
    }

    @Test
    fun `adesso in un'attesa tra due eventi nello stesso luogo evidenzia il luogo`() {
        val a = luogo("A")
        val b = luogo("B", lat = 5.0, lng = 6.0)
        val ev1 = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val ev2 = evento(2, istante(10, 10, 30), istante(10, 11, 0))
        val adesso = istante(10, 10, 15) // dentro l'attesa tra fine ev1 e inizio ev2
        val posizioni = listOf(posizione(1, a, b), posizione(2, b, a))

        val risultato = calcolaPosizioneAttuale(listOf(ev1, ev2), posizioni, adesso, mappaCoordinate(posizioni))

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata, ottenuto $risultato", trovata != null)
        val suLuogo = trovata!!.posizione as PosizioneAttualeEsecuzione.SuLuogo
        assertEquals("B", suLuogo.luogoId)
        assertEquals(5.0, suLuogo.lat, 0.0001)
        assertEquals(6.0, suLuogo.lng, 0.0001)
    }

    @Test
    fun `adesso prima del primo evento non da' nessuna posizione`() {
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 8, 0)
        val posizioni = listOf(posizione(1, luogo("A"), luogo("B")))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    @Test
    fun `adesso dopo l'ultimo evento non da' nessuna posizione`() {
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 12, 0)
        val posizioni = listOf(posizione(1, luogo("A"), luogo("B")))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    /**
     * Il test che verifica direttamente il sospetto principale: un evento con la STESSA fascia
     * oraria (09:00-10:00) ma su un giorno diverso da "adesso" non deve MAI far scattare il ramo
     * "in corso" - solo un confronto che ignorasse la data (es. su LocalTime invece che su
     * ZonedDateTime) produrrebbe qui un falso positivo.
     */
    @Test
    fun `evento con la stessa fascia oraria ma di un altro giorno non genera un falso positivo`() {
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0)) // 10 giugno, 09:00-10:00
        val adesso = istante(11, 9, 30) // 11 giugno, stessa fascia oraria (09:30)
        val posizioni = listOf(posizione(1, luogo("A"), luogo("B")))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue(
            "atteso NonDisponibile (nessun evento copre 'adesso'), ottenuto $risultato",
            risultato is RisultatoPosizioneAttuale.NonDisponibile
        )
    }

    @Test
    fun `coordinate mancanti su partenza o arrivo non producono una posizione inventata`() {
        val partenzaSenzaCoordinate = luogo("A", lat = null, lng = null)
        val arrivo = luogo("B")
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)
        val posizioni = listOf(posizione(1, partenzaSenzaCoordinate, arrivo))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    @Test
    fun `nessuna posizione tracciata per l'evento in corso non produce una posizione inventata`() {
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        // Nessuna voce in posizioni per l'evento 1: come un evento creato prima di questa feature.
        val risultato = calcolaPosizioneAttuale(listOf(ev), emptyList(), adesso)

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    @Test
    fun `attesa tra luoghi diversi (evento intermedio non tracciabile) non produce una posizione inventata`() {
        val ev1 = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val ev2 = evento(2, istante(10, 10, 30), istante(10, 11, 0))
        val adesso = istante(10, 10, 15)

        // Arrivo di ev1 e partenza di ev2 NON coincidono (luoghi "B" e "C"): un evento intermedio
        // non tracciabile è stato "saltato" nel mezzo, non c'è certezza su dove ci si trovi.
        val posizioni = listOf(posizione(1, luogo("A"), luogo("B")), posizione(2, luogo("C"), luogo("D")))

        val risultato = calcolaPosizioneAttuale(listOf(ev1, ev2), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    /**
     * Causa reale confermata dal registro attività dell'utente: le coordinate congelate mancano,
     * ma la mappa le risolve comunque a runtime via geocoding dell'indirizzo (da cui i pin visibili
     * negli screenshot). Il calcolo deve usare esattamente quella mappa risolta
     * (coordinatePerLuogoId, costruita dal chiamante da RisoluzioneMappa.tappe), non i soli campi
     * congelati: qui simula il geocoding avvenuto con successo, e il risultato deve essere Trovata.
     */
    @Test
    fun `coordinate congelate mancanti ma presenti in coordinatePerLuogoId (geocoding) producono una posizione`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivoSenzaCoordinateCongelate = luogo("milano-centrale", "Milano Centrale", lat = null, lng = null)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenza, arrivoSenzaCoordinateCongelate)),
            adesso,
            coordinatePerLuogoId = mapOf(
                "A" to (0.0 to 0.0),
                "milano-centrale" to (45.4841 to 9.2039) // "risolto" da risolviMappa via Geocoder
            )
        )

        assertTrue("atteso Trovata, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.Trovata)
    }

    /**
     * Stesso caso, ma il geocoding NON è (ancora) presente in coordinatePerLuogoId - es. non
     * ancora completato, o fallito anche lui: l'esito resta NonDisponibile, esattamente come prima
     * di questo fix. Copre anche il caso "calcolo eseguito prima che il geocoding completi",
     * segnalato dall'utente: il chiamante deve limitarsi a passare la mappa che ha in quel momento.
     */
    @Test
    fun `coordinate assenti sia congelate sia in coordinatePerLuogoId restano NonDisponibile`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivoSenzaCoordinate = luogo("milano-centrale", "Milano Centrale", lat = null, lng = null)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenza, arrivoSenzaCoordinate)),
            adesso,
            coordinatePerLuogoId = mapOf("A" to (0.0 to 0.0)) // "milano-centrale" non ancora risolto
        )

        assertTrue("atteso NonDisponibile, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    /**
     * Verifica esplicita richiesta dall'utente: il confronto temporale resta corretto anche
     * quando "adesso" e l'evento portano ZonedDateTime in FUSI ORARI DIVERSI (nel registro reale:
     * la finestra dell'esecuzione in Europe/Zurich, "adesso" in Europe/Rome - stesso offset oggi,
     * ma non deve essere per quello che il confronto funziona). isBefore/isAfter confrontano
     * sempre l'istante assoluto, mai l'ora locale: un evento a Berlino (UTC+2 in estate) e
     * "adesso" ad Auckland (UTC+12) che punta ALLO STESSO istante deve comunque risultare "in
     * corso", nonostante ore locali completamente diverse.
     */
    @Test
    fun `il confronto temporale e' corretto anche tra fusi orari diversi per lo stesso istante`() {
        val zonaBerlino = ZoneId.of("Europe/Berlin")
        val zonaAuckland = ZoneId.of("Pacific/Auckland")
        val inizioBerlino = ZonedDateTime.of(2026, 6, 10, 9, 0, 0, 0, zonaBerlino)
        val fineBerlino = ZonedDateTime.of(2026, 6, 10, 10, 0, 0, 0, zonaBerlino)
        // Stesso istante di inizioBerlino, espresso in Pacific/Auckland: +30 minuti da quell'istante,
        // cioè esattamente a metà tratta - qualunque sia l'ora locale mostrata in quel fuso.
        val adessoAuckland = inizioBerlino.withZoneSameInstant(zonaAuckland).plusMinutes(30)

        val ev = EventoCreato(
            eventoId = 1, inizio = inizioBerlino, fine = fineBerlino,
            titolo = "evento-berlino", descrizione = "", colore = null, indirizzoNavigazione = null
        )
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivo = luogo("B", lat = 10.0, lng = 20.0)
        val posizioni = listOf(posizione(1, partenza, arrivo))

        val risultato = calcolaPosizioneAttuale(listOf(ev), posizioni, adessoAuckland, mappaCoordinate(posizioni))

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata (stesso istante, fusi diversi), ottenuto $risultato", trovata != null)
        val posizioneAttuale = trovata!!.posizione as PosizioneAttualeEsecuzione.SuSegmento
        assertEquals(5.0, posizioneAttuale.lat, 0.0001) // a metà tratta, come da +30 minuti su un'ora
        assertEquals(10.0, posizioneAttuale.lng, 0.0001)
    }

    /**
     * Riproduce esattamente lo scenario reale segnalato dall'utente (esecuzione Lugano/Ariccia del
     * 04/09/2026): un'attesa a Milano Centrale tra l'arrivo di un treno (blocco 15:00-16:20) e la
     * partenza del successivo (blocco 17:30-21:00), con "adesso" alle 17:01 - dentro il buco. Con
     * lo stesso luogoId su entrambi i lati e coordinate presenti, deve comportarsi come il caso
     * generico già coperto sopra: qui blocca in un test i numeri esatti del bug report.
     */
    @Test
    fun `attesa a Milano Centrale con stesso luogoId e coordinate presenti mostra la posizione`() {
        val milanoCentrale = luogo("milano-centrale", "Milano Centrale", lat = 45.4841, lng = 9.2039)
        val trenoInArrivo = evento(2, istante(4, 15, 0), istante(4, 16, 20), titolo = "Lugano Stazione -> Milano Centrale")
        val trenoInPartenza = evento(3, istante(4, 17, 30), istante(4, 21, 0), titolo = "Milano Centrale -> Roma Termini")
        val adesso = istante(4, 17, 1)
        val posizioni = listOf(
            posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentrale),
            posizione(3, milanoCentrale, luogo("roma-termini", "Roma Termini"))
        )

        val risultato = calcolaPosizioneAttuale(listOf(trenoInArrivo, trenoInPartenza), posizioni, adesso, mappaCoordinate(posizioni))

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata, ottenuto $risultato", trovata != null)
        val suLuogo = trovata!!.posizione as PosizioneAttualeEsecuzione.SuLuogo
        assertEquals("milano-centrale", suLuogo.luogoId)
    }

    /**
     * Stessa attesa a Milano Centrale, ma le due tratte referenziano due Luogo DIVERSI per lo
     * stesso posto reale (id diversi, stesso nome) - il caso di una libreria Luoghi non
     * deduplicata segnalato dall'utente come possibile causa: un confronto sul solo luogoId
     * fallirebbe qui sempre, il fallback per nome lo recupera.
     */
    @Test
    fun `attesa a Milano Centrale con due luogoId diversi ma stesso nome mostra comunque la posizione`() {
        val milanoCentraleArrivo = luogo("luogo-creato-per-la-tratta-2", "Milano Centrale", lat = 45.4841, lng = 9.2039)
        val milanoCentralePartenza = luogo("luogo-creato-per-la-tratta-3", "Milano Centrale", lat = 45.4841, lng = 9.2039)
        val trenoInArrivo = evento(2, istante(4, 15, 0), istante(4, 16, 20))
        val trenoInPartenza = evento(3, istante(4, 17, 30), istante(4, 21, 0))
        val adesso = istante(4, 17, 1)
        val posizioni = listOf(
            posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentraleArrivo),
            posizione(3, milanoCentralePartenza, luogo("roma-termini", "Roma Termini"))
        )

        val risultato = calcolaPosizioneAttuale(listOf(trenoInArrivo, trenoInPartenza), posizioni, adesso, mappaCoordinate(posizioni))

        assertTrue("atteso Trovata, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.Trovata)
    }

    /**
     * Stessa attesa a Milano Centrale, stesso luogoId su entrambi i lati, ma senza coordinate né
     * congelate né risolte (nessun geocoding riuscito nemmeno lui): l'esito è NonDisponibile con
     * motivo esplicito "coordinate mancanti", non un buco silenzioso, e NON è "fuori finestra" (il
     * viaggio è comunque in corso).
     */
    @Test
    fun `attesa a Milano Centrale senza coordinate produce NonDisponibile con motivo esplicito, non fuori finestra`() {
        val milanoCentraleSenzaCoordinate = luogo("milano-centrale", "Milano Centrale", lat = null, lng = null)
        val trenoInArrivo = evento(2, istante(4, 15, 0), istante(4, 16, 20))
        val trenoInPartenza = evento(3, istante(4, 17, 30), istante(4, 21, 0))
        val adesso = istante(4, 17, 1)
        val posizioni = listOf(
            posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentraleSenzaCoordinate),
            posizione(3, milanoCentraleSenzaCoordinate, luogo("roma-termini", "Roma Termini"))
        )

        val risultato = calcolaPosizioneAttuale(listOf(trenoInArrivo, trenoInPartenza), posizioni, adesso, mappaCoordinate(posizioni))

        val nonDisponibile = risultato as? RisultatoPosizioneAttuale.NonDisponibile
        assertTrue("atteso NonDisponibile, ottenuto $risultato", nonDisponibile != null)
        assertTrue(
            "motivo atteso su coordinate mancanti, ottenuto '${nonDisponibile!!.motivo}'",
            nonDisponibile.motivo.contains("Coordinate mancanti")
        )
        assertTrue("non deve essere marcato come 'fuori finestra'", !nonDisponibile.fuoriFinestra)
    }
}
