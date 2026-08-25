package com.desideri.viaggiotemplate.ui.impostazioni

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostazioniScreen(viewModel: ImpostazioniViewModel = viewModel(factory = ImpostazioniViewModelFactory.get())) {
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

    Scaffold(topBar = { TopAppBar(title = { Text("Impostazioni") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Calendario di destinazione", style = MaterialTheme.typography.titleMedium) }
            item {
                Text(
                    "Scegli su quale calendario Google verranno scritti gli eventi generati da \"Esegui template\". " +
                        "Il dispositivo può avere più account Google, ognuno con un proprio calendario: scegli quello giusto qui, una volta sola.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!permessoConcesso) {
                item {
                    Button(onClick = { richiediPermessi.launch(permessiCalendario) }) {
                        Text("Consenti accesso al calendario")
                    }
                }
            } else if (stato.calendariDisponibili.isEmpty()) {
                item { Text("Nessun calendario scrivibile trovato sul dispositivo.") }
            } else {
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
    }
}
