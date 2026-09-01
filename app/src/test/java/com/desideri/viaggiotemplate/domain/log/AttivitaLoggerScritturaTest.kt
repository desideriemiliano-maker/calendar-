package com.desideri.viaggiotemplate.domain.log

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Test di integrazione di [AttivitaLogger] con vera I/O su una directory temporanea (via
 * [AttivitaLogger.initPerTest], che bypassa il `Context` Android indisponibile nei test JVM puri).
 * Copre il bug per cui la schermata di consultazione mostrava "solo la prima azione registrata":
 * qui si verifica che sia il livello di scrittura di [AttivitaLogger] a conservare TUTTE le voci
 * (non solo la prima), sequenziali o concorrenti — la causa vera era altrove (nella UI, vedi
 * [com.desideri.viaggiotemplate.ui.registro.RegistroAttivitaScreen]: il ViewModel caricava una
 * volta sola alla creazione e non si aggiornava alle riaperture del registro), ma questi test
 * blindano comunque il livello di scrittura da eventuali regressioni future.
 */
class AttivitaLoggerScritturaTest {

    private lateinit var directoryTemp: File

    @Before
    fun setUp() {
        directoryTemp = Files.createTempDirectory("registro-attivita-test").toFile()
        AttivitaLogger.initPerTest(directoryTemp)
    }

    @After
    fun tearDown() {
        directoryTemp.deleteRecursively()
    }

    @Test
    fun `voci scritte in sequenza sono tutte presenti alla lettura`() = runBlocking {
        repeat(20) { i -> AttivitaLogger.azioneUtente("azione $i") }
        AttivitaLogger.attendiScrittureInSospesoPerTest()

        val voci = AttivitaLogger.leggiRecenti()

        assertEquals(20, voci.size)
        val descrizioni = voci.map { it.descrizione }.toSet()
        repeat(20) { i -> assertTrue("manca 'azione $i'", "azione $i" in descrizioni) }
    }

    @Test
    fun `voci scritte concorrentemente da più coroutine sono tutte presenti`() = runBlocking {
        val job = (0 until 50).map { i ->
            async { AttivitaLogger.azioneUtente("concorrente $i") }
        }
        job.forEach { it.await() }
        AttivitaLogger.attendiScrittureInSospesoPerTest()

        val voci = AttivitaLogger.leggiRecenti()

        assertEquals(50, voci.size)
        val descrizioni = voci.map { it.descrizione }.toSet()
        repeat(50) { i -> assertTrue("manca 'concorrente $i'", "concorrente $i" in descrizioni) }
    }

    @Test
    fun `voci di categorie diverse restano tutte, non solo la prima`() = runBlocking {
        AttivitaLogger.azioneUtente("prima azione")
        AttivitaLogger.integrazione("seconda voce", EsitoRegistro.SUCCESSO, 120L)
        AttivitaLogger.errore("terza voce", "dettaglio")
        AttivitaLogger.attendiScrittureInSospesoPerTest()

        val voci = AttivitaLogger.leggiRecenti()

        assertEquals(3, voci.size)
        assertEquals(setOf("prima azione", "seconda voce", "terza voce"), voci.map { it.descrizione }.toSet())
    }

    @Test
    fun `svuota rimuove tutte le voci scritte in precedenza`() = runBlocking {
        repeat(5) { i -> AttivitaLogger.azioneUtente("azione $i") }
        AttivitaLogger.attendiScrittureInSospesoPerTest()
        assertEquals(5, AttivitaLogger.leggiRecenti().size)

        AttivitaLogger.svuota()

        assertTrue(AttivitaLogger.leggiRecenti().isEmpty())
    }
}
