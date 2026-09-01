package com.desideri.viaggiotemplate.domain.model

/**
 * Un luogo riutilizzabile come partenza o arrivo di una tratta: nome per la visualizzazione e
 * ricerca orari, indirizzo opzionale usato per la navigazione (solo tratte AUTO in arrivo qui).
 *
 * [latitudine]/[longitudine] sono un'alternativa opzionale all'indirizzo testuale: quando
 * presenti hanno priorità sull'indirizzo per l'evento calendario e la navigazione, essendo più
 * precise (vedi [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter]).
 *
 * [colore] è lo stesso ARGB di [com.desideri.viaggiotemplate.domain.model.Tratta.colore]/
 * [com.desideri.viaggiotemplate.domain.model.Template.colore] (stessa palette, stesso significato:
 * null = grigio di default del tema), usato per lo sfondo della card in libreria e per il marker
 * di questo luogo nella vista mappa. [ordine] è la posizione nella lista, riordinabile a mano.
 *
 * [icona] è opzionale (null = nessuna icona, il comportamento di prima) e caratterizza il tipo di
 * luogo — vedi [IconaLuogo] — mostrata accanto al nome nella lista e come glifo del marker sulla
 * mappa, sempre sul disco colorato con [colore].
 */
data class Luogo(
    val id: String,
    val nome: String,
    val indirizzo: String? = null,
    val latitudine: Double? = null,
    val longitudine: Double? = null,
    val colore: Int? = null,
    val ordine: Int = 0,
    val icona: IconaLuogo? = null
)
