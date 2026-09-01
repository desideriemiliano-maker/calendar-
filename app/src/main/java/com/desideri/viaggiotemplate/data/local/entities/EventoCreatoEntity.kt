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
 *
 * Le colonne `partenza*`/`arrivo*`/`tipoTratta` sono la posizione congelata al momento della
 * scrittura (vedi [com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato]), per la
 * vista mappa di "Eventi creati": mai ri-risolte contro `tratta`/`luogo`, che possono cambiare o
 * essere eliminati dopo la creazione. Tutte nullable e tutte null insieme per gli eventi creati
 * prima dell'introduzione di questa funzionalità (migrazione 16→17) — per quelli la mappa non ha
 * nulla da mostrare.
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
    val calendarEventId: Long,
    /** TipoTratta.name della tratta di questo evento; null = posizione non congelata (evento pre-esistente). */
    val tipoTratta: String? = null,
    val partenzaLuogoId: String? = null,
    val partenzaNome: String? = null,
    val partenzaIndirizzo: String? = null,
    val partenzaLatitudine: Double? = null,
    val partenzaLongitudine: Double? = null,
    val partenzaColore: Int? = null,
    /** IconaLuogo.name della partenza; null = nessuna icona (o posizione non congelata). */
    val partenzaIcona: String? = null,
    val arrivoLuogoId: String? = null,
    val arrivoNome: String? = null,
    val arrivoIndirizzo: String? = null,
    val arrivoLatitudine: Double? = null,
    val arrivoLongitudine: Double? = null,
    val arrivoColore: Int? = null,
    /** IconaLuogo.name dell'arrivo; null = nessuna icona (o posizione non congelata). */
    val arrivoIcona: String? = null
)
