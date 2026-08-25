package com.desideri.viaggiotemplate.ui.impostazioni

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.changelog.CHANGELOG

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostazioniScreen(viewModel: ImpostazioniViewModel = viewModel(factory = ImpostazioniViewModelFactory.get())) {
    var menuEspanso by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
    var mostraVersioni by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                actions = {
                    IconButton(onClick = { menuEspanso = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Altre opzioni")
                    }
                    DropdownMenu(expanded = menuEspanso, onDismissRequest = { menuEspanso = false }) {
                        DropdownMenuItem(
                            text = { Text("Calendario") },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraCalendario = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Versioni") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraVersioni = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Usa il menu in alto a destra per scegliere il calendario di destinazione o consultare le versioni dell'app.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (mostraCalendario) {
        DialogCalendario(viewModel = viewModel, onDismiss = { mostraCalendario = false })
    }
    if (mostraVersioni) {
        DialogVersioni(onDismiss = { mostraVersioni = false })
    }
}

@Composable
private fun DialogCalendario(viewModel: ImpostazioniViewModel, onDismiss: () -> Unit) {
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    val permessiCalendario = remember { arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
    fun permessiGiaConcessi() = permessiCalendario.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permessoConcesso by remember { mutableStateOf(permessiGiaConcessi()) }
    val richiediPermessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { risultati ->
        permessoConcesso = risultati.values.all { it }
    }

    LaunchedEffect(permessoConcesso) {
        if (permessoConcesso) viewModel.caricaCalendari(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text("Calendario di destinazione", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scegli su quale calendario Google verranno scritti gli eventi generati da \"Esegui template\". " +
                        "Il dispositivo può avere più account Google, ognuno con un proprio calendario: scegli quello giusto qui, una volta sola.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                if (!permessoConcesso) {
                    Button(onClick = { richiediPermessi.launch(permessiCalendario) }) {
                        Text("Consenti accesso al calendario")
                    }
                } else if (stato.calendariDisponibili.isEmpty()) {
                    Text("Nessun calendario scrivibile trovato sul dispositivo.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stato.calendariDisponibili, key = { it.id }) { cal ->
                            val selezionato = cal.id == stato.calendarioSelezionatoId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selezionato, onClick = { viewModel.selezionaCalendario(cal.id) })
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selezionato, onClick = { viewModel.selezionaCalendario(cal.id) })
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text(cal.nome)
                                        Text(
                                            cal.account + if (cal.isPrimary) " · primario" else "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun DialogVersioni(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text("Versioni", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Storico delle modifiche, una voce per ogni commit.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (CHANGELOG.isEmpty()) {
                    Text("Nessuno storico disponibile.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CHANGELOG.sortedByDescending { it.versionCode }) { voce ->
                            Column {
                                Text(
                                    "v${voce.versionCode} — ${voce.data}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(voce.messaggio, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}
