package com.desideri.viaggiotemplate.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Test di [indiceSezioneDopoSwipe]: la logica pura dietro lo swipe orizzontale tra le sezioni della barra in basso (vedi AppNavigation). */
class AppNavigationSwipeTest {

    private val larghezzaSchermo = 1000
    private val numeroSezioni = 5

    @Test
    fun `swipe verso sinistra oltre soglia va alla sezione successiva`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = 1,
            trascinamentoOrizzontale = -300f, // -30% della larghezza, oltre la soglia 20%
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(2, indice)
    }

    @Test
    fun `swipe verso destra oltre soglia va alla sezione precedente`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = 2,
            trascinamentoOrizzontale = 300f,
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(1, indice)
    }

    @Test
    fun `sotto soglia non cambia sezione`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = 2,
            trascinamentoOrizzontale = 100f, // 10%, sotto la soglia 20%
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(2, indice)
    }

    @Test
    fun `esattamente alla soglia cambia sezione`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = 0,
            trascinamentoOrizzontale = -200f, // esattamente 20%
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(1, indice)
    }

    @Test
    fun `nessun wraparound oltre l'ultima sezione`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = numeroSezioni - 1,
            trascinamentoOrizzontale = -500f,
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(numeroSezioni - 1, indice)
    }

    @Test
    fun `nessun wraparound prima della prima sezione`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = 0,
            trascinamentoOrizzontale = 500f,
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(0, indice)
    }

    @Test
    fun `indice corrente non riconosciuto resta invariato`() {
        val indice = indiceSezioneDopoSwipe(
            indiceCorrente = -1,
            trascinamentoOrizzontale = -500f,
            larghezzaSchermoPx = larghezzaSchermo,
            numeroSezioni = numeroSezioni
        )
        assertEquals(-1, indice)
    }
}
