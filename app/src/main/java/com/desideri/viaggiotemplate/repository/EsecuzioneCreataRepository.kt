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

    /** Registra una nuova esecuzione con gli ID (Calendar Provider) degli eventi che ha scritto, l'orario di inizio del più mattiniero tra questi e nome/colore del Template usato. */
    suspend fun registra(
        id: String,
        eventIds: List<Long>,
        inizioPrimoEvento: Instant,
        templateNome: String?,
        templateColore: Int?
    ) {
        dao.registraEsecuzione(
            EsecuzioneCreataEntity(
                id = id,
                inizioPrimoEvento = inizioPrimoEvento.toEpochMilli(),
                templateNome = templateNome,
                templateColore = templateColore
            ),
            eventIds.map { EventoCreatoEntity(esecuzioneId = id, calendarEventId = it) }
        )
    }

    suspend fun eventIdsPer(esecuzioneId: String): List<Long> = dao.eventIdsPerEsecuzione(esecuzioneId)

    /** Elimina la registrazione locale dell'esecuzione (non tocca il Calendar Provider). */
    suspend fun elimina(esecuzioneId: String) = dao.elimina(esecuzioneId)
}

private fun EsecuzioneCreataEntity.toDomain() = EsecuzioneCreata(
    id = id,
    inizioPrimoEvento = Instant.ofEpochMilli(inizioPrimoEvento),
    templateNome = templateNome,
    templateColore = templateColore
)
