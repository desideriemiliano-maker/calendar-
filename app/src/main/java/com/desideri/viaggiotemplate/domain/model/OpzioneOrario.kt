package com.desideri.viaggiotemplate.domain.model

/**
 * Pattern di uno slot ferroviario ricorrente.
 *
 * Esempio: treno che parte al minuto 43 di ogni ora e arriva al minuto 29 dell'ora dopo:
 *   OpzioneOrario(minutoPartenza = 43, cadenzaOre = 1, parita = null, offsetOreArrivo = 1, minutoArrivo = 29)
 *
 * Esempio: treno che parte al minuto 43 solo nelle ore dispari (13, 15, 17...):
 *   OpzioneOrario(minutoPartenza = 43, cadenzaOre = 2, parita = 1, offsetOreArrivo = 1, minutoArrivo = 34)
 */
data class OpzioneOrario(
    val id: String,
    val minutoPartenza: Int,
    val cadenzaOre: Int = 1,        // 1 = ogni ora, 2 = ogni due ore, ecc.
    val parita: Int? = null,        // 0 = ore pari, 1 = ore dispari; usato solo se cadenzaOre > 1
    val offsetOreArrivo: Int,       // 0 = arriva nella stessa ora, 1 = ora dopo, ecc.
    val minutoArrivo: Int,
    val etichetta: String? = null   // opzionale, per distinguere slot multipli nella UI
) {
    /** Verifica se questo pattern ha uno slot nell'ora indicata (in base a cadenza/parità). */
    fun èAttivoNellOra(ora: Int): Boolean {
        if (cadenzaOre <= 1) return true
        val p = ((ora % cadenzaOre) + cadenzaOre) % cadenzaOre
        return p == (parita ?: 0)
    }
}
