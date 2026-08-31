package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.LuogoDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaUsoLuogo
import com.desideri.viaggiotemplate.domain.model.Luogo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Ruolo con cui una tratta usa un luogo: come partenza, come arrivo, o entrambi (tratta che parte e arriva nello stesso luogo). */
enum class RuoloLuogoInTratta { PARTENZA, ARRIVO, ENTRAMBI }

/** Una tratta che blocca l'eliminazione di un Luogo, con il ruolo che il luogo ha in essa. */
data class UsoTrattaLuogo(val nomeTratta: String, val ruolo: RuoloLuogoInTratta)

/** Esito di [LuogoRepository.elimina]: distingue l'eliminazione riuscita dal blocco per uso da parte di tratte esistenti. */
sealed class EsitoEliminazioneLuogo {
    data object Eliminato : EsitoEliminazioneLuogo()
    data class BloccatoDaTratte(val tratte: List<UsoTrattaLuogo>) : EsitoEliminazioneLuogo()
}

private fun TrattaUsoLuogo.ruoloPer(luogoId: String): RuoloLuogoInTratta = when {
    luogoPartenzaId == luogoId && luogoArrivoId == luogoId -> RuoloLuogoInTratta.ENTRAMBI
    luogoPartenzaId == luogoId -> RuoloLuogoInTratta.PARTENZA
    else -> RuoloLuogoInTratta.ARRIVO
}

class LuogoRepository(private val luogoDao: LuogoDao, private val trattaDao: TrattaDao) {

    fun osservaLuoghi(): Flow<List<Luogo>> =
        luogoDao.osservaTutti().map { lista -> lista.map { it.toDomain() } }

    suspend fun getLuogo(id: String): Luogo? = luogoDao.getPerId(id)?.toDomain()

    /** Inserisce un luogo nuovo o aggiorna uno esistente (mai una REPLACE: vedi [LuogoDao.inserisci]). */
    suspend fun salva(luogo: Luogo) {
        val entity = luogo.toEntity()
        if (luogoDao.getPerId(luogo.id) != null) {
            luogoDao.aggiorna(entity)
        } else {
            luogoDao.inserisci(entity)
        }
    }

    /** Blocca l'eliminazione se il luogo è ancora usato da almeno una tratta (come partenza o arrivo): l'utente deve prima spostare quelle tratte su un altro luogo. */
    suspend fun elimina(luogo: Luogo): EsitoEliminazioneLuogo {
        val usi = trattaDao.getPerLuogo(luogo.id)
        if (usi.isNotEmpty()) {
            return EsitoEliminazioneLuogo.BloccatoDaTratte(
                usi.map { UsoTrattaLuogo(it.nome, it.ruoloPer(luogo.id)) }
            )
        }
        luogoDao.elimina(luogo.toEntity())
        return EsitoEliminazioneLuogo.Eliminato
    }

    /** Scambia la posizione (ordine) di due luoghi nella libreria. */
    suspend fun scambiaOrdine(a: Luogo, b: Luogo) {
        luogoDao.aggiornaOrdine(a.id, b.ordine)
        luogoDao.aggiornaOrdine(b.id, a.ordine)
    }

    fun nuovoId(): String = UUID.randomUUID().toString()
}
