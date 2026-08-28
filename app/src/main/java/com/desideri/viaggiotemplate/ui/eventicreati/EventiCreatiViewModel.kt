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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatoEventiCreati(
    val esecuzioni: List<EsecuzioneCreata> = emptyList(),
    val filtroData: LocalDate? = null,
    val ordineAscendente: Boolean = true,
    val mostraPassati: Boolean = false,
    val esecuzioneSelezionata: EsecuzioneCreata? = null,
    val eventiSelezionati: List<EventoCreato> = emptyList(),
    val eventIdsSalvati: List<Long> = emptyList(),
    val caricamentoEventi: Boolean = false,
    val messaggio: String? = null
) {
    /** Esecuzioni che rispettano il filtro data (per data di inizio del primo evento, cioè del viaggio) ed [mostraPassati], ordinate per quella data secondo [ordineAscendente]. */
    val risultati: List<EsecuzioneCreata>
        get() = esecuzioni
            .filter { esecuzione ->
                (filtroData == null || esecuzione.inizioPrimoEvento.atZone(ZoneId.systemDefault()).toLocalDate() == filtroData) &&
                    (mostraPassati || esecuzione.inizioPrimoEvento.isAfter(Instant.now()))
            }
            .let { lista -> if (ordineAscendente) lista.sortedBy { it.inizioPrimoEvento } else lista.sortedByDescending { it.inizioPrimoEvento } }

    /** True se l'esecuzione aveva eventi salvati ma nessuno è più presente sul calendario (eliminati dall'utente): la registrazione è ormai orfana. */
    val proponiEliminazione: Boolean
        get() = !caricamentoEventi && eventIdsSalvati.isNotEmpty() && eventiSelezionati.isEmpty()
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

    fun invertiOrdinamento() {
        _stato.value = _stato.value.copy(ordineAscendente = !_stato.value.ordineAscendente)
    }

    fun impostaMostraPassati(mostra: Boolean) {
        _stato.value = _stato.value.copy(mostraPassati = mostra)
    }

    /** Rilegge dal Calendar Provider gli eventi di questa esecuzione, a partire dagli ID salvati localmente al momento della scrittura. */
    fun selezionaEsecuzione(context: Context, esecuzione: EsecuzioneCreata) {
        _stato.value = _stato.value.copy(
            esecuzioneSelezionata = esecuzione,
            eventiSelezionati = emptyList(),
            eventIdsSalvati = emptyList(),
            caricamentoEventi = true,
            messaggio = null
        )
        viewModelScope.launch {
            try {
                val eventIds = repository.eventIdsPer(esecuzione.id)
                val eventi = CalendarWriter(context).eventiPerId(eventIds)
                _stato.value = _stato.value.copy(eventiSelezionati = eventi, eventIdsSalvati = eventIds, caricamentoEventi = false)
            } catch (e: Exception) {
                _stato.value = _stato.value.copy(
                    caricamentoEventi = false,
                    messaggio = "Errore nella lettura dal calendario: ${e.message}"
                )
            }
        }
    }

    fun tornaAiRisultati() {
        _stato.value = _stato.value.copy(esecuzioneSelezionata = null, eventiSelezionati = emptyList(), eventIdsSalvati = emptyList())
    }

    /**
     * Elimina la registrazione locale dell'esecuzione. Se [eliminaAncheCalendario] è true, prova
     * prima a eliminare anche i suoi eventi rimasti sul Calendar Provider (richiede WRITE_CALENDAR).
     */
    fun eliminaEsecuzione(context: Context, esecuzione: EsecuzioneCreata, eliminaAncheCalendario: Boolean) {
        viewModelScope.launch {
            try {
                if (eliminaAncheCalendario) {
                    val eventIds = repository.eventIdsPer(esecuzione.id)
                    if (eventIds.isNotEmpty()) CalendarWriter(context).eliminaEventi(eventIds)
                }
                repository.elimina(esecuzione.id)
                if (_stato.value.esecuzioneSelezionata?.id == esecuzione.id) {
                    _stato.value = _stato.value.copy(esecuzioneSelezionata = null, eventiSelezionati = emptyList(), eventIdsSalvati = emptyList())
                }
            } catch (e: Exception) {
                _stato.value = _stato.value.copy(messaggio = "Errore nell'eliminazione: ${e.message}")
            }
        }
    }
}

object EventiCreatiViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EventiCreatiViewModel(AppContainer.esecuzioneCreataRepository) as T
    }
}
