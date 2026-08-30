package com.desideri.viaggiotemplate.ui.luoghi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione

@Composable
fun LuoghiScreen(viewModel: LuoghiViewModel = viewModel(factory = LuoghiViewModelFactory.get())) {
    val luoghi by viewModel.luoghi.collectAsState()
    val erroreEliminazione by viewModel.erroreEliminazione.collectAsState()
    var luogoInModifica by remember { mutableStateOf<Luogo?>(null) }
    var mostraEditor by remember { mutableStateOf(false) }
    var luogoDaEliminare by remember { mutableStateOf<Luogo?>(null) }

    BackHandler(enabled = mostraEditor) { mostraEditor = false }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                luogoInModifica = null
                mostraEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuovo luogo")
            }
        }
    ) { padding ->
        if (mostraEditor) {
            LuogoEditorScreen(
                luogoEsistente = luogoInModifica,
                nuovoId = viewModel::nuovoId,
                onSalva = { luogo ->
                    viewModel.salva(luogo)
                    mostraEditor = false
                },
                onAnnulla = { mostraEditor = false },
                padding = padding
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(luoghi, key = { it.id }) { luogo ->
                        Card(
                            onClick = { luogoInModifica = luogo; mostraEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(luogo.nome, style = MaterialTheme.typography.titleMedium)
                                    luogo.indirizzo?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { luogoDaEliminare = luogo }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    luogoDaEliminare?.let { luogo ->
        DialogConfermaEliminazione(
            nomeElemento = luogo.nome,
            onConferma = { viewModel.elimina(luogo); luogoDaEliminare = null },
            onAnnulla = { luogoDaEliminare = null }
        )
    }

    erroreEliminazione?.let { numeroTratte ->
        AlertDialog(
            onDismissRequest = viewModel::chiudiErroreEliminazione,
            title = { Text("Impossibile eliminare il luogo") },
            text = {
                Text(
                    if (numeroTratte == 1) {
                        "È usato da 1 tratta: rimuovilo prima da lì (o assegna a quella tratta un altro luogo)."
                    } else {
                        "È usato da $numeroTratte tratte: rimuovilo prima da lì (o assegna a quelle tratte un altro luogo)."
                    }
                )
            },
            confirmButton = { TextButton(onClick = viewModel::chiudiErroreEliminazione) { Text("OK") } }
        )
    }
}
