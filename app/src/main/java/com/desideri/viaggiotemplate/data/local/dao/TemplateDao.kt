package com.desideri.viaggiotemplate.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotCandidatoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotConCandidati
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotEntity
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface TemplateDao {

    @Query("SELECT * FROM template ORDER BY ordine")
    fun osservaTuttiITemplate(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM template WHERE id = :id")
    suspend fun getTemplate(id: String): TemplateEntity?

    @Transaction
    @Query("SELECT * FROM template_slot WHERE templateId = :templateId ORDER BY ordine")
    suspend fun getSlotConCandidati(templateId: String): List<TemplateSlotConCandidati>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciTemplate(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciSlot(slot: List<TemplateSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciCandidati(candidati: List<TemplateSlotCandidatoEntity>)

    @Query("DELETE FROM template_slot WHERE templateId = :templateId")
    suspend fun eliminaSlotDiTemplate(templateId: String)

    @Delete
    suspend fun eliminaTemplate(template: TemplateEntity)

    @Query("UPDATE template SET ordine = :ordine WHERE id = :id")
    suspend fun aggiornaOrdine(id: String, ordine: Int)

    /**
     * Sostituisce interamente slot e candidati di un template (approccio "replace all",
     * più semplice da ragionare quando si salva dall'editor).
     */
    @Transaction
    suspend fun salvaTemplateCompleto(
        template: TemplateEntity,
        slot: List<TemplateSlotEntity>,
        candidatiPerSlot: Map<String, List<String>> // templateSlotId -> lista trattaId
    ) {
        inserisciTemplate(template)
        eliminaSlotDiTemplate(template.id) // CASCADE elimina anche i candidati vecchi
        inserisciSlot(slot)
        val candidati = candidatiPerSlot.flatMap { (slotId, trattaIds) ->
            trattaIds.map { TemplateSlotCandidatoEntity(templateSlotId = slotId, trattaId = it) }
        }
        if (candidati.isNotEmpty()) inserisciCandidati(candidati)
    }
}
