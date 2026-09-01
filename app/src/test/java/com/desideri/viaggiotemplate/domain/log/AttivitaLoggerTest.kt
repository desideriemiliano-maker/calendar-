package com.desideri.viaggiotemplate.domain.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Test delle funzioni pure di [AttivitaLogger] (nomi file, rotazione, serializzazione): nessuna dipendenza Android. */
class AttivitaLoggerTest {

    @Test
    fun `nomeFilePer e dataDaNomeFile sono inversi`() {
        val data = LocalDate.of(2026, 9, 1)
        val nome = nomeFilePer(data)
        assertEquals("attivita-2026-09-01.jsonl", nome)
        assertEquals(data, dataDaNomeFile(nome))
    }

    @Test
    fun `dataDaNomeFile ignora file estranei alla directory`() {
        assertNull(dataDaNomeFile("qualcosa_altro.txt"))
        assertNull(dataDaNomeFile("attivita-non-una-data.jsonl"))
        assertNull(dataDaNomeFile(".nomedia"))
    }

    @Test
    fun `fileDaEliminare conserva esattamente gli ultimi 7 giorni`() {
        val oggi = LocalDate.of(2026, 9, 15)
        val nomi = (0..10).map { nomeFilePer(oggi.minusDays(it.toLong())) }

        val daEliminare = fileDaEliminare(nomi, oggi)

        // Giorni conservati: oggi e i 6 precedenti (7 in tutto, offset 0..6); eliminati offset 7..10.
        val attesiEliminati = (7..10).map { nomeFilePer(oggi.minusDays(it.toLong())) }.toSet()
        assertEquals(attesiEliminati, daEliminare.toSet())
    }

    @Test
    fun `fileDaEliminare non tocca nulla se tutto rientra nella finestra`() {
        val oggi = LocalDate.of(2026, 9, 15)
        val nomi = (0..6).map { nomeFilePer(oggi.minusDays(it.toLong())) }
        assertTrue(fileDaEliminare(nomi, oggi).isEmpty())
    }

    @Test
    fun `fileDaEliminare ignora nomi file non riconosciuti`() {
        val oggi = LocalDate.of(2026, 9, 15)
        assertTrue(fileDaEliminare(listOf("altro.txt", ".nomedia"), oggi).isEmpty())
    }

    @Test
    fun `round-trip JSON di una voce integrazione`() {
        val voce = VoceRegistro(
            timestampMs = 1_756_700_123_456L,
            categoria = CategoriaRegistro.INTEGRAZIONE,
            descrizione = "Trenitalia: ricerca corse Roma Termini -> Milano Centrale",
            esito = EsitoRegistro.ERRORE,
            durataMs = 842L,
            dettaglioErrore = "timeout"
        )
        val ricostruita = voce.toJson().toVoceRegistro()
        assertEquals(voce, ricostruita)
    }

    @Test
    fun `round-trip JSON di una voce azione utente senza campi opzionali`() {
        val voce = VoceRegistro(
            timestampMs = 1_756_700_000_000L,
            categoria = CategoriaRegistro.AZIONE_UTENTE,
            descrizione = "Creati 5 eventi calendario per 'Weekend a Roma'"
        )
        val ricostruita = voce.toJson().toVoceRegistro()
        assertEquals(voce, ricostruita)
    }

    @Test
    fun `una riga JSON corrotta non fa fallire la lettura`() {
        val json = org.json.JSONObject("""{"ts": "non-un-numero", "cat": "AZIONE_UTENTE", "descr": "x"}""")
        assertNull(json.toVoceRegistro())
    }
}
