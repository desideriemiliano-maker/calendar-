package com.desideri.viaggiotemplate.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.desideri.viaggiotemplate.data.local.AppDatabase
import com.desideri.viaggiotemplate.data.local.ImpostazioniStore
import com.desideri.viaggiotemplate.data.local.ItaloCredentialsStore
import com.desideri.viaggiotemplate.data.local.MIGRATION_1_2
import com.desideri.viaggiotemplate.data.local.MIGRATION_2_3
import com.desideri.viaggiotemplate.data.local.MIGRATION_3_4
import com.desideri.viaggiotemplate.data.local.MIGRATION_4_5
import com.desideri.viaggiotemplate.data.local.MIGRATION_5_6
import com.desideri.viaggiotemplate.data.local.MIGRATION_6_7
import com.desideri.viaggiotemplate.data.local.MIGRATION_7_8
import com.desideri.viaggiotemplate.data.local.MIGRATION_8_9
import com.desideri.viaggiotemplate.data.local.MIGRATION_9_10
import com.desideri.viaggiotemplate.data.local.MIGRATION_10_11
import com.desideri.viaggiotemplate.data.local.MIGRATION_11_12
import com.desideri.viaggiotemplate.data.local.MIGRATION_12_13
import com.desideri.viaggiotemplate.data.local.MIGRATION_13_14
import com.desideri.viaggiotemplate.data.local.MIGRATION_14_15
import com.desideri.viaggiotemplate.data.local.MIGRATION_15_16
import com.desideri.viaggiotemplate.data.local.MIGRATION_16_17
import com.desideri.viaggiotemplate.data.local.NOME_FILE_DATABASE
import com.desideri.viaggiotemplate.domain.backup.DatabaseBackupManager
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.repository.EsecuzioneCreataRepository
import com.desideri.viaggiotemplate.repository.LuogoRepository
import com.desideri.viaggiotemplate.repository.TemplateRepository
import com.desideri.viaggiotemplate.repository.TrattaRepository
import java.io.File

/**
 * Service locator minimale: se il progetto usa già Hilt/Koin, sostituire questa
 * classe con l'iniezione preferita. Va inizializzata una volta sola, es. in Application.onCreate().
 */
object AppContainer {
    private lateinit var database: AppDatabase

    lateinit var trattaRepository: TrattaRepository
        private set
    lateinit var luogoRepository: LuogoRepository
        private set
    lateinit var templateRepository: TemplateRepository
        private set
    lateinit var impostazioniStore: ImpostazioniStore
        private set
    lateinit var italoCredentialsStore: ItaloCredentialsStore
        private set
    lateinit var esecuzioneCreataRepository: EsecuzioneCreataRepository
        private set
    lateinit var databaseBackupManager: DatabaseBackupManager
        private set

    fun init(context: Context) {
        if (::database.isInitialized) return
        database = costruisciDatabase(context)
        trattaRepository = TrattaRepository(database.trattaDao())
        luogoRepository = LuogoRepository(database.luogoDao(), database.trattaDao())
        templateRepository = TemplateRepository(database.templateDao())
        impostazioniStore = ImpostazioniStore(context)
        italoCredentialsStore = ItaloCredentialsStore(context)
        esecuzioneCreataRepository = EsecuzioneCreataRepository(database.esecuzioneCreataDao())
        databaseBackupManager = DatabaseBackupManager(context.applicationContext)
    }

    private fun costruisciDatabase(context: Context): AppDatabase {
        // Letto PRIMA che Room apra il file: se il .db esiste già ma a uno schema precedente,
        // è la versione da cui si migrerà. Un solo punto di log invece di uno per ciascuna delle
        // Migration sopra (che altrimenti andrebbero toccate una per una, rischioso su codice
        // stabile per un beneficio pressoché nullo: su un device già aggiornato non rieseguono più).
        val versionePrimaDellApertura = versioneSchemaEsistente(context)

        val database = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOME_FILE_DATABASE)
            // Migrazioni esplicite: preservano i dati dell'utente ad ogni cambio di schema.
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17
            )
            .build()

        // Forza l'apertura (Room la farebbe comunque al primo accesso a un DAO, tra pochissimo):
        // così la migrazione, se necessaria, è già avvenuta quando si logga qui sotto.
        val versioneDopoLApertura = database.openHelper.readableDatabase.version
        if (versionePrimaDellApertura != null && versionePrimaDellApertura != versioneDopoLApertura) {
            AttivitaLogger.azioneUtente("Migrazione schema database: v$versionePrimaDellApertura -> v$versioneDopoLApertura")
        }

        return database
    }

    private fun versioneSchemaEsistente(context: Context): Int? {
        val file = percorsoFileDatabase(context)
        if (!file.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: Exception) {
            null
        }
    }

    /** Percorso del file .db sul filesystem del device, per il backup/ripristino su Google Drive. */
    fun percorsoFileDatabase(context: Context): File = context.getDatabasePath(NOME_FILE_DATABASE)

    /**
     * Forza la scrittura di tutte le pagine dal file -wal nel file .db principale (checkpoint WAL):
     * senza questo passaggio, copiare il solo file .db per il backup rischierebbe di ottenere uno
     * snapshot incompleto, con le scritture più recenti presenti solo nel file -wal accanto.
     */
    fun eseguiCheckpointWal() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)", arrayOf<Any>()).use { it.moveToFirst() }
    }

    /**
     * Chiude la connessione Room al database corrente, da chiamare prima di sostituire il file .db
     * con un backup ripristinato da Drive. Dopo questa chiamata l'app va riavviata (non c'è un
     * percorso di "riapertura" in-place: i repository e i ViewModel già vivi mantengono riferimenti
     * diretti alle istanze correnti) — [com.desideri.viaggiotemplate.domain.backup.DatabaseBackupManager]
     * lo fa scatenando un riavvio completo del processo subito dopo lo scambio del file.
     */
    fun chiudiDatabase() {
        if (::database.isInitialized) database.close()
    }
}
