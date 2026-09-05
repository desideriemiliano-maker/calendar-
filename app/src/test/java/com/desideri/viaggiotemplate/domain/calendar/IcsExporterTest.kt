package com.desideri.viaggiotemplate.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Test di [generaIcs] e delle sue funzioni di supporto: struttura VCALENDAR/VEVENT valida,
 * escaping RFC 5545 dei testi, orari sempre con fuso esplicito (mai locali "nudi"), promemoria
 * come VALARM e piegatura delle righe lunghe.
 */
class IcsExporterTest {

    private fun evento(
        id: Long = 1L,
        inizio: ZonedDateTime = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneOffset.of("+02:00")),
        fine: ZonedDateTime = ZonedDateTime.of(2026, 6, 15, 10, 30, 0, 0, ZoneOffset.of("+02:00")),
        titolo: String = "Milano -> Roma",
        descrizione: String = "",
        colore: Int? = null,
        indirizzoNavigazione: String? = null
    ) = EventoCreato(
        eventoId = id,
        inizio = inizio,
        fine = fine,
        titolo = titolo,
        descrizione = descrizione,
        colore = colore,
        indirizzoNavigazione = indirizzoNavigazione
    )

    private fun righe(ics: String): List<String> = ics.split("\r\n").filter { it.isNotEmpty() }

    // --- Struttura generale --------------------------------------------------------------------

    @Test
    fun `busta il calendario tra BEGIN e END VCALENDAR con un VEVENT per evento`() {
        val ics = generaIcs(listOf(evento(id = 1), evento(id = 2)))
        val righe = righe(ics)
        assertEquals("BEGIN:VCALENDAR", righe.first())
        assertEquals("END:VCALENDAR", righe.last())
        assertEquals(2, righe.count { it == "BEGIN:VEVENT" })
        assertEquals(2, righe.count { it == "END:VEVENT" })
        assertTrue(righe.contains("VERSION:2.0"))
    }

    @Test
    fun `ogni riga termina con CRLF, mai solo LF`() {
        val ics = generaIcs(listOf(evento()))
        assertFalse(ics.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun `lista vuota produce comunque un VCALENDAR valido senza VEVENT`() {
        val ics = generaIcs(emptyList())
        val righe = righe(ics)
        assertEquals("BEGIN:VCALENDAR", righe.first())
        assertEquals("END:VCALENDAR", righe.last())
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    // --- Campi del VEVENT -----------------------------------------------------------------------

    @Test
    fun `include titolo, luogo e descrizione`() {
        val ics = generaIcs(listOf(evento(titolo = "Partenza", descrizione = "Nota libera", indirizzoNavigazione = "Via Roma 1")))
        assertTrue(ics.contains("SUMMARY:Partenza"))
        assertTrue(ics.contains("LOCATION:Via Roma 1"))
        assertTrue(ics.contains("DESCRIPTION:Nota libera"))
    }

    @Test
    fun `omette LOCATION e DESCRIPTION quando assenti o vuoti`() {
        val ics = generaIcs(listOf(evento(descrizione = "", indirizzoNavigazione = null)))
        assertFalse(ics.contains("LOCATION:"))
        assertFalse(ics.contains("DESCRIPTION:"))
    }

    @Test
    fun `UID e' stabile e derivato dall'id evento`() {
        val ics = generaIcs(listOf(evento(id = 42L)))
        assertTrue(ics.contains("UID:evento-42@viaggiotemplate.desideri.com"))
    }

    @Test
    fun `UID resta identico rigenerando lo stesso evento in momenti diversi`() {
        val e = evento(id = 7L)
        val primo = generaIcs(listOf(e), adesso = Instant.parse("2026-01-01T00:00:00Z"))
        val secondo = generaIcs(listOf(e), adesso = Instant.parse("2026-06-15T12:00:00Z"))
        fun uid(ics: String) = righe(ics).single { it.startsWith("UID:") }
        assertEquals(uid(primo), uid(secondo))
    }

    @Test
    fun `DTSTAMP riflette il momento di generazione, non l'orario dell'evento`() {
        val ics = generaIcs(listOf(evento()), adesso = Instant.parse("2026-01-02T03:04:05Z"))
        assertTrue(ics.contains("DTSTAMP:20260102T030405Z"))
    }

    // --- Orari: sempre con fuso esplicito (mai locali "nudi") ------------------------------------

    @Test
    fun `DTSTART e DTEND sono espressi in UTC con suffisso Z, mai orari locali senza fuso`() {
        // +02:00: le 09:00 locali diventano 07:00 UTC, le 10:30 diventano 08:30 UTC.
        val ics = generaIcs(listOf(evento(
            inizio = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneOffset.of("+02:00")),
            fine = ZonedDateTime.of(2026, 6, 15, 10, 30, 0, 0, ZoneOffset.of("+02:00"))
        )))
        assertTrue(ics.contains("DTSTART:20260615T070000Z"))
        assertTrue(ics.contains("DTEND:20260615T083000Z"))
        // Nessuna riga DTSTART/DTEND con TZID: sarebbe ambigua senza un blocco VTIMEZONE.
        assertFalse(ics.contains("DTSTART;TZID"))
        assertFalse(ics.contains("DTEND;TZID"))
    }

    @Test
    fun `fusi orari diversi nella stessa esportazione convergono correttamente in UTC`() {
        val zurigo = evento(id = 1, inizio = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneOffset.of("+02:00")), fine = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, ZoneOffset.of("+02:00")))
        val londra = evento(id = 2, inizio = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneOffset.of("+01:00")), fine = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, ZoneOffset.of("+01:00")))
        val ics = generaIcs(listOf(zurigo, londra))
        assertTrue(ics.contains("DTSTART:20260615T070000Z"))
        assertTrue(ics.contains("DTSTART:20260615T080000Z"))
    }

    // --- Promemoria come VALARM -------------------------------------------------------------------

    @Test
    fun `evento con promemoria produce un VALARM con il TRIGGER corretto`() {
        val ics = generaIcs(listOf(evento(id = 5L)), promemoriaMinuti = mapOf(5L to 15))
        val righe = righe(ics)
        val inizioAlarm = righe.indexOf("BEGIN:VALARM")
        val fineAlarm = righe.indexOf("END:VALARM")
        assertTrue(inizioAlarm in 0 until fineAlarm)
        assertTrue(righe.contains("TRIGGER:-PT15M"))
        assertTrue(righe.contains("ACTION:DISPLAY"))
    }

    @Test
    fun `evento senza promemoria non produce alcun VALARM`() {
        val ics = generaIcs(listOf(evento(id = 5L)), promemoriaMinuti = emptyMap())
        assertFalse(ics.contains("VALARM"))
    }

    @Test
    fun `promemoria a zero minuti non produce VALARM`() {
        val ics = generaIcs(listOf(evento(id = 5L)), promemoriaMinuti = mapOf(5L to 0))
        assertFalse(ics.contains("VALARM"))
    }

    @Test
    fun `promemoria di un altro evento non si applica a questo`() {
        val ics = generaIcs(listOf(evento(id = 5L)), promemoriaMinuti = mapOf(99L to 30))
        assertFalse(ics.contains("VALARM"))
    }

    // --- Escaping RFC 5545 §3.3.11 -----------------------------------------------------------------

    @Test
    fun `escapeTestoIcs precede backslash punto e virgola e virgola con un backslash`() {
        assertEquals("a\\\\b", escapeTestoIcs("a\\b"))
        assertEquals("a\\;b", escapeTestoIcs("a;b"))
        assertEquals("a\\,b", escapeTestoIcs("a,b"))
    }

    @Test
    fun `escapeTestoIcs converte ogni forma di a-capo nella sequenza letterale backslash-n`() {
        assertEquals("a\\nb", escapeTestoIcs("a\nb"))
        assertEquals("a\\nb", escapeTestoIcs("a\r\nb"))
        assertEquals("a\\nb", escapeTestoIcs("a\rb"))
    }

    @Test
    fun `escapeTestoIcs esegue il backslash per primo per non ri-escapare backslash introdotti dopo`() {
        // Se ';' venisse escaped prima del backslash, "a;b" -> "a\;b" -> poi il backslash
        // verrebbe ri-escaped in "a\\;b", diverso dall'atteso "a\;b".
        assertEquals("a\\;b", escapeTestoIcs("a;b"))
        assertEquals("x\\\\y\\;z", escapeTestoIcs("x\\y;z"))
    }

    @Test
    fun `titolo e descrizione con caratteri speciali sono correttamente escaped nel VEVENT`() {
        val ics = generaIcs(listOf(evento(titolo = "Tratta, A; B", descrizione = "riga1\nriga2")))
        assertTrue(ics.contains("SUMMARY:Tratta\\, A\\; B"))
        assertTrue(ics.contains("DESCRIPTION:riga1\\nriga2"))
    }

    // --- Piegatura righe RFC 5545 §3.1 -------------------------------------------------------------

    @Test
    fun `piegaLineaIcs non tocca righe entro il limite`() {
        val corta = "SUMMARY:breve"
        assertEquals(corta, piegaLineaIcs(corta))
    }

    @Test
    fun `piegaLineaIcs spezza le righe lunghe con continuazione indentata da uno spazio`() {
        val lunga = "SUMMARY:" + "x".repeat(100)
        val piegata = piegaLineaIcs(lunga)
        assertTrue(piegata.contains("\r\n "))
        // Ogni riga fisica (incluso CRLF) rispetta il limite di 75 ottetti.
        piegata.split("\r\n").forEach { riga ->
            assertTrue(riga.toByteArray(Charsets.UTF_8).size <= 75)
        }
        // Ricomponendo (rimuovendo "\r\n" + lo spazio di continuazione) si ottiene il testo originale.
        assertEquals(lunga, piegata.replace("\r\n ", ""))
    }

    @Test
    fun `piegaLineaIcs non spezza mai a meta' di un carattere UTF-8 multi-byte`() {
        // Titolo con molti caratteri accentati italiani, per superare il limite di 75 ottetti
        // vicino a un confine multi-byte.
        val lungaConAccenti = "SUMMARY:" + "città più bellezza è però perché".repeat(4)
        val piegata = piegaLineaIcs(lungaConAccenti)
        piegata.split("\r\n").forEach { riga ->
            // Se il taglio cadesse a metà di un carattere multi-byte, decodificare come UTF-8
            // produrrebbe caratteri di rimpiazzo (U+FFFD) invece del testo originale intatto.
            assertFalse(riga.contains('�'))
        }
        assertEquals(lungaConAccenti, piegata.replace("\r\n ", ""))
    }

    // --- Nome file sicuro ----------------------------------------------------------------------

    @Test
    fun `nomeFileSicuroIcs rimuove caratteri non alfanumerici e tronca`() {
        assertEquals("Milano-Roma", nomeFileSicuroIcs("Milano/Roma"))
        assertEquals("evento", nomeFileSicuroIcs("   "))
        assertEquals(5, nomeFileSicuroIcs("abcdefghij", maxLunghezza = 5).length)
    }
}
