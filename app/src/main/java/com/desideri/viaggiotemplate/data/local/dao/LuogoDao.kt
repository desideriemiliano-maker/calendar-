package com.desideri.viaggiotemplate.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.desideri.viaggiotemplate.data.local.entities.LuogoEntity
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface LuogoDao {

    @Query("SELECT * FROM luogo ORDER BY nome COLLATE NOCASE")
    fun osservaTutti(): Flow<List<LuogoEntity>>

    @Query("SELECT * FROM luogo WHERE id = :id")
    suspend fun getPerId(id: String): LuogoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisci(luogo: LuogoEntity)

    @Delete
    suspend fun elimina(luogo: LuogoEntity)
}
