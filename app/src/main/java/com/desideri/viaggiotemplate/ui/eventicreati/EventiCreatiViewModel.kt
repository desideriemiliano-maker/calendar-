package com.desideri.viaggiotemplate.ui.eventicreati

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.calendar.CalendarWriter
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.calendar.RisultatoEliminazioneEventi
import com.desideri.viaggiotemplate.domain.calendar.dataViaggio
import com.desideri.viaggiotemplate.domain.calendar.nomeFileSicuroIcs
import com.desideri.viaggiotemplate.domain.calendar.notaSincronizzazione
import com.desideri.viaggiotemplate.domain.calendar.passata
import com.desideri.viaggiotemplate.domain.calendar.scriviIcsCondivisibile
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.repository.EsecuzioneCreataRepository
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class StatoEventiCreati(
    val esecuzioni: List<EsecuzioneCreata> = emptyList(),
    val filtroData: LocalDate? = null,
    val ordineAscendente: Boolean = true,
    val mostraPassati: Boolean = false,
    val esecuzioneSelezionata: EsecuzioneCreata? = null,
    val eventiSelezionati: List<EventoCreato> = emptyList(),
    val eventIdsSalvati: List<Long> = emptyList(),
    /** Posizioni congelate degli eventi selezionati (vedi PosizioneEventoCreato), per la vista mappa. */
    val posizioni: List<PosizioneEventoCreato> = emptyList(),
    val caricamentoEventi: Boolean = false,
    val messaggio: String? = null,
    /** Valorizzato quando l'eliminazione dal calendario non ha rimosso tutti gli eventi richiesti: la registrazione locale NON viene toccata in quel caso, per permettere di riprovare. */
    val erroreEliminazioneCalendario: RisultatoEliminazioneEventi? = null,
    /**
     * Nota informativa "leggera" (vedi [notaSincronizzazione]) dopo un'eliminazione dal calendario
     * riuscita che coinvolge un calendario non locale: mai un errore, solo un promemoria che la
     * rimozione dal server può richiedere qualche istante (o un'azione se la sync è disattivata).
     * Pensata per una snackbar, non un dialog — nessuna azione bloccante richiesta nel caso comune.
     * Si autoconsuma (vedi [notaEliminazioneMostrata]) dopo che la UI l'ha mostrata una volta.
     */
    val notaEliminazione: String? = null
) {
    /** Esecuzioni che rispettano il filtro data (per data di inizio del primo evento, cioè del viaggio) ed [mostraPassati], ordinate per quella data secondo [ordineAscendente]. */
    val risultati: List<EsecuzioneCreata>
        get() = esecuzioni
            .filter { esecuzione ->
                (filtroData == null || esecuzione.dataViaggio() == filtroData) &&
                    (mostraPassati || !esecuzione.passata())
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
            posizioni = emptyList(),
            caricamentoEventi = true,
            messaggio = null
        )
        viewModelScope.launch {
            try {
                val eventIds = repository.eventIdsPer(esecuzione.id)
                val eventi = CalendarWriter(context).eventiPerId(eventIds)
                val posizioni = repository.posizioniPer(esecuzione.id)
                _stato.value = _stato.value.copy(
                    eventiSelezionati = eventi,
                    eventIdsSalvati = eventIds,
                    posizioni = posizioni,
                    caricamentoEventi = false
                )
            } catch (e: Exception) {
                _stato.value = _stato.value.copy(
                    caricamentoEventi = false,
                    messaggio = "Errore nella lettura dal calendario: ${e.message}"
                )
            }
        }
    }

    fun tornaAiRisultati() {
        _stato.value = _stato.value.copy(esecuzioneSelezionata = null, eventiSelezionati = emptyList(), eventIdsSalvati = emptyList(), posizioni = emptyList())
    }

    /**
     * Elimina la registrazione locale dell'esecuzione. Se [eliminaAncheCalendario] è true, prova
     * prima a eliminare anche i suoi eventi rimasti sul Calendar Provider (richiede WRITE_CALENDAR):
     * se anche dopo il fallback per singolo evento (vedi CalendarWriter.eliminaEventi) restano
     * eventi non cancellati, la registrazione locale NON viene toccata — resta collegata agli
     * stessi eventId, così l'utente può riprovare — e lo stato espone il dettaglio in
     * [StatoEventiCreati.erroreEliminazioneCalendario] perché la UI mostri un errore esplicito.
     */
    fun eliminaEsecuzione(context: Context, esecuzione: EsecuzioneCreata, eliminaAncheCalendario: Boolean) {
        viewModelScope.launch {
            try {
                var nota: String? = null
                if (eliminaAncheCalendario) {
                    val eventIds = repository.eventIdsPer(esecuzione.id)
                    if (eventIds.isNotEmpty()) {
                        val esito = CalendarWriter(context).eliminaEventi(eventIds)
                        if (!esito.completato) {
                            _stato.value = _stato.value.copy(erroreEliminazioneCalendario = esito)
                            return@launch
                        }
                        nota = esito.notaSincronizzazione()
                    }
                }
                repository.elimina(esecuzione.id)
                AttivitaLogger.azioneUtente(
                    "Eliminata esecuzione \"${esecuzione.templateNome ?: "template senza nome"}\" del ${esecuzione.dataViaggio()}" +
                        if (eliminaAncheCalendario) " (anche dal calendario)" else ""
                )
                _stato.value = if (_stato.value.esecuzioneSelezionata?.id == esecuzione.id) {
                    _stato.value.copy(esecuzioneSelezionata = null, eventiSelezionati = emptyList(), eventIdsSalvati = emptyList(), posizioni = emptyList(), notaEliminazione = nota)
                } else {
                    _stato.value.copy(notaEliminazione = nota)
                }
            } catch (e: Exception) {
                AttivitaLogger.errore("Eliminazione esecuzione fallita", e.message)
                _stato.value = _stato.value.copy(messaggio = "Errore nell'eliminazione: ${e.message}")
            }
        }
    }

    /**
     * Esporta in .ics TUTTI gli eventi di [esecuzione] (rileggendoli dal Calendar Provider a
     * partire dagli ID salvati localmente, non necessariamente quelli già in [StatoEventiCreati]:
     * questa esportazione è raggiungibile anche dalla lista risultati, prima di aver selezionato
     * l'esecuzione — vedi EventiCreatiScreen). Null se l'esecuzione non ha più eventi sul
     * calendario o se la scrittura del file fallisce; in quel caso non c'è nulla da condividere,
     * silenziosamente (un'esportazione fallita non è un errore da bloccare con un dialog).
     */
    suspend fun esportaEsecuzione(context: Context, esecuzione: EsecuzioneCreata): Uri? = withContext(Dispatchers.IO) {
        try {
            val eventIds = repository.eventIdsPer(esecuzione.id)
            if (eventIds.isEmpty()) return@withContext null
            val writer = CalendarWriter(context)
            val eventi = writer.eventiPerId(eventIds)
            if (eventi.isEmpty()) return@withContext null
            val promemoria = writer.promemoriaMinutiPerEventi(eventIds)
            val uri = scriviIcsCondivisibile(context, eventi, promemoria, nomeFileSicuroIcs(esecuzione.templateNome ?: "eventi"))
            if (uri != null) {
                AttivitaLogger.azioneUtente(
                    "Esportato .ics per esecuzione \"${esecuzione.templateNome ?: "template senza nome"}\" del ${esecuzione.dataViaggio()} (${eventi.size} eventi)"
                )
            }
            uri
        } catch (e: Exception) {
            AttivitaLogger.errore("Esportazione .ics dell'esecuzione fallita", e.message)
            null
        }
    }

    /** Come [esportaEsecuzione], per un singolo [EventoCreato] (rete di sicurezza per il flusso doppio-clic in Outlook desktop, vedi IcsExporter). */
    suspend fun esportaEvento(context: Context, evento: EventoCreato): Uri? = withContext(Dispatchers.IO) {
        try {
            val promemoria = CalendarWriter(context).promemoriaMinutiPerEventi(listOf(evento.eventoId))
            val uri = scriviIcsCondivisibile(context, listOf(evento), promemoria, nomeFileSicuroIcs(evento.titolo))
            if (uri != null) {
                AttivitaLogger.azioneUtente("Esportato .ics per evento \"${evento.titolo}\"")
            }
            uri
        } catch (e: Exception) {
            AttivitaLogger.errore("Esportazione .ics dell'evento fallita", e.message)
            null
        }
    }

    /** Chiude il dialog d'errore di [StatoEventiCreati.erroreEliminazioneCalendario]: la registrazione locale resta intatta, l'utente può riprovare dal pulsante elimina. */
    fun chiudiErroreEliminazioneCalendario() {
        _stato.value = _stato.value.copy(erroreEliminazioneCalendario = null)
    }

    /** Consuma [StatoEventiCreati.notaEliminazione] dopo che la UI l'ha mostrata (snackbar), così non ricompare a una ricomposizione successiva. */
    fun notaEliminazioneMostrata() {
        _stato.value = _stato.value.copy(notaEliminazione = null)
    }
}

object EventiCreatiViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EventiCreatiViewModel(AppContainer.esecuzioneCreataRepository) as T
    }
}
