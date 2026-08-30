package com.desideri.viaggiotemplate.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateGpsTest {

    @Test
    fun `punto decimale con virgola e spazio come separatore`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45.4642, 9.1900"))
    }

    @Test
    fun `punto decimale senza spazio dopo la virgola`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45.4642,9.1900"))
    }

    @Test
    fun `punto decimale con spazio come unico separatore`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45.4642 9.1900"))
    }

    @Test
    fun `punto decimale con punto e virgola come separatore`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45.4642;9.1900"))
    }

    @Test
    fun `interi senza decimali con due gruppi`() {
        assertEquals(45.0 to 9.0, parseCoordinateGps("45, 9"))
    }

    @Test
    fun `virgola decimale su entrambi i numeri con separatore virgola`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45,4642, 9,1900"))
    }

    @Test
    fun `virgola decimale con separatore punto e virgola`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45,4642;9,1900"))
    }

    @Test
    fun `virgola decimale con separatore spazio`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45,4642 9,1900"))
    }

    @Test
    fun `virgola decimale con negativi su entrambi i numeri`() {
        assertEquals(-45.4642 to -9.19, parseCoordinateGps("-45,4642, -9,1900"))
    }

    @Test
    fun `virgola decimale con negativo solo sulla longitudine`() {
        assertEquals(45.4642 to -9.19, parseCoordinateGps("45,4642, -9,1900"))
    }

    @Test
    fun `punto decimale con negativo solo sulla latitudine`() {
        assertEquals(-45.4642 to 9.19, parseCoordinateGps("-45.4642, 9.1900"))
    }

    @Test
    fun `separatori ripetuti o misti vengono collassati in uno solo`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("45,4642 ,  9,1900"))
    }

    @Test
    fun `spazi superflui ai bordi vengono ignorati`() {
        assertEquals(45.4642 to 9.19, parseCoordinateGps("  45.4642, 9.1900  "))
    }

    @Test
    fun `formati misti punto e virgola tra i due numeri sono ambigui e fanno fallire il parsing`() {
        // "45.4642" (punto) e "9,1900" (virgola) messi insieme fanno 3 gruppi: né 2 né 4, quindi
        // ambiguo per costruzione. Deve degradare a errore, mai indovinare quale fosse l'intento.
        assertNull(parseCoordinateGps("45.4642, 9,1900"))
    }

    @Test
    fun `singolo numero con virgola decimale e ambiguo con due interi separati`() {
        // Caso limite noto e voluto: un solo numero con virgola decimale (es. "45,46" digitato a
        // metà) produce esattamente 2 gruppi, indistinguibile da "45" e "46" separati dal punto
        // come convenzione. La strategia a 2/4 gruppi risolve deliberatamente a favore del punto.
        assertEquals(45.0 to 46.0, parseCoordinateGps("45,46"))
    }

    @Test
    fun `tre gruppi sono incompleti e ambigui`() {
        assertNull(parseCoordinateGps("45,4642,9"))
    }

    @Test
    fun `cinque gruppi non hanno un'interpretazione valida`() {
        assertNull(parseCoordinateGps("45,4642,9,19,00"))
    }

    @Test
    fun `un solo numero senza secondo valore e incompleto`() {
        assertNull(parseCoordinateGps("45.4642"))
    }

    @Test
    fun `testo vuoto o solo spazi ritorna null`() {
        assertNull(parseCoordinateGps(""))
        assertNull(parseCoordinateGps("   "))
    }

    @Test
    fun `testo non numerico ritorna null invece di lanciare un'eccezione`() {
        assertNull(parseCoordinateGps("non sono coordinate"))
    }

    @Test
    fun `testo parzialmente numerico con due gruppi ritorna null`() {
        assertNull(parseCoordinateGps("45.46a, 9.19"))
    }

    @Test
    fun `nessuna eccezione anche con solo separatori`() {
        assertNull(parseCoordinateGps(",,,"))
    }
}
