package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "luogo")
data class LuogoEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val indirizzo: String? = null,
    /** Coordinate GPS, alternative all'indirizzo testuale: se presenti hanno priorità sull'indirizzo per navigazione ed evento calendario. */
    val latitudine: Double? = null,
    val longitudine: Double? = null,
    /** Colore ARGB della card in libreria (e del marker sulla mappa); null = colore grigio di default del tema. */
    val colore: Int? = null,
    val ordine: Int = 0,
    /** IconaLuogo.name; null = nessuna icona. */
    val icona: String? = null
)
