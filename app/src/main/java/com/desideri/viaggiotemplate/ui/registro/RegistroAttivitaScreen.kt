package com.desideri.viaggiotemplate.ui.registro

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.log.CategoriaRegistro
import com.desideri.viaggiotemplate.domain.log.EsitoRegistro
import com.desideri.viaggiotemplate.domain.log.VoceRegistro
import com.desideri.viaggiotemplate.ui.common.ContatoreElementi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS")

private sealed class FiltroCategoria(val etichetta: String) {
    data object Tutte : FiltroCategoria("Tutte")
    data class Specifica(val categoria: CategoriaRegistro) : FiltroCategoria(categoria.etichetta)
}

/**
 * Consultazione del registro attività (vedi [com.desideri.viaggiotemplate.domain.log.AttivitaLogger]):
 * raggiunta dal menu a tre puntini, subito prima di "Versioni". Non è una delle 5 sezioni con tab
 * in basso, quindi non passa dal NavHost: come Calendario/Versioni è uno stato booleano gestito da
 * AppNavigation, ma qui serve uno schermo intero (lista filtrabile, non un semplice AlertDialog) —
 * un [Dialog] a piena larghezza (`usePlatformDefaultWidth = false`) lo ottiene senza dover
 * ristrutturare il layout di AppNavigation in un Box esplicito, con lo stesso identico meccanismo
 * (finestra separata) con cui Compose renderizza già gli AlertDialog di quella schermata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroAttivitaScreen(onChiudi: () -> Unit, viewModel: RegistroAttivitaViewModel = viewModel(factory = RegistroAttivitaViewModelFactory.get())) {
    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            RegistroAttivitaContenuto(onChiudi, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistroAttivitaContenuto(onChiudi: () -> Unit, viewModel: RegistroAttivitaViewModel) {
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Il ViewModel sopravvive alla chiusura/riapertura di questo Dialog (stesso ViewModelStoreOwner
    // per tutta la vita dell'Activity: chiudere il dialog non lo distrugge, solo il suo `init{}` gira
    // una volta sola alla prima apertura), quindi senza questo effetto la lista mostrata resterebbe
    // quella catturata la primissima volta — ogni riapertura successiva mostrerebbe solo le voci di
    // allora, non quelle scritte nel frattempo. `LaunchedEffect(Unit)` invece riparte a ogni
    // ricomposizione di QUESTO composable, che è sì rimosso e ricreato a ogni apertura/chiusura del
    // dialog (il ramo `if (mostraRegistroAttivita)` in AppNavigation).
    LaunchedEffect(Unit) { viewModel.carica() }

    var testoRicerca by remember { mutableStateOf("") }
    var filtroCategoria by remember { mutableStateOf<FiltroCategoria>(FiltroCategoria.Tutte) }
    var menuFiltroEspanso by remember { mutableStateOf(false) }

    val vociFiltrate = remember(stato.voci, testoRicerca, filtroCategoria) {
        stato.voci.filter { voce ->
            (testoRicerca.isBlank() ||
                voce.descrizione.contains(testoRicerca, ignoreCase = true) ||
                voce.dettaglioErrore?.contains(testoRicerca, ignoreCase = true) == true) &&
                when (val filtro = filtroCategoria) {
                    FiltroCategoria.Tutte -> true
                    is FiltroCategoria.Specifica -> voce.categoria == filtro.categoria
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro attività") },
                navigationIcon = {
                    IconButton(onClick = onChiudi) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Chiudi")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val uri = viewModel.esporta(context)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Condividi registro attività"))
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Condividi registro")
                    }
                    IconButton(onClick = viewModel::richiediSvuotamento, enabled = stato.voci.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Svuota registro")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = testoRicerca,
                    onValueChange = { testoRicerca = it },
                    label = { Text("Cerca") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (testoRicerca.isNotEmpty()) {
                            IconButton(onClick = { testoRicerca = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancella ricerca")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(
                    expanded = menuFiltroEspanso,
                    onExpandedChange = { menuFiltroEspanso = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filtroCategoria.etichetta,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuFiltroEspanso) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    DropdownMenu(expanded = menuFiltroEspanso, onDismissRequest = { menuFiltroEspanso = false }) {
                        DropdownMenuItem(
                            text = { Text(FiltroCategoria.Tutte.etichetta) },
                            onClick = { filtroCategoria = FiltroCategoria.Tutte; menuFiltroEspanso = false }
                        )
                        CategoriaRegistro.entries.forEach { categoria ->
                            DropdownMenuItem(
                                text = { Text(categoria.etichetta) },
                                onClick = { filtroCategoria = FiltroCategoria.Specifica(categoria); menuFiltroEspanso = false }
                            )
                        }
                    }
                }
            }
            ContatoreElementi(mostrati = vociFiltrate.size, totale = stato.voci.size)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    stato.caricamento -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    stato.voci.isEmpty() -> Text(
                        "Nessuna attività registrata nell'ultima settimana.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vociFiltrate, key = { "${it.timestampMs}-${it.descrizione.hashCode()}" }) { voce ->
                            RigaVoceRegistro(voce)
                        }
                    }
                }
            }
        }
    }

    if (stato.svuotamentoRichiesto) {
        AlertDialog(
            onDismissRequest = viewModel::annullaSvuotamento,
            title = { Text("Svuotare il registro?") },
            text = { Text("Tutte le voci conservate verranno eliminate definitivamente.") },
            confirmButton = { TextButton(onClick = viewModel::confermaSvuotamento) { Text("Svuota") } },
            dismissButton = { TextButton(onClick = viewModel::annullaSvuotamento) { Text("Annulla") } }
        )
    }
}

@Composable
private fun RigaVoceRegistro(voce: VoceRegistro) {
    val (icona, tint) = when {
        voce.categoria == CategoriaRegistro.AZIONE_UTENTE -> Icons.Filled.TouchApp to MaterialTheme.colorScheme.primary
        voce.esito == EsitoRegistro.ERRORE || voce.categoria == CategoriaRegistro.ERRORE -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        voce.esito == EsitoRegistro.SUCCESSO -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(icona, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(voce.descrizione, style = MaterialTheme.typography.bodyMedium)
                val orario = Instant.ofEpochMilli(voce.timestampMs).atZone(ZoneId.systemDefault()).format(FORMATO_TIMESTAMP)
                val durata = voce.durataMs?.let { " · ${it}ms" } ?: ""
                Text("$orario$durata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                voce.dettaglioErrore?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
