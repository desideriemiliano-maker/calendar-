package com.desideri.viaggiotemplate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.desideri.viaggiotemplate.data.local.dao.EsecuzioneCreataDao
import com.desideri.viaggiotemplate.data.local.dao.TemplateDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.data.local.entities.EsecuzioneCreataEntity
import com.desideri.viaggiotemplate.data.local.entities.EventoCreatoEntity
import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.OrarioFissoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotCandidatoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity

@Database(
    entities = [
        TrattaEntity::class,
        OpzioneOrarioEntity::class,
        OrarioFissoEntity::class,
        TemplateEntity::class,
        TemplateSlotCandidatoEntity::class,
        TemplateSlotEntity::class,
        EsecuzioneCreataEntity::class,
        EventoCreatoEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trattaDao(): TrattaDao
    abstract fun templateDao(): TemplateDao
    abstract fun esecuzioneCreataDao(): EsecuzioneCreataDao
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

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `orario_fisso` (
                `id` TEXT NOT NULL,
                `trattaId` TEXT NOT NULL,
                `partenzaMinuti` INTEGER NOT NULL,
                `arrivoMinuti` INTEGER NOT NULL,
                `etichetta` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`trattaId`) REFERENCES `tratta`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN vettore TEXT")
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN notifica TEXT NOT NULL DEFAULT 'NESSUNA'")
    }
}

val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE template_slot ADD COLUMN notificaOverride TEXT")
    }
}

val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `esecuzione_creata` (
                `id` TEXT NOT NULL,
                `dataCreazione` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evento_creato_calendario` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `esecuzioneId` TEXT NOT NULL,
                `calendarEventId` INTEGER NOT NULL,
                FOREIGN KEY(`esecuzioneId`) REFERENCES `esecuzione_creata`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evento_creato_calendario_esecuzioneId` ON `evento_creato_calendario` (`esecuzioneId`)")
    }
}

val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE esecuzione_creata ADD COLUMN templateNome TEXT")
        db.execSQL("ALTER TABLE esecuzione_creata ADD COLUMN templateColore INTEGER")
    }
}
