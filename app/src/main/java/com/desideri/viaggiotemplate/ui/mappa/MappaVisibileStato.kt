package com.desideri.viaggiotemplate.ui.mappa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Contatore globale di quante mappe (vedi `MappaPercorsoScreen`) sono attualmente composte sullo
 * schermo, letto da `AppNavigation` per disattivare esplicitamente lo swipe di cambio sezione
 * mentre una mappa è visibile.
 *
 * Necessario perché l'assunzione originale dello swipe (vedi commit a29a6b3: "un componente
 * scorrevole annidato consuma il drag per primo nella stessa pass, quindi il rilevatore a monte lo
 * trova già consumato e si ferma da solo") NON vale per l'AndroidView di osmdroid: gestisce i
 * tocchi nel sistema di dispatch nativo delle View (onTouchEvent/requestDisallowInterceptTouchEvent),
 * un mondo separato da quello di Compose - il pan della mappa lì non marca il PointerInputChange di
 * Compose come consumato, quindi il detectHorizontalDragGestures a monte lo vede comunque e cambia
 * sezione mentre l'utente sta solo spostando la mappa. Va quindi disattivato esplicitamente, non
 * si può contare sulla propagazione/consumo del gesto.
 *
 * Un contatore, non un semplice booleano: corretto anche nel caso limite di più mappe presenti
 * insieme nella composizione per un istante (transizione tra due schermate), a prescindere
 * dall'ordine in cui montano/smontano.
 */
object MappaVisibileStato {
    private var contatore by mutableStateOf(0)

    val visibile: Boolean get() = contatore > 0

    fun incrementa() {
        contatore++
    }

    fun decrementa() {
        contatore = (contatore - 1).coerceAtLeast(0)
    }
}
