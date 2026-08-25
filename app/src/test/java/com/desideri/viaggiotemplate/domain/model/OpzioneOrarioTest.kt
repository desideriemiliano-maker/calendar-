package com.desideri.viaggiotemplate.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpzioneOrarioTest {

    @Test
    fun `cadenza 1 e' attiva in ogni ora indipendentemente dalla parita`() {
        val opzione = OpzioneOrario(id = "o", minutoPartenza = 0, cadenzaOre = 1, offsetOreArrivo = 0, minutoArrivo = 15)
        (0..23).forEach { ora -> assertTrue("ora $ora dovrebbe essere attiva", opzione.èAttivoNellOra(ora)) }
    }

    @Test
    fun `cadenza 2 con parita pari e' attiva solo nelle ore pari`() {
        val opzione = OpzioneOrario(id = "o", minutoPartenza = 0, cadenzaOre = 2, parita = 0, offsetOreArrivo = 0, minutoArrivo = 15)
        assertTrue(opzione.èAttivoNellOra(0))
        assertTrue(opzione.èAttivoNellOra(14))
        assertFalse(opzione.èAttivoNellOra(13))
        assertFalse(opzione.èAttivoNellOra(21))
    }

    @Test
    fun `cadenza 2 con parita dispari e' attiva solo nelle ore dispari`() {
        val opzione = OpzioneOrario(id = "o", minutoPartenza = 43, cadenzaOre = 2, parita = 1, offsetOreArrivo = 1, minutoArrivo = 34)
        assertTrue(opzione.èAttivoNellOra(13))
        assertTrue(opzione.èAttivoNellOra(17))
        assertFalse(opzione.èAttivoNellOra(12))
        assertFalse(opzione.èAttivoNellOra(0))
    }

    @Test
    fun `cadenza maggiore di 2 rispetta il resto della divisione`() {
        // ogni 3 ore, a partire dalle ore con resto 1 (1, 4, 7, 10, 13, 16, 19, 22)
        val opzione = OpzioneOrario(id = "o", minutoPartenza = 0, cadenzaOre = 3, parita = 1, offsetOreArrivo = 0, minutoArrivo = 20)
        assertTrue(opzione.èAttivoNellOra(1))
        assertTrue(opzione.èAttivoNellOra(4))
        assertFalse(opzione.èAttivoNellOra(2))
        assertFalse(opzione.èAttivoNellOra(3))
    }
}
