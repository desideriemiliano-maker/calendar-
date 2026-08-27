package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Associa un evento scritto sul Calendar Provider ([calendarEventId], l'ID restituito
 * dall'insert) alla sua esecuzione ([esecuzioneId]). Serve perché il Calendar Provider non
 * permette a un'app normale di taggare gli eventi con dati propri (ExtendedProperties è
 * riservato ai sync adapter), quindi l'associazione va tenuta qui invece che sul provider.
 */
@Entity(
    tableName = "evento_creato_calendario",
    foreignKeys = [
        ForeignKey(
            entity = EsecuzioneCreataEntity::class,
            parentColumns = ["id"],
            childColumns = ["esecuzioneId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("esecuzioneId")]
)
data class EventoCreatoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val esecuzioneId: String,
    val calendarEventId: Long
)
