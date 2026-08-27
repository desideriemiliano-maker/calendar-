package com.desideri.viaggiotemplate.ui.eventicreati

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.calendar.CalendarWriter
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.repository.EsecuzioneCreataRepository
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class StatoEventiCreati(
    val esecuzioni: List<EsecuzioneCreata> = emptyList(),
    val filtroData: LocalDate? = null,
    val esecuzioneSelezionata: EsecuzioneCreata? = null,
    val eventiSelezionati: List<EventoCreato> = emptyList(),
    val caricamentoEventi: Boolean = false,
    val messaggio: String? = null
) {
    /** Esecuzioni che rispettano il filtro data (per data di inizio del primo evento, cioè del viaggio), più recenti prima. */
    val risultati: List<EsecuzioneCreata>
        get() = esecuzioni.filter { esecuzione ->
            filtroData == null || esecuzione.inizioPrimoEvento.atZone(ZoneId.systemDefault()).toLocalDate() == filtroData
        }
}

class EventiCreatiViewModel(
    private val repository: EsecuzioneCreataRepository
) : ViewModel() {

    private val _stato = MutableStateFlow(StatoEventiCreati())
    val stato: StateFlow<StatoEventiCreati> = _stato.asStateFlow()

    init {
        viewModelScope.launch {
            repository.osservaTutte().collect { lista ->
                _stato.value = _stato.value.copy(esecuzioni = lista)
            }
        }
    }

    fun impostaFiltroData(data: LocalDate?) {
        _stato.value = _stato.value.copy(filtroData = data)
    }

    /** Rilegge dal Calendar Provider gli eventi di questa esecuzione, a partire dagli ID salvati localmente al momento della scrittura. */
    fun selezionaEsecuzione(context: Context, esecuzione: EsecuzioneCreata) {
        _stato.value = _stato.value.copy(
            esecuzioneSelezionata = esecuzione,
            eventiSelezionati = emptyList(),
            caricamentoEventi = true,
            messaggio = null
        )
        viewModelScope.launch {
            try {
                val eventIds = repository.eventIdsPer(esecuzione.id)
                val eventi = CalendarWriter(context).eventiPerId(eventIds)
                _stato.value = _stato.value.copy(eventiSelezionati = eventi, caricamentoEventi = false)
            } catch (e: Exception) {
                _stato.value = _stato.value.copy(
                    caricamentoEventi = false,
                    messaggio = "Errore nella lettura dal calendario: ${e.message}"
                )
            }
        }
    }

    fun tornaAiRisultati() {
        _stato.value = _stato.value.copy(esecuzioneSelezionata = null, eventiSelezionati = emptyList())
    }
}

object EventiCreatiViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EventiCreatiViewModel(AppContainer.esecuzioneCreataRepository) as T
    }
}
