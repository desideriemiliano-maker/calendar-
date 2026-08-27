package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Traccia una singola esecuzione di "Aggiungi al calendario": [id] è lo stesso UUID con cui gli
 * eventi corrispondenti sono taggati sul Calendar Provider (vedi CalendarWriter.inserisciEventi),
 * [dataCreazione] è il momento in cui quella scrittura è avvenuta.
 */
@Entity(tableName = "esecuzione_creata")
data class EsecuzioneCreataEntity(
    @PrimaryKey val id: String,
    val dataCreazione: Long
)
