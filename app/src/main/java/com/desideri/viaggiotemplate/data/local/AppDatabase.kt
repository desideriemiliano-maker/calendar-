package com.desideri.viaggiotemplate.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.desideri.viaggiotemplate.data.local.dao.EsecuzioneCreataDao
import com.desideri.viaggiotemplate.data.local.dao.LuogoDao
import com.desideri.viaggiotemplate.data.local.dao.TemplateDao
import com.desideri.viaggiotemplate.data.local.dao.TrattaDao
import com.desideri.viaggiotemplate.data.local.entities.EsecuzioneCreataEntity
import com.desideri.viaggiotemplate.data.local.entities.EventoCreatoEntity
import com.desideri.viaggiotemplate.data.local.entities.LuogoEntity
import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.OrarioFissoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotCandidatoEntity
import com.desideri.viaggiotemplate.data.local.entities.TemplateSlotEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity
import java.util.UUID

/** Nome del file .db sul filesystem del device: unica fonte di verità, riusato da [com.desideri.viaggiotemplate.ui.AppContainer] e dal backup/ripristino Drive per localizzare il file senza duplicare la stringa. */
const val NOME_FILE_DATABASE = "viaggio-template.db"

/** Versione corrente dello schema (== `PRAGMA user_version` scritto da Room): riusata dal ripristino di un backup Drive per verificare, prima di sostituire il database live, che il file scaricato sia dello schema atteso. */
const val VERSIONE_SCHEMA_DATABASE = 15

@Database(
    entities = [
        TrattaEntity::class,
        LuogoEntity::class,
        OpzioneOrarioEntity::class,
        OrarioFissoEntity::class,
        TemplateEntity::class,
        TemplateSlotCandidatoEntity::class,
        TemplateSlotEntity::class,
        EsecuzioneCreataEntity::class,
        EventoCreatoEntity::class
    ],
    version = VERSIONE_SCHEMA_DATABASE,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trattaDao(): TrattaDao
    abstract fun luogoDao(): LuogoDao
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

val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tratta ADD COLUMN indirizzoArrivo TEXT")
    }
}

/**
 * Introduce l'entità Luogo (nome + indirizzo) e trasforma `tratta.luogoPartenza`/`luogoArrivo` da
 * testo libero a riferimenti (`luogoPartenzaId`/`luogoArrivoId`) verso `luogo`. Il vecchio
 * `tratta.indirizzoArrivo` (solo AUTO) viene assorbito nell'indirizzo del Luogo di arrivo.
 *
 * Deduplicazione: due nomi che differiscono solo per maiuscole/spazi iniziali o finali diventano
 * un solo Luogo; il nome visualizzato resta quello della prima tratta incontrata (ordinando per
 * `ordine`, poi per ordine di inserimento) — comportamento meno sorprendente per chi già conosce
 * i propri dati. Se più tratte assegnano indirizzi diversi allo stesso luogo di arrivo, nessun
 * indirizzo viene scartato silenziosamente: i valori distinti vengono uniti con " | " nell'unico
 * Luogo risultante, visibili e correggibili a mano nella scheda Luoghi.
 *
 * `tratta` viene ricreata (le versioni minime supportate di SQLite non hanno DROP COLUMN): nuova
 * tabella con lo schema finale, righe ricopiate risolvendo gli id dei Luoghi, tabella vecchia
 * eliminata e quella nuova rinominata. Tutta la migrazione gira nella transazione che
 * SQLiteOpenHelper apre per l'upgrade: se un passaggio fallisce, rollback automatico e il database
 * resta alla versione precedente, mai in uno stato intermedio.
 */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `luogo` (
                `id` TEXT NOT NULL,
                `nome` TEXT NOT NULL,
                `indirizzo` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        data class RigaTrattaEsistente(
            val nomePartenza: String,
            val nomeArrivo: String,
            val indirizzoArrivo: String?
        )

        val righe = mutableListOf<RigaTrattaEsistente>()
        db.query("SELECT luogoPartenza, luogoArrivo, indirizzoArrivo FROM tratta ORDER BY ordine, rowid").use { cursore ->
            val idxPartenza = cursore.getColumnIndexOrThrow("luogoPartenza")
            val idxArrivo = cursore.getColumnIndexOrThrow("luogoArrivo")
            val idxIndirizzo = cursore.getColumnIndexOrThrow("indirizzoArrivo")
            while (cursore.moveToNext()) {
                righe += RigaTrattaEsistente(
                    nomePartenza = cursore.getString(idxPartenza) ?: "",
                    nomeArrivo = cursore.getString(idxArrivo) ?: "",
                    indirizzoArrivo = if (cursore.isNull(idxIndirizzo)) null else cursore.getString(idxIndirizzo)
                )
            }
        }

        fun chiave(nome: String) = nome.trim().lowercase()

        class GruppoLuogo(val nomeVisualizzato: String) {
            val indirizzi = LinkedHashSet<String>()
        }

        val gruppi = LinkedHashMap<String, GruppoLuogo>()
        for (riga in righe) {
            gruppi.getOrPut(chiave(riga.nomePartenza)) { GruppoLuogo(riga.nomePartenza) }
            val gruppoArrivo = gruppi.getOrPut(chiave(riga.nomeArrivo)) { GruppoLuogo(riga.nomeArrivo) }
            riga.indirizzoArrivo?.trim()?.takeIf { it.isNotEmpty() }?.let { gruppoArrivo.indirizzi += it }
        }

        val idPerChiave = LinkedHashMap<String, String>()
        for ((chiaveNome, gruppo) in gruppi) {
            val id = UUID.randomUUID().toString()
            idPerChiave[chiaveNome] = id
            val indirizzoFinale = if (gruppo.indirizzi.isEmpty()) null else gruppo.indirizzi.joinToString(" | ")
            db.insert(
                "luogo",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("id", id)
                    put("nome", gruppo.nomeVisualizzato)
                    put("indirizzo", indirizzoFinale)
                }
            )
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tratta_new` (
                `id` TEXT NOT NULL,
                `nome` TEXT NOT NULL,
                `tipo` TEXT NOT NULL,
                `luogoPartenzaId` TEXT NOT NULL,
                `luogoArrivoId` TEXT NOT NULL,
                `durataMinutiReale` INTEGER NOT NULL,
                `margineMinuti` INTEGER NOT NULL,
                `arrotondaInizio` TEXT NOT NULL,
                `arrotondaFine` TEXT NOT NULL,
                `stepArrotondamentoMinuti` INTEGER NOT NULL,
                `titoloTemplate` TEXT NOT NULL,
                `ordine` INTEGER NOT NULL DEFAULT 0,
                `colore` INTEGER,
                `orarioInizioDefaultMinuti` INTEGER,
                `vettore` TEXT,
                `notifica` TEXT NOT NULL DEFAULT 'NESSUNA',
                PRIMARY KEY(`id`),
                FOREIGN KEY(`luogoPartenzaId`) REFERENCES `luogo`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`luogoArrivoId`) REFERENCES `luogo`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )

        db.query(
            "SELECT id, nome, tipo, luogoPartenza, luogoArrivo, durataMinutiReale, margineMinuti, " +
                "arrotondaInizio, arrotondaFine, stepArrotondamentoMinuti, titoloTemplate, ordine, " +
                "colore, orarioInizioDefaultMinuti, vettore, notifica FROM tratta"
        ).use { cursore ->
            val idxId = cursore.getColumnIndexOrThrow("id")
            val idxNome = cursore.getColumnIndexOrThrow("nome")
            val idxTipo = cursore.getColumnIndexOrThrow("tipo")
            val idxPartenza = cursore.getColumnIndexOrThrow("luogoPartenza")
            val idxArrivo = cursore.getColumnIndexOrThrow("luogoArrivo")
            val idxDurata = cursore.getColumnIndexOrThrow("durataMinutiReale")
            val idxMargine = cursore.getColumnIndexOrThrow("margineMinuti")
            val idxArrotondaInizio = cursore.getColumnIndexOrThrow("arrotondaInizio")
            val idxArrotondaFine = cursore.getColumnIndexOrThrow("arrotondaFine")
            val idxStep = cursore.getColumnIndexOrThrow("stepArrotondamentoMinuti")
            val idxTitolo = cursore.getColumnIndexOrThrow("titoloTemplate")
            val idxOrdine = cursore.getColumnIndexOrThrow("ordine")
            val idxColore = cursore.getColumnIndexOrThrow("colore")
            val idxOrarioDefault = cursore.getColumnIndexOrThrow("orarioInizioDefaultMinuti")
            val idxVettore = cursore.getColumnIndexOrThrow("vettore")
            val idxNotifica = cursore.getColumnIndexOrThrow("notifica")

            while (cursore.moveToNext()) {
                val luogoPartenzaId = idPerChiave.getValue(chiave(cursore.getString(idxPartenza) ?: ""))
                val luogoArrivoId = idPerChiave.getValue(chiave(cursore.getString(idxArrivo) ?: ""))
                val valori = ContentValues().apply {
                    put("id", cursore.getString(idxId))
                    put("nome", cursore.getString(idxNome))
                    put("tipo", cursore.getString(idxTipo))
                    put("luogoPartenzaId", luogoPartenzaId)
                    put("luogoArrivoId", luogoArrivoId)
                    put("durataMinutiReale", cursore.getInt(idxDurata))
                    put("margineMinuti", cursore.getInt(idxMargine))
                    put("arrotondaInizio", cursore.getString(idxArrotondaInizio))
                    put("arrotondaFine", cursore.getString(idxArrotondaFine))
                    put("stepArrotondamentoMinuti", cursore.getInt(idxStep))
                    put("titoloTemplate", cursore.getString(idxTitolo))
                    put("ordine", cursore.getInt(idxOrdine))
                    if (cursore.isNull(idxColore)) putNull("colore") else put("colore", cursore.getInt(idxColore))
                    if (cursore.isNull(idxOrarioDefault)) {
                        putNull("orarioInizioDefaultMinuti")
                    } else {
                        put("orarioInizioDefaultMinuti", cursore.getInt(idxOrarioDefault))
                    }
                    put("vettore", cursore.getString(idxVettore))
                    put("notifica", cursore.getString(idxNotifica))
                }
                db.insert("tratta_new", SQLiteDatabase.CONFLICT_ABORT, valori)
            }
        }

        db.execSQL("DROP TABLE `tratta`")
        db.execSQL("ALTER TABLE `tratta_new` RENAME TO `tratta`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tratta_luogoPartenzaId` ON `tratta` (`luogoPartenzaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tratta_luogoArrivoId` ON `tratta` (`luogoArrivoId`)")
    }
}

/** Aggiunge coordinate GPS opzionali al Luogo, alternative all'indirizzo testuale (vedi [Luogo] e [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter] per la priorità). */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE luogo ADD COLUMN latitudine REAL")
        db.execSQL("ALTER TABLE luogo ADD COLUMN longitudine REAL")
    }
}

/**
 * Aggiunge `colore` (stessa palette/significato di Tratta/Template) e `ordine` al Luogo, per
 * poterlo colorare e riordinare come già le altre due liste della libreria.
 *
 * Le righe esistenti non hanno un ordine significativo: `ALTER TABLE ... ADD COLUMN` le
 * lascerebbe tutte a 0 (parità totale, ordine indefinito). Si assegna invece un ordine iniziale
 * deterministico per nome (alfabetico, case-insensitive: lo stesso ordinamento con cui la
 * schermata Luoghi mostrava l'elenco finora), così l'elenco non cambia visivamente subito dopo
 * l'aggiornamento — riordinarlo diventa da qui in poi un'azione esplicita dell'utente.
 *
 * Fatto con un cursore invece di un'unica UPDATE con funzione finestra (ROW_NUMBER): le funzioni
 * finestra richiedono SQLite 3.25+, non garantito sulla versione di sistema di un dispositivo con
 * minSdk 26 (Android 8.0, 2017) quanto quella bundlata con Room.
 */
val MIGRATION_14_15: Migration = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE luogo ADD COLUMN colore INTEGER")
        db.execSQL("ALTER TABLE luogo ADD COLUMN ordine INTEGER NOT NULL DEFAULT 0")

        val ids = mutableListOf<String>()
        db.query("SELECT id FROM luogo ORDER BY nome COLLATE NOCASE").use { cursore ->
            val idxId = cursore.getColumnIndexOrThrow("id")
            while (cursore.moveToNext()) ids += cursore.getString(idxId)
        }
        ids.forEachIndexed { indice, id ->
            db.execSQL("UPDATE luogo SET ordine = ? WHERE id = ?", arrayOf(indice, id))
        }
    }
}
