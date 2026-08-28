package com.desideri.viaggiotemplate.ui.eventicreati

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.ui.common.DialogSelettoreData
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_DATA_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EventiCreatiScreen() {
    val viewModel: EventiCreatiViewModel = viewModel(factory = EventiCreatiViewModelFactory.get())
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    fun permessoGiaConcesso() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    var permessoConcesso by remember { mutableStateOf(permessoGiaConcesso()) }
    val richiediPermesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { risultati ->
        permessoConcesso = risultati.values.all { it }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!permessoConcesso) {
                Text(
                    "Serve l'accesso al calendario per rileggere ed eliminare gli eventi creati dall'app.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(onClick = { richiediPermesso.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) {
                    Text("Consenti accesso al calendario")
                }
            } else if (stato.esecuzioneSelezionata == null) {
                PannelloRicerca(stato = stato, viewModel = viewModel, context = context)
            } else {
                PannelloDettaglio(stato = stato, viewModel = viewModel, context = context)
            }
        }
    }
}

@Composable
private fun ColumnScope.PannelloRicerca(stato: StatoEventiCreati, viewModel: EventiCreatiViewModel, context: Context) {
    var mostraSelettoreData by remember { mutableStateOf(false) }
    var esecuzioneDaEliminare by remember { mutableStateOf<EsecuzioneCreata?>(null) }

    Text(
        "Cerca per data del viaggio (l'inizio del primo evento) le esecuzioni di \"Aggiungi al calendario\" e rileggi gli eventi scritti.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = stato.filtroData?.format(FORMATO_DATA) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Data del viaggio") },
            placeholder = { Text("Tutte le date") },
            trailingIcon = {
                Row {
                    if (stato.filtroData != null) {
                        IconButton(onClick = { viewModel.impostaFiltroData(null) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Rimuovi filtro data")
                        }
                    }
                    IconButton(onClick = { mostraSelettoreData = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Scegli data")
                    }
                }
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { viewModel.invertiOrdinamento() }) {
            Icon(
                if (stato.ordineAscendente) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (stato.ordineAscendente) "Ordina per data decrescente" else "Ordina per data crescente"
            )
        }
        IconToggleButton(checked = stato.mostraPassati, onCheckedChange = { viewModel.impostaMostraPassati(it) }) {
            Icon(
                if (stato.mostraPassati) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (stato.mostraPassati) "Nascondi eventi passati" else "Mostra eventi passati"
            )
        }
    }

    if (mostraSelettoreData) {
        DialogSelettoreData(
            dataIniziale = stato.filtroData ?: LocalDate.now(),
            onConferma = { viewModel.impostaFiltroData(it); mostraSelettoreData = false },
            onDismiss = { mostraSelettoreData = false }
        )
    }

    val risultati = stato.risultati
    if (risultati.isEmpty()) {
        Text(
            "Nessuna esecuzione trovata.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(risultati, key = { it.id }) { esecuzione ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selezionaEsecuzione(context, esecuzione) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            esecuzione.inizioPrimoEvento.atZone(ZoneId.systemDefault()).format(FORMATO_DATA_ORA) +
                                (esecuzione.templateNome?.let { " ($it)" } ?: ""),
                            modifier = Modifier.padding(12.dp).weight(1f)
                        )
                        IconButton(onClick = { esecuzioneDaEliminare = esecuzione }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina evento creato")
                        }
                    }
                }
            }
        }
    }

    esecuzioneDaEliminare?.let { esecuzione ->
        DialogConfermaEliminazioneEsecuzione(
            onConferma = { eliminaAncheCalendario ->
                viewModel.eliminaEsecuzione(context, esecuzione, eliminaAncheCalendario)
                esecuzioneDaEliminare = null
            },
            onAnnulla = { esecuzioneDaEliminare = null }
        )
    }
}

@Composable
private fun DialogConfermaEliminazioneEsecuzione(
    onConferma: (eliminaAncheCalendario: Boolean) -> Unit,
    onAnnulla: () -> Unit
) {
    var eliminaAncheCalendario by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Eliminare questo evento creato?") },
        text = {
            Column {
                Text("La registrazione locale verrà eliminata e non potrà più essere recuperata.")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Checkbox(checked = eliminaAncheCalendario, onCheckedChange = { eliminaAncheCalendario = it })
                    Text("Elimina anche gli eventi dal calendario")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(eliminaAncheCalendario) }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

@Composable
private fun ColumnScope.PannelloDettaglio(stato: StatoEventiCreati, viewModel: EventiCreatiViewModel, context: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.tornaAiRisultati() }) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Torna ai risultati")
        }
        Text(
            "Eventi del ${stato.esecuzioneSelezionata?.inizioPrimoEvento?.atZone(ZoneId.systemDefault())?.format(FORMATO_DATA_ORA).orEmpty()}" +
                (stato.esecuzioneSelezionata?.templateNome?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.titleMedium
        )
    }

    when {
        stato.caricamentoEventi -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        stato.eventiSelezionati.isEmpty() -> Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                "Nessun evento trovato: sono stati eliminati dal calendario.",
                style = MaterialTheme.typography.bodySmall
            )
            if (stato.proponiEliminazione) {
                Text(
                    "Questo evento creato non ha più eventi sul calendario: puoi eliminare la registrazione.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = {
                        stato.esecuzioneSelezionata?.let {
                            viewModel.eliminaEsecuzione(context, it, eliminaAncheCalendario = false)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Elimina questo evento creato")
                }
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val coloreTemplate = stato.esecuzioneSelezionata?.templateColore
            items(stato.eventiSelezionati, key = { it.eventoId }) { evento ->
                val coloreSfondo = (coloreTemplate ?: evento.colore)?.let { Color(it) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = coloreSfondo?.let {
                        CardDefaults.cardColors(
                            containerColor = it,
                            contentColor = if (it.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White
                        )
                    } ?: CardDefaults.cardColors(),
                    onClick = {
                        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, evento.eventoId)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(evento.titolo, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${evento.inizio.format(FORMATO_ORA)} - ${evento.fine.format(FORMATO_ORA)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (evento.descrizione.isNotBlank()) {
                            Text(
                                evento.descrizione,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
