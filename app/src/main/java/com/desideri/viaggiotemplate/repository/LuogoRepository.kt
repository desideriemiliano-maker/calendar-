package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.dao.LuogoDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.domain.model.Luogo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Esito di [LuogoRepository.elimina]: distingue l'eliminazione riuscita dal blocco per uso da parte di tratte esistenti. */
sealed class EsitoEliminazioneLuogo {
    data object Eliminato : EsitoEliminazioneLuogo()
    data class BloccatoDaTratte(val numeroTratte: Int) : EsitoEliminazioneLuogo()
}

class LuogoRepository(private val luogoDao: LuogoDao, private val trattaDao: TrattaDao) {

    fun osservaLuoghi(): Flow<List<Luogo>> =
        luogoDao.osservaTutti().map { lista -> lista.map { it.toDomain() } }

    suspend fun getLuogo(id: String): Luogo? = luogoDao.getPerId(id)?.toDomain()

    suspend fun salva(luogo: Luogo) {
        luogoDao.inserisci(luogo.toEntity())
    }

    /** Blocca l'eliminazione se il luogo è ancora usato da almeno una tratta (come partenza o arrivo): l'utente deve prima spostare quelle tratte su un altro luogo. */
    suspend fun elimina(luogo: Luogo): EsitoEliminazioneLuogo {
        val usi = trattaDao.contaPerLuogo(luogo.id)
        if (usi > 0) return EsitoEliminazioneLuogo.BloccatoDaTratte(usi)
        luogoDao.elimina(luogo.toEntity())
        return EsitoEliminazioneLuogo.Eliminato
    }

    fun nuovoId(): String = UUID.randomUUID().toString()
}
