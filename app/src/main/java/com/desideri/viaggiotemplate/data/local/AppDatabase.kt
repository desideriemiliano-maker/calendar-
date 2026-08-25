package com.desideri.viaggiotemplate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.desideri.viaggiotemplate.data.local.dao.TemplateDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotCandidatoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity

@Database(
    entities = [
        TrattaEntity::class,
        OpzioneOrarioEntity::class,
        TemplateEntity::class,
        TemplateSlotCandidatoEntity::class,
        TemplateSlotEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trattaDao(): TrattaDao
    abstract fun templateDao(): TemplateDao
}

/**
 * Migrazioni esplicite: NON usare fallbackToDestructiveMigration, cancellerebbe i dati
 * dell'utente ad ogni cambio di schema (è già successo una volta per errore).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN ordine INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE template ADD COLUMN ordine INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN colore INTEGER")
        db.execSQL("ALTER TABLE template ADD COLUMN colore INTEGER")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN orarioInizioDefaultMinuti INTEGER")
    }
}
