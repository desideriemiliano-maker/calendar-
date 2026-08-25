package com.desideri.viaggiotemplate.ui.tratte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.repository.TrattaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TratteViewModel(private val repository: TrattaRepository) : ViewModel() {

    private val _tratte = MutableStateFlow<List<Tratta>>(emptyList())
    val tratte: StateFlow<List<Tratta>> = _tratte.asStateFlow()

    init {
        viewModelScope.launch {
            repository.osservaTratte().collect { _tratte.value = it }
        }
    }

    fun salva(tratta: Tratta) {
        viewModelScope.launch { repository.salva(tratta) }
    }

    fun elimina(tratta: Tratta) {
        viewModelScope.launch { repository.elimina(tratta) }
    }

    /** Sposta una tratta su (-1) o giù (+1) nella lista della libreria. */
    fun sposta(tratta: Tratta, direzione: Int) {
        viewModelScope.launch {
            val lista = _tratte.value
            val indice = lista.indexOfFirst { it.id == tratta.id }
            val nuovoIndice = indice + direzione
            if (indice !in lista.indices || nuovoIndice !in lista.indices) return@launch
            repository.scambiaOrdine(lista[indice], lista[nuovoIndice])
        }
    }

    /** Duplica una tratta (nuovo id, in coda alla lista) così l'originale resta invariato. */
    fun clona(tratta: Tratta) {
        viewModelScope.launch {
            val copia = tratta.copy(
                id = repository.nuovoId(),
                nome = "${tratta.nome} (copia)",
                ordine = _tratte.value.size,
                opzioniOrario = tratta.opzioniOrario.map { it.copy(id = repository.nuovoId()) }
            )
            repository.salva(copia)
        }
    }

    fun nuovoId(): String = repository.nuovoId()

    fun prossimoOrdine(): Int = _tratte.value.size
}
