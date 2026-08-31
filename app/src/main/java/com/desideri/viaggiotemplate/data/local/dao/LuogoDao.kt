package com.desideri.viaggiotemplate.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.desideri.viaggiotemplate.data.local.entities.LuogoEntity
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface LuogoDao {

    @Query("SELECT * FROM luogo ORDER BY ordine")
    fun osservaTutti(): Flow<List<LuogoEntity>>

    @Query("SELECT * FROM luogo WHERE id = :id")
    suspend fun getPerId(id: String): LuogoEntity?

    /**
     * Solo per un luogo nuovo: NON usare onConflict = REPLACE per aggiornare un luogo esistente.
     * REPLACE su conflitto di chiave primaria esegue una vera DELETE + INSERT a livello SQLite, e
     * quella DELETE innesca l'azione ON DELETE RESTRICT della FK da `tratta` verso questo luogo se
     * è già usato da una tratta, facendo fallire il salvataggio invece di aggiornarlo. Per
     * modificare un luogo esistente usa [aggiorna], che è una vera UPDATE e non tocca le FK.
     */
    @Insert
    suspend fun inserisci(luogo: LuogoEntity)

    @Update
    suspend fun aggiorna(luogo: LuogoEntity)

    @Delete
    suspend fun elimina(luogo: LuogoEntity)

    @Query("UPDATE luogo SET ordine = :ordine WHERE id = :id")
    suspend fun aggiornaOrdine(id: String, ordine: Int)
}
