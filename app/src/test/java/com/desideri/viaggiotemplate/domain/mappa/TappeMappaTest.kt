package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.LuogoCongelato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Test di [calcolaPosizioneAttuale]: la richiesta che ha portato a scriverli era "non vedo
 * l'indicatore sulla mappa" - questi test verificano che il confronto temporale al cuore della
 * funzione sia corretto (in particolare: mai un falso positivo tra istanti sulla stessa fascia
 * oraria ma giorni diversi, l'ipotesi di bug più temuta) più i casi limite già documentati nel
 * commento della funzione stessa (fuori finestra, coordinate mancanti).
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

    private fun luogoLive(id: String, nome: String = id, lat: Double? = 10.0, lng: Double? = 20.0) = Luogo(
        id = id, nome = nome, indirizzo = null, latitudine = lat, longitudine = lng, colore = null, icona = null
    )

    @Test
    fun `adesso a meta di una tratta interpola linearmente tra partenza e arrivo`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivo = luogo("B", lat = 10.0, lng = 20.0)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30) // esattamente a metà dell'ora di durata

        val risultato = calcolaPosizioneAttuale(listOf(ev), listOf(posizione(1, partenza, arrivo)), adesso)

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
     * corretta: il problema riportato ("non vedo indicatore né pulsante") non era qui, ma
     * nell'inquadratura della mappa e nella scopribilità del pulsante (vedi TemplateMappaScreen).
     */
    @Test
    fun `adesso dentro il terzo blocco (17-30 a 21-00, verificato alle 18-14) interpola correttamente`() {
        val partenza = luogo("milano-centrale", "Milano Centrale", lat = 0.0, lng = 0.0)
        val arrivo = luogo("roma-termini", "Roma Termini", lat = 100.0, lng = 200.0)
        val ev = evento(3, istante(4, 17, 30), istante(4, 21, 0), titolo = "Milano Centrale -> Roma Termini")
        val adesso = istante(4, 18, 14)

        val risultato = calcolaPosizioneAttuale(listOf(ev), listOf(posizione(3, partenza, arrivo)), adesso)

        val trovata = risultato as? RisultatoPosizioneAttuale.Trovata
        assertTrue("atteso Trovata, ottenuto $risultato", trovata != null)
        val posizioneAttuale = trovata!!.posizione as PosizioneAttualeEsecuzione.SuSegmento
        val frazioneAttesa = 44.0 / 210.0 // 17:30 -> 18:14 = 44 minuti, su una tratta di 3h30m = 210 minuti
        assertEquals(100.0 * frazioneAttesa, posizioneAttuale.lat, 0.01)
        assertEquals(200.0 * frazioneAttesa, posizioneAttuale.lng, 0.01)
    }

    /**
     * Stesso scenario, ma con Roma Termini SENZA coordinate (Milano Centrale le ha, come conferma
     * il suo marker visibile nello screenshot dell'utente): il motivo deve nominare esplicitamente
     * "Roma Termini" - non un generico "partenza o arrivo" che lascerebbe l'utente a controllare
     * entrambi i luoghi a mano per capire quale correggere in Luoghi.
     */
    @Test
    fun `Roma Termini senza coordinate produce un motivo che lo nomina esplicitamente`() {
        val milanoCentrale = luogo("milano-centrale", "Milano Centrale", lat = 45.4841, lng = 9.2039)
        val romaTerminiSenzaCoordinate = luogo("roma-termini", "Roma Termini", lat = null, lng = null)
        val ev = evento(3, istante(4, 17, 30), istante(4, 21, 0), titolo = "Milano Centrale -> Roma Termini")
        val adesso = istante(4, 18, 14)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(3, milanoCentrale, romaTerminiSenzaCoordinate)),
            adesso
        )

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

        val risultato = calcolaPosizioneAttuale(
            listOf(ev1, ev2),
            listOf(posizione(1, a, b), posizione(2, b, a)),
            adesso
        )

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

        val risultato = calcolaPosizioneAttuale(listOf(ev), listOf(posizione(1, luogo("A"), luogo("B"))), adesso)

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    @Test
    fun `adesso dopo l'ultimo evento non da' nessuna posizione`() {
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 12, 0)

        val risultato = calcolaPosizioneAttuale(listOf(ev), listOf(posizione(1, luogo("A"), luogo("B"))), adesso)

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

        val risultato = calcolaPosizioneAttuale(listOf(ev), listOf(posizione(1, luogo("A"), luogo("B"))), adesso)

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

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenzaSenzaCoordinate, arrivo)),
            adesso
        )

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
        val risultato = calcolaPosizioneAttuale(
            listOf(ev1, ev2),
            listOf(posizione(1, luogo("A"), luogo("B")), posizione(2, luogo("C"), luogo("D"))),
            adesso
        )

        assertTrue(risultato is RisultatoPosizioneAttuale.NonDisponibile)
    }

    /**
     * Diagnosi confermata dall'utente (marker di fallback debug sempre mostrato, mai quello vero):
     * un'esecuzione può avere posizioni congelate valide su nome/id (i pin della mappa base
     * funzionano) ma senza coordinate, perché il Luogo non le aveva ancora al momento della
     * creazione. Con lo stesso id anche nella libreria Luoghi attuale, il ripiego su luoghiLive
     * deve recuperare le coordinate e produrre comunque una posizione.
     */
    @Test
    fun `coordinate congelate mancanti vengono recuperate dal Luogo attuale con lo stesso id`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivoSenzaCoordinate = luogo("milano-centrale", "Milano Centrale", lat = null, lng = null)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenza, arrivoSenzaCoordinate)),
            adesso,
            luoghiLive = listOf(luogoLive("milano-centrale", "Milano Centrale", lat = 45.4841, lng = 9.2039))
        )

        assertTrue("atteso Trovata, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.Trovata)
    }

    /**
     * Stesso caso, ma il Luogo congelato ha un id che non esiste più nella libreria attuale (es.
     * eliminato e poi ricreato): il ripiego prova per nome, e deve funzionare quando il nome è
     * univoco tra i Luoghi attuali.
     */
    @Test
    fun `coordinate congelate mancanti vengono recuperate dal Luogo attuale con lo stesso nome se l'id non esiste piu`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivoSenzaCoordinate = luogo("id-vecchio-eliminato", "Milano Centrale", lat = null, lng = null)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenza, arrivoSenzaCoordinate)),
            adesso,
            luoghiLive = listOf(luogoLive("id-nuovo", "Milano Centrale", lat = 45.4841, lng = 9.2039))
        )

        assertTrue("atteso Trovata, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.Trovata)
    }

    /**
     * Il ripiego per nome NON deve mai indovinare: se due Luoghi attuali condividono lo stesso
     * nome, scegliere uno a caso produrrebbe potenzialmente una posizione SBAGLIATA - peggio che
     * nessuna posizione. In questo caso l'esito resta NonDisponibile, esattamente come senza
     * ripiego.
     */
    @Test
    fun `il ripiego per nome non si applica se il nome e' ambiguo tra piu' Luoghi attuali`() {
        val partenza = luogo("A", lat = 0.0, lng = 0.0)
        val arrivoSenzaCoordinate = luogo("id-vecchio", "Milano Centrale", lat = null, lng = null)
        val ev = evento(1, istante(10, 9, 0), istante(10, 10, 0))
        val adesso = istante(10, 9, 30)

        val risultato = calcolaPosizioneAttuale(
            listOf(ev),
            listOf(posizione(1, partenza, arrivoSenzaCoordinate)),
            adesso,
            luoghiLive = listOf(
                luogoLive("id-1", "Milano Centrale", lat = 45.4841, lng = 9.2039),
                luogoLive("id-2", "Milano Centrale", lat = 45.0, lng = 9.0)
            )
        )

        assertTrue("atteso NonDisponibile, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.NonDisponibile)
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

        val risultato = calcolaPosizioneAttuale(
            listOf(trenoInArrivo, trenoInPartenza),
            listOf(
                posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentrale),
                posizione(3, milanoCentrale, luogo("roma-termini", "Roma Termini"))
            ),
            adesso
        )

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

        val risultato = calcolaPosizioneAttuale(
            listOf(trenoInArrivo, trenoInPartenza),
            listOf(
                posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentraleArrivo),
                posizione(3, milanoCentralePartenza, luogo("roma-termini", "Roma Termini"))
            ),
            adesso
        )

        assertTrue("atteso Trovata, ottenuto $risultato", risultato is RisultatoPosizioneAttuale.Trovata)
    }

    /**
     * Stessa attesa a Milano Centrale, stesso luogoId su entrambi i lati, ma senza coordinate (il
     * caso storico dei luoghi di tipo stazione, compilati soprattutto per le tratte AUTO): l'esito
     * è NonDisponibile con motivo esplicito "coordinate mancanti", non un buco silenzioso, e NON è
     * "fuori finestra" (il viaggio è comunque in corso) - va quindi segnalato come banner in
     * EsecuzioneMappaScreen, non solo loggato.
     */
    @Test
    fun `attesa a Milano Centrale senza coordinate produce NonDisponibile con motivo esplicito, non fuori finestra`() {
        val milanoCentraleSenzaCoordinate = luogo("milano-centrale", "Milano Centrale", lat = null, lng = null)
        val trenoInArrivo = evento(2, istante(4, 15, 0), istante(4, 16, 20))
        val trenoInPartenza = evento(3, istante(4, 17, 30), istante(4, 21, 0))
        val adesso = istante(4, 17, 1)

        val risultato = calcolaPosizioneAttuale(
            listOf(trenoInArrivo, trenoInPartenza),
            listOf(
                posizione(2, luogo("lugano-stazione", "Lugano Stazione"), milanoCentraleSenzaCoordinate),
                posizione(3, milanoCentraleSenzaCoordinate, luogo("roma-termini", "Roma Termini"))
            ),
            adesso
        )

        val nonDisponibile = risultato as? RisultatoPosizioneAttuale.NonDisponibile
        assertTrue("atteso NonDisponibile, ottenuto $risultato", nonDisponibile != null)
        assertTrue(
            "motivo atteso su coordinate mancanti, ottenuto '${nonDisponibile!!.motivo}'",
            nonDisponibile.motivo.contains("Coordinate mancanti")
        )
        assertTrue("non deve essere marcato come 'fuori finestra'", !nonDisponibile.fuoriFinestra)
    }
}
