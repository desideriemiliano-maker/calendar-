package com.desideri.viaggiotemplate.domain.model

/**
 * Un luogo riutilizzabile come partenza o arrivo di una tratta: nome per la visualizzazione e
 * ricerca orari, indirizzo opzionale usato per la navigazione (solo tratte AUTO in arrivo qui).
 */
data class Luogo(
    val id: String,
    val nome: String,
    val indirizzo: String? = null
)
