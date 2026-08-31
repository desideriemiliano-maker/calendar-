package com.desideri.viaggiotemplate.ui.tratte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.desideri.viaggiotemplate.ui.AppContainer

object TratteViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TratteViewModel(AppContainer.trattaRepository, AppContainer.luogoRepository) as T
    }
}
