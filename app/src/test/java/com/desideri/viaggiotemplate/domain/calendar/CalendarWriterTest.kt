package com.desideri.viaggiotemplate.domain.calendar

import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Test di [risolviIstanti], la funzione pura (nessun Android) che ricostruisce inizio/fine come
 * istanti assoluti a partire dagli orari [EventoCalcolato.inizioBlocco]/`fineBlocco`, che da soli
 * non portano alcuna informazione di giorno. Copre il bug per cui un evento con `fineBlocco`
 * "prima" di `inizioBlocco` (tratta notturna, o arrotondamento che spinge la fine a 00:00) veniva
 * scritto a calendario con DTEND prima di DTSTART, facendo fallire silenziosamente l'inserimento.
 */
class CalendarWriterTest {

    private val data = LocalDate.of(2026, 6, 15)

    private fun tratta(id: String) = Tratta(
        id = id, nome = id, tipo = TipoTratta.AUTO,
        luogoPartenzaId = "lp-$id", luogoArrivoId = "la-$id",
        luogoPartenza = "P-$id", luogoArrivo = "A-$id",
        durataMinutiReale = 30, margineMinuti = 0
    )

    private fun evento(id: String, inizioBlocco: LocalTime, fineBlocco: LocalTime) = EventoCalcolato(
        templateSlotId = id,
        tratta = tratta(id),
        inizioReale = inizioBlocco,
        fineReale = fineBlocco,
        inizioBlocco = inizioBlocco,
        fineBlocco = fineBlocco
    )

    private fun daScrivere(id: String, inizioBlocco: LocalTime, fineBlocco: LocalTime) = EventoDaScrivere(
        evento = evento(id, inizioBlocco, fineBlocco),
        notifica = Notifica.NESSUNA,
        descrizione = "",
        colore = null
    )

    @Test
    fun `evento nello stesso giorno resta sullo stesso giorno`() {
        val eventi = listOf(daScrivere("a", LocalTime.of(9, 0), LocalTime.of(10, 0)))
        val istanti = risolviIstanti(data, eventi)
        assertEquals(data.atTime(9, 0), istanti[0].first)
        assertEquals(data.atTime(10, 0), istanti[0].second)
    }

    @Test
    fun `evento che finisce esattamente a mezzanotte va sul giorno dopo`() {
        val eventi = listOf(daScrivere("a", LocalTime.of(23, 30), LocalTime.MIDNIGHT))
        val istanti = risolviIstanti(data, eventi)
        assertEquals(data.atTime(23, 30), istanti[0].first)
        assertEquals(data.plusDays(1).atTime(0, 0), istanti[0].second)
        assertTrue("la fine deve essere dopo l'inizio", istanti[0].second.isAfter(istanti[0].first))
    }

    @Test
    fun `evento che scavalca la mezzanotte senza finire esattamente a 00-00`() {
        val eventi = listOf(daScrivere("a", LocalTime.of(23, 30), LocalTime.of(0, 30)))
        val istanti = risolviIstanti(data, eventi)
        assertEquals(data.atTime(23, 30), istanti[0].first)
        assertEquals(data.plusDays(1).atTime(0, 30), istanti[0].second)
        assertTrue("la fine deve essere dopo l'inizio", istanti[0].second.isAfter(istanti[0].first))
    }

    @Test
    fun `arrotondamento che spinge la fine a 00-00 su un evento diurno`() {
        // Un blocco 23:50-23:55 arrotondato per eccesso a step 10 diventa 23:50-00:00.
        val eventi = listOf(daScrivere("a", LocalTime.of(23, 50), LocalTime.MIDNIGHT))
        val istanti = risolviIstanti(data, eventi)
        assertEquals(data.atTime(23, 50), istanti[0].first)
        assertEquals(data.plusDays(1).atTime(0, 0), istanti[0].second)
    }

    @Test
    fun `catena di eventi che scavalca la mezzanotte resta monotona`() {
        val eventi = listOf(
            daScrivere("1", LocalTime.of(22, 0), LocalTime.of(23, 0)),
            daScrivere("2", LocalTime.of(23, 10), LocalTime.of(0, 10)),
            daScrivere("3", LocalTime.of(0, 20), LocalTime.of(1, 0))
        )
        val istanti = risolviIstanti(data, eventi)

        assertEquals(data.atTime(22, 0), istanti[0].first)
        assertEquals(data.atTime(23, 0), istanti[0].second)

        assertEquals(data.atTime(23, 10), istanti[1].first)
        assertEquals(data.plusDays(1).atTime(0, 10), istanti[1].second)

        assertEquals(data.plusDays(1).atTime(0, 20), istanti[2].first)
        assertEquals(data.plusDays(1).atTime(1, 0), istanti[2].second)

        for (i in 1 until istanti.size) {
            assertTrue("evento $i deve iniziare dopo la fine del precedente", !istanti[i].first.isBefore(istanti[i - 1].second))
        }
        for (istante in istanti) {
            assertTrue("ogni evento deve finire dopo il suo inizio", istante.second.isAfter(istante.first))
        }
    }

    @Test
    fun `sequenza reale segnalata dall'utente - 15-00-18-00 seguito da 18-00-00-00`() {
        // Caso concreto: tratta "15:00 Todi / Todi 18:00" seguita da "18:00 Todi / Cantù Casa 00:00".
        // Il secondo evento deve finire alle 00:00 del giorno SUCCESSIVO, non dello stesso giorno
        // (altrimenti DTEND <= DTSTART: il Calendar Provider accetta comunque l'insert restituendo
        // un id, ma non materializza alcuna istanza — evento scritto ma invisibile ovunque).
        val eventi = listOf(
            daScrivere("A", LocalTime.of(15, 0), LocalTime.of(18, 0)),
            daScrivere("B", LocalTime.of(18, 0), LocalTime.MIDNIGHT)
        )
        val istanti = risolviIstanti(data, eventi)

        assertEquals(data.atTime(15, 0), istanti[0].first)
        assertEquals(data.atTime(18, 0), istanti[0].second)
        assertEquals(data.atTime(18, 0), istanti[1].first)
        assertEquals(data.plusDays(1).atTime(0, 0), istanti[1].second)
        assertTrue("la fine del secondo evento deve essere sul giorno successivo", istanti[1].second.isAfter(istanti[1].first))
    }

    @Test
    fun `evento a cavallo di mezzanotte esatto a inizio giornata non scavalca due volte`() {
        val eventi = listOf(
            daScrivere("1", LocalTime.of(0, 0), LocalTime.of(1, 0)),
            daScrivere("2", LocalTime.of(1, 30), LocalTime.of(2, 0))
        )
        val istanti = risolviIstanti(data, eventi)
        assertEquals(data.atTime(0, 0), istanti[0].first)
        assertEquals(data.atTime(1, 0), istanti[0].second)
        assertEquals(data.atTime(1, 30), istanti[1].first)
        assertEquals(data.atTime(2, 0), istanti[1].second)
    }
}
