package com.desideri.viaggiotemplate.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.OrarioFissoEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaConOpzioni
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity
import kotlinx.coroutines.flow.Flow

/** Proiezione minima per elencare le tratte che bloccano l'eliminazione di un Luogo. */
data class TrattaUsoLuogo(val nome: String, val luogoPartenzaId: String, val luogoArrivoId: String)

@androidx.room.Dao
interface TrattaDao {

    @Transaction
    @Query("SELECT * FROM tratta ORDER BY ordine")
    fun osservaTutte(): Flow<List<TrattaConOpzioni>>

    @Transaction
    @Query("SELECT * FROM tratta WHERE id = :id")
    suspend fun getPerId(id: String): TrattaConOpzioni?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisci(tratta: TrattaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciOpzioni(opzioni: List<OpzioneOrarioEntity>)

    @Query("DELETE FROM opzione_orario WHERE trattaId = :trattaId")
    suspend fun eliminaOpzioniDiTratta(trattaId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciOrariFissi(orariFissi: List<OrarioFissoEntity>)

    @Query("DELETE FROM orario_fisso WHERE trattaId = :trattaId")
    suspend fun eliminaOrariFissiDiTratta(trattaId: String)

    @Delete
    suspend fun elimina(tratta: TrattaEntity)

    @Query("UPDATE tratta SET ordine = :ordine WHERE id = :id")
    suspend fun aggiornaOrdine(id: String, ordine: Int)

    /** Tratte che usano questo luogo come partenza o arrivo: usato per bloccare l'eliminazione di un Luogo ancora in uso, mostrando all'utente quali tratte lo bloccano. */
    @Query("SELECT nome, luogoPartenzaId, luogoArrivoId FROM tratta WHERE luogoPartenzaId = :luogoId OR luogoArrivoId = :luogoId ORDER BY ordine")
    suspend fun getPerLuogo(luogoId: String): List<TrattaUsoLuogo>

    @Transaction
    suspend fun salvaConOpzioni(tratta: TrattaEntity, opzioni: List<OpzioneOrarioEntity>, orariFissi: List<OrarioFissoEntity>) {
        inserisci(tratta)
        eliminaOpzioniDiTratta(tratta.id)
        if (opzioni.isNotEmpty()) inserisciOpzioni(opzioni)
        eliminaOrariFissiDiTratta(tratta.id)
        if (orariFissi.isNotEmpty()) inserisciOrariFissi(orariFissi)
    }
}
