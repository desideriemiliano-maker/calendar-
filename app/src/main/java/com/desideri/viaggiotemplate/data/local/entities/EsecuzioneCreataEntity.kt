package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Traccia una singola esecuzione di "Aggiungi al calendario": [id] è lo stesso UUID con cui gli
 * eventi corrispondenti sono associati in evento_creato_calendario, [inizioPrimoEvento] è l'orario
 * di inizio (quello scritto su DTSTART) del più mattiniero tra gli eventi di quella esecuzione —
 * più intuitivo da cercare rispetto al momento in cui è stato premuto "Aggiungi al calendario",
 * che può non coincidere con il giorno del viaggio pianificato.
 * Il nome della colonna resta "dataCreazione" (la tabella nasceva con quel significato) per non
 * introdurre una migrazione di rinomina solo cosmetica.
 */
@Entity(tableName = "esecuzione_creata")
data class EsecuzioneCreataEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "dataCreazione") val inizioPrimoEvento: Long,
    /** Nome e colore del Template usato, congelati al momento della scrittura: sopravvivono a una rinomina o eliminazione successiva del Template. */
    val templateNome: String? = null,
    val templateColore: Int? = null
)
