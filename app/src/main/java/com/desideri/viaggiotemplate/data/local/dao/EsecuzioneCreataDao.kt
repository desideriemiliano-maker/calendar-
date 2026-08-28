package com.desideri.viaggiotemplate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.desideri.viaggiotemplate.data.local.entities.EsecuzioneCreataEntity
import com.desideri.viaggiotemplate.data.local.entities.EventoCreatoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EsecuzioneCreataDao {

    @Insert
    suspend fun inserisci(entity: EsecuzioneCreataEntity)

    @Insert
    suspend fun inserisciEventi(eventi: List<EventoCreatoEntity>)

    @Query("SELECT * FROM esecuzione_creata ORDER BY dataCreazione DESC")
    fun osservaTutte(): Flow<List<EsecuzioneCreataEntity>>

    @Query("SELECT calendarEventId FROM evento_creato_calendario WHERE esecuzioneId = :esecuzioneId")
    suspend fun eventIdsPerEsecuzione(esecuzioneId: String): List<Long>

    /** Elimina la registrazione locale dell'esecuzione: gli eventi collegati vengono rimossi a cascata (ON DELETE CASCADE). */
    @Query("DELETE FROM esecuzione_creata WHERE id = :id")
    suspend fun elimina(id: String)

    @Transaction
    suspend fun registraEsecuzione(esecuzione: EsecuzioneCreataEntity, eventi: List<EventoCreatoEntity>) {
        inserisci(esecuzione)
        if (eventi.isNotEmpty()) inserisciEventi(eventi)
    }
}
