package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.Tratta

/**
 * Sequenza ordinata degli id Luogo attraversati dal template: partenza e arrivo di ogni tratta
 * selezionata, nell'ordine degli slot ([Template.slotsOrdinati]).
 *
 * Le ripetizioni consecutive sono collassate (il caso normale, in cui l'arrivo di una tratta
 * coincide con la partenza della successiva, produce una sola tappa). Nessun controllo nel resto
 * dell'app garantisce però questa continuità geografica (il motore di calcolo orari propaga solo
 * i tempi, non verifica i luoghi): se un template ha un "salto", le due tappe restano entrambe
 * distinte, in sequenza, invece di essere fuse per errore.
 *
 * Uno slot la cui tratta selezionata non è (più) tra [tratte] viene saltato silenziosamente:
 * capita solo per un template salvato con un riferimento a una tratta poi eliminata dalla libreria.
 */
fun sequenzaIdLuoghi(template: Template, tratte: List<Tratta>): List<String> {
    val tratteById = tratte.associateBy { it.id }
    val ids = mutableListOf<String>()
    template.slotsOrdinati.forEach { slot ->
        val tratta = tratteById[slot.trattaSelezionataId] ?: return@forEach
        if (ids.lastOrNull() != tratta.luogoPartenzaId) ids += tratta.luogoPartenzaId
        if (ids.lastOrNull() != tratta.luogoArrivoId) ids += tratta.luogoArrivoId
    }
    return ids
}
