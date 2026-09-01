package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.EsecuzioneCreataDao
import com.desideri.viaggiotemplate.data.local.entities.EsecuzioneCreataEntity
import com.desideri.viaggiotemplate.data.local.entities.EventoCreatoEntity
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.domain.calendar.LuogoCongelato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.model.IconaLuogo
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class EsecuzioneCreataRepository(private val dao: EsecuzioneCreataDao) {

    fun osservaTutte(): Flow<List<EsecuzioneCreata>> =
        dao.osservaTutte().map { lista -> lista.map { it.toDomain() } }

    /**
     * Registra una nuova esecuzione: [posizioni] porta sia l'ID (Calendar Provider) di ciascun
     * evento scritto sia la sua posizione congelata (vedi [PosizioneEventoCreato]), per la vista
     * mappa di "Eventi creati". [inizioPrimoEvento] è l'orario di inizio del più mattiniero tra
     * questi eventi, nome/colore quelli del Template usato.
     */
    suspend fun registra(
        id: String,
        posizioni: List<PosizioneEventoCreato>,
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
            posizioni.map { it.toEntity(id) }
        )
    }

    suspend fun eventIdsPer(esecuzioneId: String): List<Long> = dao.eventIdsPerEsecuzione(esecuzioneId)

    /** Posizioni congelate degli eventi di questa esecuzione, per la vista mappa. Righe pre-esistenti alla funzionalità: tutti i campi posizione null. */
    suspend fun posizioniPer(esecuzioneId: String): List<PosizioneEventoCreato> =
        dao.posizioniPerEsecuzione(esecuzioneId).map { it.toDomainPosizione() }

    /** Elimina la registrazione locale dell'esecuzione (non tocca il Calendar Provider). */
    suspend fun elimina(esecuzioneId: String) = dao.elimina(esecuzioneId)
}

private fun EsecuzioneCreataEntity.toDomain() = EsecuzioneCreata(
    id = id,
    inizioPrimoEvento = Instant.ofEpochMilli(inizioPrimoEvento),
    templateNome = templateNome,
    templateColore = templateColore
)

private fun PosizioneEventoCreato.toEntity(esecuzioneId: String) = EventoCreatoEntity(
    esecuzioneId = esecuzioneId,
    calendarEventId = calendarEventId,
    tipoTratta = tipoTratta?.name,
    partenzaLuogoId = partenza?.luogoId,
    partenzaNome = partenza?.nome,
    partenzaIndirizzo = partenza?.indirizzo,
    partenzaLatitudine = partenza?.latitudine,
    partenzaLongitudine = partenza?.longitudine,
    partenzaColore = partenza?.colore,
    partenzaIcona = partenza?.icona?.name,
    arrivoLuogoId = arrivo?.luogoId,
    arrivoNome = arrivo?.nome,
    arrivoIndirizzo = arrivo?.indirizzo,
    arrivoLatitudine = arrivo?.latitudine,
    arrivoLongitudine = arrivo?.longitudine,
    arrivoColore = arrivo?.colore,
    arrivoIcona = arrivo?.icona?.name
)

private fun EventoCreatoEntity.toDomainPosizione() = PosizioneEventoCreato(
    calendarEventId = calendarEventId,
    tipoTratta = tipoTratta?.let { TipoTratta.valueOf(it) },
    partenza = partenzaLuogoId?.let { luogoId ->
        LuogoCongelato(
            luogoId = luogoId,
            nome = partenzaNome.orEmpty(),
            indirizzo = partenzaIndirizzo,
            latitudine = partenzaLatitudine,
            longitudine = partenzaLongitudine,
            colore = partenzaColore,
            icona = IconaLuogo.daNomeOrNull(partenzaIcona)
        )
    },
    arrivo = arrivoLuogoId?.let { luogoId ->
        LuogoCongelato(
            luogoId = luogoId,
            nome = arrivoNome.orEmpty(),
            indirizzo = arrivoIndirizzo,
            latitudine = arrivoLatitudine,
            longitudine = arrivoLongitudine,
            colore = arrivoColore,
            icona = IconaLuogo.daNomeOrNull(arrivoIcona)
        )
    }
)
