package com.desideri.viaggiotemplate

import android.app.Application
import com.desideri.viaggiotemplate.ui.AppContainer

class ViaggioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
