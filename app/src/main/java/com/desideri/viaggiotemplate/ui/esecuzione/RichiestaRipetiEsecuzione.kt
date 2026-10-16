package com.desideri.viaggiotemplate.ui.esecuzione

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Richiesta in sospeso di "Ripeti" (vedi EventiCreatiScreen): l'utente sceglie una nuova data per
 * ripetere un'esecuzione passata, viene spostato sulla sezione Esegui, e lì [EsecuzioneScreen]
 * consuma questa richiesta per selezionare lo stesso Template e calcolare subito gli orari per
 * quella data (vedi [com.desideri.viaggiotemplate.ui.esecuzione.EsecuzioneViewModel.ripetiEsecuzione]).
 *
 * Un object singleton, stesso principio di [com.desideri.viaggiotemplate.ui.mappa.MappaVisibileStato]:
 * non esiste nel codebase un meccanismo di navigazione tra tab che porti un payload, e le due
 * schermate coinvolte (EventiCreatiScreen, EsecuzioneScreen) sono composable distinti senza un
 * ViewModel condiviso sopra il NavHost.
 */
object RichiestaRipetiEsecuzione {
    data class Richiesta(val templateId: String, val data: LocalDate)

    var pendente by mutableStateOf<Richiesta?>(null)
        private set

    fun richiedi(templateId: String, data: LocalDate) {
        pendente = Richiesta(templateId, data)
    }

    fun consumata() {
        pendente = null
    }
}
