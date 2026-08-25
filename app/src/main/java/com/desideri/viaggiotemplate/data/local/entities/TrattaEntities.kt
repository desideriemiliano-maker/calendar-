package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "tratta")
data class TrattaEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val tipo: String,                 // TipoTratta.name
    val luogoPartenza: String,
    val luogoArrivo: String,
    val durataMinutiReale: Int,
    val margineMinuti: Int,
    val arrotondaInizio: String,       // Arrotondamento.name
    val arrotondaFine: String,
    val stepArrotondamentoMinuti: Int,
    val titoloTemplate: String,
    val ordine: Int = 0,
    val colore: Int? = null,
    /** Minuti dalla mezzanotte (0-1439); null se non impostato. */
    val orarioInizioDefaultMinuti: Int? = null
)

@Entity(
    tableName = "opzione_orario",
    foreignKeys = [
        ForeignKey(
            entity = TrattaEntity::class,
            parentColumns = ["id"],
            childColumns = ["trattaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OpzioneOrarioEntity(
    @PrimaryKey val id: String,
    val trattaId: String,
    val minutoPartenza: Int,
    val cadenzaOre: Int,
    val parita: Int?,
    val offsetOreArrivo: Int,
    val minutoArrivo: Int,
    val etichetta: String?
)

@Entity(
    tableName = "orario_fisso",
    foreignKeys = [
        ForeignKey(
            entity = TrattaEntity::class,
            parentColumns = ["id"],
            childColumns = ["trattaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OrarioFissoEntity(
    @PrimaryKey val id: String,
    val trattaId: String,
    /** Minuti dalla mezzanotte (0-1439). */
    val partenzaMinuti: Int,
    val arrivoMinuti: Int,
    val etichetta: String?
)

/** Proiezione con relazione 1-a-N per leggere una Tratta con tutte le sue opzioni orario e orari fissi. */
data class TrattaConOpzioni(
    @Embedded val tratta: TrattaEntity,
    @Relation(parentColumn = "id", entityColumn = "trattaId")
    val opzioni: List<OpzioneOrarioEntity>,
    @Relation(parentColumn = "id", entityColumn = "trattaId")
    val orariFissi: List<OrarioFissoEntity>
)
