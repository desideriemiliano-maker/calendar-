package com.desideri.viaggiotemplate.domain.model

import java.time.LocalTime

/**
 * Uno slot ferroviario non ricorrente: un orario specifico (es. un treno straordinario
 * o un unico treno diretto della giornata), a differenza di [OpzioneOrario] che descrive
 * invece un pattern che si ripete ogni ora/N ore.
 *
 * Se `arrivo` è precedente a `partenza`, si intende che l'arrivo avviene dopo mezzanotte.
 */
data class OrarioFisso(
    val id: String,
    val partenza: LocalTime,
    val arrivo: LocalTime,
    val etichetta: String? = null
)
