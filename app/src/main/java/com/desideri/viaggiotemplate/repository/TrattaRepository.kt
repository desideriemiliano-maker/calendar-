package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.domain.model.Tratta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TrattaRepository(private val dao: TrattaDao) {

    fun osservaTratte(): Flow<List<Tratta>> =
        dao.osservaTutte().map { lista -> lista.map { it.toDomain() } }

    suspend fun getTratta(id: String): Tratta? = dao.getPerId(id)?.toDomain()

    suspend fun salva(tratta: Tratta) {
        dao.salvaConOpzioni(tratta.toEntity(), tratta.opzioniToEntity())
    }

    suspend fun elimina(tratta: Tratta) {
        dao.elimina(tratta.toEntity())
    }

    /** Scambia la posizione (ordine) di due tratte nella libreria. */
    suspend fun scambiaOrdine(a: Tratta, b: Tratta) {
        dao.aggiornaOrdine(a.id, b.ordine)
        dao.aggiornaOrdine(b.id, a.ordine)
    }

    fun nuovoId(): String = UUID.randomUUID().toString()
}
