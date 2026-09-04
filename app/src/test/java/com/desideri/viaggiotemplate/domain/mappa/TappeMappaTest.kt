package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.LuogoCongelato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
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

    private fun luogo(id: String, lat: Double? = 10.0, lng: Double? = 20.0) = LuogoCongelato(
        luogoId = id, nome = id, indirizzo = null, latitudine = lat, longitudine = lng, colore = null, icona = null
    )

    private fun evento(id: Long, inizio: ZonedDateTime, fine: ZonedDateTime, titolo: String = "evento-$id") = EventoCreato(
        eventoId = id, inizio = inizio, fine = fine, titolo = titolo, descrizione = "", colore = null, indirizzoNavigazione = null
    )

    private fun posizione(
        id: Long,
        partenza: LuogoCongelato?,
        arrivo: LuogoCongelato?
    ) = PosizioneEventoCreato(calendarEventId = id, tipoTratta = TipoTratta.AUTO, partenza = partenza, arrivo = arrivo)

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
}
