package com.desideri.viaggiotemplate.ui.eventicreati

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.ui.common.DialogSelettoreData
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_DATA_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DialogEventiCreati(onDismiss: () -> Unit) {
    val viewModel: EventiCreatiViewModel = viewModel(factory = EventiCreatiViewModelFactory.get())
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    fun permessoGiaConcesso() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    var permessoConcesso by remember { mutableStateOf(permessoGiaConcesso()) }
    val richiediPermesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concesso ->
        permessoConcesso = concesso
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                if (!permessoConcesso) {
                    Text("Eventi creati", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Serve l'accesso al calendario per rileggere gli eventi creati dall'app.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    Button(onClick = { richiediPermesso.launch(Manifest.permission.READ_CALENDAR) }) {
                        Text("Consenti accesso al calendario")
                    }
                } else if (stato.esecuzioneSelezionata == null) {
                    PannelloRicerca(stato = stato, viewModel = viewModel, context = context)
                } else {
                    PannelloDettaglio(stato = stato, viewModel = viewModel)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun PannelloRicerca(stato: StatoEventiCreati, viewModel: EventiCreatiViewModel, context: Context) {
    var mostraSelettoreData by remember { mutableStateOf(false) }

    Text("Eventi creati", style = MaterialTheme.typography.titleMedium)
    Text(
        "Cerca per data di inserimento le esecuzioni di \"Aggiungi al calendario\" e rileggi gli eventi scritti.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )

    OutlinedTextField(
        value = stato.filtroData?.format(FORMATO_DATA) ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text("Data di inserimento") },
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
        modifier = Modifier.fillMaxWidth()
    )

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
        LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(risultati, key = { it.id }) { esecuzione ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selezionaEsecuzione(context, esecuzione) }
                ) {
                    Text(
                        esecuzione.dataCreazione.atZone(ZoneId.systemDefault()).format(FORMATO_DATA_ORA),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PannelloDettaglio(stato: StatoEventiCreati, viewModel: EventiCreatiViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.tornaAiRisultati() }) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Torna ai risultati")
        }
        Text(
            "Eventi del ${stato.esecuzioneSelezionata?.dataCreazione?.atZone(ZoneId.systemDefault())?.format(FORMATO_DATA_ORA).orEmpty()}",
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
        stato.eventiSelezionati.isEmpty() -> Text(
            "Nessun evento trovato: potrebbero essere stati eliminati dal calendario.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        else -> LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stato.eventiSelezionati, key = { it.eventoId }) { evento ->
                Card(modifier = Modifier.fillMaxWidth()) {
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
