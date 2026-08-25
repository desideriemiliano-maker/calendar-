package com.desideri.viaggiotemplate.ui

import android.content.Context
import androidx.room.Room
import com.desideri.viaggiotemplate.data.local.AppDatabase
import com.desideri.viaggiotemplate.data.local.ImpostazioniStore
import com.desideri.viaggiotemplate.data.local.MIGRATION_1_2
import com.desideri.viaggiotemplate.data.local.MIGRATION_2_3
import com.desideri.viaggiotemplate.data.local.MIGRATION_3_4
import com.desideri.viaggiotemplate.repository.TemplateRepository
import com.desideri.viaggiotemplate.repository.TrattaRepository

/**
 * Service locator minimale: se il progetto usa già Hilt/Koin, sostituire questa
 * classe con l'iniezione preferita. Va inizializzata una volta sola, es. in Application.onCreate().
 */
object AppContainer {
    private lateinit var database: AppDatabase

    lateinit var trattaRepository: TrattaRepository
        private set
    lateinit var templateRepository: TemplateRepository
        private set
    lateinit var impostazioniStore: ImpostazioniStore
        private set

    fun init(context: Context) {
        if (::database.isInitialized) return
        database = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "viaggio-template.db"
        )
            // Migrazioni esplicite: preservano i dati dell'utente ad ogni cambio di schema.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        trattaRepository = TrattaRepository(database.trattaDao())
        templateRepository = TemplateRepository(database.templateDao())
        impostazioniStore = ImpostazioniStore(context)
    }
}
