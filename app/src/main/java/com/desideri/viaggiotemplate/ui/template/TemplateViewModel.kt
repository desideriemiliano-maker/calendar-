package com.desideri.viaggiotemplate.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.repository.LuogoRepository
import com.desideri.viaggiotemplate.repository.TemplateRepository
import com.desideri.viaggiotemplate.repository.TrattaRepository
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TemplateViewModel(
    private val templateRepository: TemplateRepository,
    private val trattaRepository: TrattaRepository,
    private val luogoRepository: LuogoRepository
) : ViewModel() {

    private val _templateEntities = MutableStateFlow<List<TemplateEntity>>(emptyList())
    val templateEntities: StateFlow<List<TemplateEntity>> = _templateEntities.asStateFlow()

    private val _tratteDisponibili = MutableStateFlow<List<Tratta>>(emptyList())
    val tratteDisponibili: StateFlow<List<Tratta>> = _tratteDisponibili.asStateFlow()

    /** Tutti i Luoghi con le loro coordinate/indirizzo, per risolvere le tappe della vista mappa di un template. */
    private val _luoghi = MutableStateFlow<List<Luogo>>(emptyList())
    val luoghi: StateFlow<List<Luogo>> = _luoghi.asStateFlow()

    init {
        viewModelScope.launch { templateRepository.osservaTemplateEntities().collect { _templateEntities.value = it } }
        viewModelScope.launch { trattaRepository.osservaTratte().collect { _tratteDisponibili.value = it } }
        viewModelScope.launch { luogoRepository.osservaLuoghi().collect { _luoghi.value = it } }
    }

    suspend fun getTemplate(id: String): Template? = templateRepository.getTemplate(id)

    fun salva(template: Template) {
        viewModelScope.launch { templateRepository.salva(template) }
    }

    fun elimina(template: TemplateEntity) {
        viewModelScope.launch { templateRepository.elimina(Template(template.id, template.nome, emptyList())) }
    }

    /** Sposta un template su (-1) o giù (+1) nella lista della libreria. */
    fun sposta(template: TemplateEntity, direzione: Int) {
        viewModelScope.launch {
            val lista = _templateEntities.value
            val indice = lista.indexOfFirst { it.id == template.id }
            val nuovoIndice = indice + direzione
            if (indice !in lista.indices || nuovoIndice !in lista.indices) return@launch
            templateRepository.scambiaOrdine(lista[indice], lista[nuovoIndice])
        }
    }

    fun nuovoId(): String = templateRepository.nuovoId()

    fun prossimoOrdine(): Int = _templateEntities.value.size
}

object TemplateViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TemplateViewModel(AppContainer.templateRepository, AppContainer.trattaRepository, AppContainer.luogoRepository) as T
    }
}
