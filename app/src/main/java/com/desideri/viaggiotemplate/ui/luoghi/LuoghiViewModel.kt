package com.desideri.viaggiotemplate.ui.luoghi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.repository.EsitoEliminazioneLuogo
import com.desideri.viaggiotemplate.repository.LuogoRepository
import com.desideri.viaggiotemplate.repository.UsoTrattaLuogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LuoghiViewModel(private val repository: LuogoRepository) : ViewModel() {

    private val _luoghi = MutableStateFlow<List<Luogo>>(emptyList())
    val luoghi: StateFlow<List<Luogo>> = _luoghi.asStateFlow()

    private val _erroreEliminazione = MutableStateFlow<List<UsoTrattaLuogo>?>(null)
    /** Tratte che usano ancora il luogo di cui è stata tentata l'eliminazione, o null se nessun errore da mostrare. */
    val erroreEliminazione: StateFlow<List<UsoTrattaLuogo>?> = _erroreEliminazione.asStateFlow()

    init {
        viewModelScope.launch {
            repository.osservaLuoghi().collect { _luoghi.value = it }
        }
    }

    fun salva(luogo: Luogo) {
        viewModelScope.launch { repository.salva(luogo) }
    }

    fun elimina(luogo: Luogo) {
        viewModelScope.launch {
            when (val esito = repository.elimina(luogo)) {
                is EsitoEliminazioneLuogo.BloccatoDaTratte -> _erroreEliminazione.value = esito.tratte
                EsitoEliminazioneLuogo.Eliminato -> Unit
            }
        }
    }

    fun chiudiErroreEliminazione() {
        _erroreEliminazione.value = null
    }

    fun nuovoId(): String = repository.nuovoId()
}
