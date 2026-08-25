package com.desideri.viaggiotemplate.domain.model

import java.time.LocalTime

/**
 * Una tratta riutilizzabile della libreria: treno, auto o riunione.
 *
 * Il "margine" rappresenta sempre il buffer richiesto prima che QUESTA tratta possa
 * iniziare, calcolato rispetto alla fine della tratta precedente nel template.
 * Per treni con più orari possibili (es. Roma Termini -> Pomezia con slot :06/:36/:42),
 * `opzioniOrario` contiene tutti i pattern; il motore di calcolo sceglie quello più
 * adatto in base alla direzione di propagazione (avanti o indietro).
 */
data class Tratta(
    val id: String,
    val nome: String,
    val tipo: TipoTratta,
    val luogoPartenza: String,
    val luogoArrivo: String,
    val durataMinutiReale: Int,           // usato per AUTO/RIUNIONE; ignorato per TRENO (deriva dagli slot)
    val margineMinuti: Int,
    val arrotondaInizio: Arrotondamento = Arrotondamento.DIFETTO,
    val arrotondaFine: Arrotondamento = Arrotondamento.ECCESSO,
    val stepArrotondamentoMinuti: Int = 10,
    val titoloTemplate: String = "{oraPartenza} {luogoPartenza} / {luogoArrivo} {oraArrivo}",
    val opzioniOrario: List<OpzioneOrario> = emptyList(),
    /**
     * Orari fissi (non ricorrenti) alternativi ai pattern di `opzioniOrario`, solo per TRENO.
     * Quando questa tratta è l'ancora del calcolo, l'utente sceglie in Esegui quale usare tra
     * fisso e ricorrente; quando non è l'ancora, il motore di calcolo sceglie automaticamente
     * il migliore tra i due in base alle condizioni delle tratte adiacenti.
     */
    val orariFissi: List<OrarioFisso> = emptyList(),
    val ordine: Int = 0,
    /** Colore ARGB della card in libreria; null = colore grigio di default del tema. */
    val colore: Int? = null,
    /**
     * Orario di inizio predefinito (tipicamente per RIUNIONE): usato insieme a
     * `durataMinutiReale` per proporre inizio/fine di default quando questa tratta
     * è usata come ancora del calcolo. Null per le tratte senza un orario fisso.
     */
    val orarioInizioDefault: LocalTime? = null
) {
    companion object {
        /** Margine di default suggerito in base al tipo, coerente con quanto validato negli strumenti HTML. */
        fun margineDefaultPerTipo(tipo: TipoTratta): Int = when (tipo) {
            TipoTratta.TRENO -> 20
            TipoTratta.AUTO, TipoTratta.RIUNIONE -> 5
            TipoTratta.A_PIEDI -> 0
        }
    }
}
