package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "luogo")
data class LuogoEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val indirizzo: String? = null
)
