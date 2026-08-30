package com.desideri.viaggiotemplate.domain.model

/**
 * Un luogo riutilizzabile come partenza o arrivo di una tratta: nome per la visualizzazione e
 * ricerca orari, indirizzo opzionale usato per la navigazione (solo tratte AUTO in arrivo qui).
 *
 * [latitudine]/[longitudine] sono un'alternativa opzionale all'indirizzo testuale: quando
 * presenti hanno priorità sull'indirizzo per l'evento calendario e la navigazione, essendo più
 * precise (vedi [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter]).
 */
data class Luogo(
    val id: String,
    val nome: String,
    val indirizzo: String? = null,
    val latitudine: Double? = null,
    val longitudine: Double? = null
)
