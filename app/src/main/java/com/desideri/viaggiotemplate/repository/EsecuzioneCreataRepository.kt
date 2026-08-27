package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.EsecuzioneCreataDao
import com.desideri.viaggiotemplate.data.local.entities.EsecuzioneCreataEntity
import com.desideri.viaggiotemplate.data.local.entities.EventoCreatoEntity
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class EsecuzioneCreataRepository(private val dao: EsecuzioneCreataDao) {

    fun osservaTutte(): Flow<List<EsecuzioneCreata>> =
        dao.osservaTutte().map { lista -> lista.map { it.toDomain() } }

    /** Registra una nuova esecuzione con gli ID (Calendar Provider) degli eventi che ha scritto. */
    suspend fun registra(id: String, eventIds: List<Long>, dataCreazione: Instant = Instant.now()) {
        dao.registraEsecuzione(
            EsecuzioneCreataEntity(id = id, dataCreazione = dataCreazione.toEpochMilli()),
            eventIds.map { EventoCreatoEntity(esecuzioneId = id, calendarEventId = it) }
        )
    }

    suspend fun eventIdsPer(esecuzioneId: String): List<Long> = dao.eventIdsPerEsecuzione(esecuzioneId)
}

private fun EsecuzioneCreataEntity.toDomain() = EsecuzioneCreata(id = id, dataCreazione = Instant.ofEpochMilli(dataCreazione))
