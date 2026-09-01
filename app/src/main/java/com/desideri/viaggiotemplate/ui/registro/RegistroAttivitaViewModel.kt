package com.desideri.viaggiotemplate.ui.registro

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.domain.log.VoceRegistro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class StatoRegistroAttivita(
    val voci: List<VoceRegistro> = emptyList(),
    val caricamento: Boolean = true,
    val svuotamentoRichiesto: Boolean = false
)

/** Formattazione della singola riga esportata: vedi [VoceRegistro.rigaEsportata]. Millisecondi inclusi, come richiesto per il registro stesso. */
private val FORMATO_EXPORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/**
 * Carica e gestisce il registro attività (vedi [AttivitaLogger]) per [RegistroAttivitaScreen].
 * Filtro categoria e ricerca testuale restano nella UI (stesso pattern di LuoghiScreen/TratteScreen/
 * TemplateScreen: stato locale della Composable, derivato con `remember`), qui c'è solo l'elenco
 * grezzo più recente-prima.
 */
class RegistroAttivitaViewModel : ViewModel() {
    private val _stato = MutableStateFlow(StatoRegistroAttivita())
    val stato: StateFlow<StatoRegistroAttivita> = _stato.asStateFlow()

    init {
        carica()
    }

    fun carica() {
        viewModelScope.launch {
            _stato.value = _stato.value.copy(caricamento = true)
            val voci = AttivitaLogger.leggiRecenti()
            _stato.value = _stato.value.copy(voci = voci, caricamento = false)
        }
    }

    fun richiediSvuotamento() {
        _stato.value = _stato.value.copy(svuotamentoRichiesto = true)
    }

    fun annullaSvuotamento() {
        _stato.value = _stato.value.copy(svuotamentoRichiesto = false)
    }

    fun confermaSvuotamento() {
        viewModelScope.launch {
            AttivitaLogger.svuota()
            _stato.value = _stato.value.copy(voci = emptyList(), svuotamentoRichiesto = false)
        }
    }

    /**
     * Scrive TUTTO il registro (non solo le voci filtrate a schermo: uno che segnala un problema
     * vuole il quadro completo) in un file di testo nella cache condivisa tramite FileProvider
     * (vedi file_paths.xml/AndroidManifest), pronto per un Intent.ACTION_SEND. Null se il registro
     * è vuoto o se la scrittura fallisce.
     */
    suspend fun esporta(context: Context): Uri? = withContext(Dispatchers.IO) {
        val voci = _stato.value.voci
        if (voci.isEmpty()) return@withContext null
        try {
            val directory = File(context.cacheDir, "registro_export").apply { mkdirs() }
            val file = File(directory, "registro-attivita.txt")
            file.writeText(voci.joinToString("\n") { it.rigaEsportata() })
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}

private fun VoceRegistro.rigaEsportata(): String {
    val orario = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).format(FORMATO_EXPORT)
    val esitoTesto = esito?.let { e -> " [$e${durataMs?.let { ", ${it}ms" } ?: ""}]" } ?: ""
    val erroreTesto = dettaglioErrore?.let { " - $it" } ?: ""
    return "$orario [${categoria.etichetta}] $descrizione$esitoTesto$erroreTesto"
}

object RegistroAttivitaViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistroAttivitaViewModel() as T
    }
}
