package com.desideri.viaggiotemplate.ui.luoghi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.desideri.viaggiotemplate.ui.AppContainer

object LuoghiViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LuoghiViewModel(AppContainer.luogoRepository) as T
    }
}
