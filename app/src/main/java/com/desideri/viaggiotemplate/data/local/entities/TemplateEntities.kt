package com.desideri.viaggiotemplate.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val nome: String,
    val ordine: Int = 0,
    val colore: Int? = null
)

@Entity(
    tableName = "template_slot",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemplateSlotEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val ordine: Int,
    val ancora: Boolean,
    val trattaSelezionataId: String
)

@Entity(
    tableName = "template_slot_candidato",
    foreignKeys = [
        ForeignKey(
            entity = TemplateSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateSlotId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemplateSlotCandidatoEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val templateSlotId: String,
    val trattaId: String
)

data class TemplateSlotConCandidati(
    @Embedded val slot: TemplateSlotEntity,
    @Relation(parentColumn = "id", entityColumn = "templateSlotId")
    val candidati: List<TemplateSlotCandidatoEntity>
)

/**
 * Nota: la composizione Template -> [TemplateSlotConCandidati] non usa @Relation annidate
 * (Room non supporta la mappatura automatica a due livelli su una POJO intermedia).
 * Viene costruita a mano nel repository con una funzione @Transaction sul DAO.
 */
data class TemplateConSlot(
    val template: TemplateEntity,
    val slot: List<TemplateSlotConCandidati>
)
