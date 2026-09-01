package com.desideri.viaggiotemplate.ui.eventicreati

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.calendar.CalendarWriter
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.domain.calendar.RisultatoEliminazioneEventi
import com.desideri.viaggiotemplate.domain.calendar.passata
import com.desideri.viaggiotemplate.repository.EsecuzioneCreataRepository
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface StatoEliminaPassati {
    data object Inattivo : StatoEliminaPassati
    data object Caricamento : StatoEliminaPassati
    /** Nessuna esecuzione ha un viaggio già passato: va detto esplicitamente invece di aprire un dialog vuoto. */
    data object NessunPassato : StatoEliminaPassati
    /** [passate] è la lista completa (non troncata) da mostrare/elaborare; il troncamento è solo nella UI. */
    data class Conferma(val passate: List<EsecuzioneCreata>) : StatoEliminaPassati
    data object Eliminazione : StatoEliminaPassati
    /** [esiti] sono i risultati calendario delle sole esecuzioni eliminate con successo, usati per la nota di sincronizzazione (vedi RisultatoEliminazioneEventi.notaSincronizzazioneAggregata) — non include chi ha eliminato solo la registrazione locale (eliminaAncheCalendario=false). */
    data class Completato(val numero: Int, val esiti: List<RisultatoEliminazioneEventi> = emptyList()) : StatoEliminaPassati
    /**
     * Almeno un'esecuzione non ha potuto eliminare tutti i suoi eventi dal calendario (stesso
     * criterio "niente eliminazioni silenziose" di [RisultatoEliminazioneEventi]/fix a27592d):
     * per quelle la registrazione locale NON è stata toccata, così l'utente può riprovare. Le
     * altre [eliminateConSuccesso] esecuzioni sono state eliminate regolarmente.
     */
    data class ErroreParziale(
        val eliminateConSuccesso: Int,
        val nonCompletate: List<Pair<EsecuzioneCreata, RisultatoEliminazioneEventi>>
    ) : StatoEliminaPassati
    data class Errore(val messaggio: String) : StatoEliminaPassati
}

/**
 * Orchestra l'eliminazione in blocco delle esecuzioni con viaggio già passato, dal menu principale
 * (non dalla schermata Eventi: va istanziato una volta sola a livello di AppNavigation, come
 * BackupDriveViewModel). Riusa [passata] per il criterio "passato" (stesso di
 * [StatoEventiCreati.risultati]) e [CalendarWriter.eliminaEventi] per l'eliminazione dal
 * calendario, con lo stesso trattamento robusto dell'esito già usato per l'eliminazione singola in
 * [EventiCreatiViewModel.eliminaEsecuzione]: se il calendario non elimina tutti gli eventi di
 * un'esecuzione, quella singola registrazione locale resta intatta.
 */
class EliminaPassatiViewModel(private val repository: EsecuzioneCreataRepository) : ViewModel() {

    private val _stato = MutableStateFlow<StatoEliminaPassati>(StatoEliminaPassati.Inattivo)
    val stato: StateFlow<StatoEliminaPassati> = _stato.asStateFlow()

    /** Cerca le esecuzioni passate e propone la conferma, o [StatoEliminaPassati.NessunPassato] se non ce ne sono. */
    fun avvia() {
        _stato.value = StatoEliminaPassati.Caricamento
        viewModelScope.launch {
            val passate = repository.osservaTutte().first().filter { it.passata() }.sortedBy { it.inizioPrimoEvento }
            _stato.value = if (passate.isEmpty()) StatoEliminaPassati.NessunPassato else StatoEliminaPassati.Conferma(passate)
        }
    }

    fun annulla() {
        _stato.value = StatoEliminaPassati.Inattivo
    }

    fun conferma(context: Context, passate: List<EsecuzioneCreata>, eliminaAncheCalendario: Boolean) {
        _stato.value = StatoEliminaPassati.Eliminazione
        viewModelScope.launch {
            var eliminateConSuccesso = 0
            val esitiSuccesso = mutableListOf<RisultatoEliminazioneEventi>()
            val nonCompletate = mutableListOf<Pair<EsecuzioneCreata, RisultatoEliminazioneEventi>>()
            val calendarWriter = if (eliminaAncheCalendario) CalendarWriter(context) else null
            try {
                for (esecuzione in passate) {
                    if (calendarWriter != null) {
                        val eventIds = repository.eventIdsPer(esecuzione.id)
                        if (eventIds.isNotEmpty()) {
                            val esito = calendarWriter.eliminaEventi(eventIds)
                            if (!esito.completato) {
                                nonCompletate += esecuzione to esito
                                continue
                            }
                            esitiSuccesso += esito
                        }
                    }
                    repository.elimina(esecuzione.id)
                    eliminateConSuccesso++
                }
                _stato.value = if (nonCompletate.isEmpty()) {
                    StatoEliminaPassati.Completato(eliminateConSuccesso, esitiSuccesso)
                } else {
                    StatoEliminaPassati.ErroreParziale(eliminateConSuccesso, nonCompletate)
                }
            } catch (e: Exception) {
                _stato.value = StatoEliminaPassati.Errore(
                    "Errore durante l'eliminazione: ${e.message}. Eliminate finora: $eliminateConSuccesso su ${passate.size}."
                )
            }
        }
    }
}

object EliminaPassatiViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EliminaPassatiViewModel(AppContainer.esecuzioneCreataRepository) as T
    }
}
