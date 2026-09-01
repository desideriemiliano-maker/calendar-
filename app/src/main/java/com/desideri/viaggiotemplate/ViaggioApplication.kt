package com.desideri.viaggiotemplate

import android.app.Application
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.ui.AppContainer

class ViaggioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Prima di AppContainer: così anche l'apertura del database (con le eventuali migrazioni
        // di schema, vedi AppContainer.costruisciDatabase) può registrarsi nel log.
        AttivitaLogger.init(this)
        AppContainer.init(this)
    }
}
