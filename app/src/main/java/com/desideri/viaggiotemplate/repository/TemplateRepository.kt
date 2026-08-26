package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.TemplateDao
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotEntity
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TemplateRepository(private val dao: TemplateDao) {

    fun osservaTemplateEntities(): Flow<List<TemplateEntity>> = dao.osservaTuttiITemplate()

    /** Carica un Template completo (con slot e candidati) come modello di dominio. */
    suspend fun getTemplate(id: String): Template? {
        val entity = dao.getTemplate(id) ?: return null
        val slotConCandidati = dao.getSlotConCandidati(id)
        val slots = slotConCandidati.map { sc ->
            TemplateSlot(
                id = sc.slot.id,
                ordine = sc.slot.ordine,
                ancora = sc.slot.ancora,
                trattaCandidatiIds = sc.candidati.map { it.trattaId },
                trattaSelezionataId = sc.slot.trattaSelezionataId,
                notificaOverride = sc.slot.notificaOverride?.let { Notifica.valueOf(it) }
            )
        }
        return Template(id = entity.id, nome = entity.nome, slots = slots, ordine = entity.ordine, colore = entity.colore)
    }

    suspend fun salva(template: Template) {
        val templateEntity = TemplateEntity(id = template.id, nome = template.nome, ordine = template.ordine, colore = template.colore)
        val slotEntities = template.slots.map {
            TemplateSlotEntity(
                id = it.id,
                templateId = template.id,
                ordine = it.ordine,
                ancora = it.ancora,
                trattaSelezionataId = it.trattaSelezionataId,
                notificaOverride = it.notificaOverride?.name
            )
        }
        val candidatiPerSlot = template.slots.associate { it.id to it.trattaCandidatiIds }
        dao.salvaTemplateCompleto(templateEntity, slotEntities, candidatiPerSlot)
    }

    suspend fun elimina(template: Template) {
        dao.eliminaTemplate(TemplateEntity(id = template.id, nome = template.nome))
    }

    /** Scambia la posizione (ordine) di due template nella libreria. */
    suspend fun scambiaOrdine(a: TemplateEntity, b: TemplateEntity) {
        dao.aggiornaOrdine(a.id, b.ordine)
        dao.aggiornaOrdine(b.id, a.ordine)
    }

    fun nuovoId(): String = UUID.randomUUID().toString()
}
