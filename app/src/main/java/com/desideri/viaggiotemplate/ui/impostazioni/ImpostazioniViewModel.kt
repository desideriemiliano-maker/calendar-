package com.desideri.viaggiotemplate.ui.impostazioni

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.desideri.viaggiotemplate.data.local.CredenzialiItalo
import com.desideri.viaggiotemplate.data.local.ImpostazioniStore
import com.desideri.viaggiotemplate.data.local.ItaloCredentialsStore
import com.desideri.viaggiotemplate.domain.calendar.CalendarWriter
import com.desideri.viaggiotemplate.domain.calendar.CalendarioDisponibile
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StatoImpostazioni(
    val calendariDisponibili: List<CalendarioDisponibile> = emptyList(),
    val calendarioSelezionatoId: Long? = null,
    val italoUsername: String? = null
)

class ImpostazioniViewModel(
    private val store: ImpostazioniStore,
    private val italoCredentialsStore: ItaloCredentialsStore
) : ViewModel() {

    private val _stato = MutableStateFlow(StatoImpostazioni(italoUsername = italoCredentialsStore.credenziali?.username))
    val stato: StateFlow<StatoImpostazioni> = _stato.asStateFlow()

    /** Salva un account Italo reale, usato al posto del login guest (bloccato lato server) per cercare gli orari. */
    fun salvaCredenzialiItalo(username: String, password: String) {
        italoCredentialsStore.credenziali = CredenzialiItalo(username, password)
        _stato.value = _stato.value.copy(italoUsername = username)
    }

    fun rimuoviCredenzialiItalo() {
        italoCredentialsStore.credenziali = null
        _stato.value = _stato.value.copy(italoUsername = null)
    }

    /** Legge i calendari scrivibili dal dispositivo. Da chiamare dopo aver ottenuto i permessi. */
    fun caricaCalendari(context: Context) {
        val disponibili = CalendarWriter(context).elencaCalendariScrivibili()
        var selezionato = store.calendarioSelezionatoId
        if (selezionato == null || disponibili.none { it.id == selezionato }) {
            // Preseleziona il primo (calendario primario) per comodità, ma resta modificabile.
            selezionato = disponibili.firstOrNull()?.id
            selezionato?.let { store.calendarioSelezionatoId = it }
        }
        _stato.value = StatoImpostazioni(calendariDisponibili = disponibili, calendarioSelezionatoId = selezionato)
    }

    fun selezionaCalendario(calendarioId: Long) {
        store.calendarioSelezionatoId = calendarioId
        _stato.value = _stato.value.copy(calendarioSelezionatoId = calendarioId)
    }
}

object ImpostazioniViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ImpostazioniViewModel(AppContainer.impostazioniStore, AppContainer.italoCredentialsStore) as T
    }
}
