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
    val longitudine: Double? = null
)
