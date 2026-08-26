package com.desideri.viaggiotemplate.domain.calcolo

import com.desideri.viaggiotemplate.domain.model.Tratta
import java.time.LocalTime

/**
 * Risultato del calcolo per una singola tratta del template.
 *
 * - `inizioReale`/`fineReale`: orari effettivi, usati per il titolo dell'evento.
 * - `inizioBlocco`/`fineBlocco`: orari arrotondati secondo la configurazione della tratta,
 *   usati per DTSTART/DTEND dell'evento calendario.
 * - `alternative`: presente solo per slot con più candidati (destinazioni alternative);
 *   ciascuna voce è il risultato che si otterrebbe scegliendo quella tratta al posto
 *   di quella attualmente selezionata.
 */
data class EventoCalcolato(
    val templateSlotId: String,
    val tratta: Tratta,
    val inizioReale: LocalTime,
    val fineReale: LocalTime,
    val inizioBlocco: LocalTime,
    val fineBlocco: LocalTime,
    val alternative: List<EventoCalcolato> = emptyList(),
    /**
     * True se la tratta è TRENO/AEREO senza alcun orario configurato: `inizioReale`/`fineReale`
     * sono un placeholder (partenza = arrivo), non un orario reale — va aggiornato con la
     * ricerca in tempo reale (SBB/Trenitalia) prima di scrivere l'evento a calendario.
     */
    val orarioDaConfermare: Boolean = false
) {
    fun titolo(): String = tratta.titoloTemplate
        .replace("{oraPartenza}", inizioReale.toStringHHmm())
        .replace("{oraArrivo}", fineReale.toStringHHmm())
        .replace("{luogoPartenza}", tratta.luogoPartenza)
        .replace("{luogoArrivo}", tratta.luogoArrivo)
}

fun LocalTime.toStringHHmm(): String = "%02d:%02d".format(hour, minute)
