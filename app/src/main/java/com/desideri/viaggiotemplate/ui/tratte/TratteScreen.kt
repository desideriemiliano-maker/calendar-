package com.desideri.viaggiotemplate.ui.tratte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TratteScreen(viewModel: TratteViewModel = viewModel(factory = TratteViewModelFactory.get())) {
    val tratte by viewModel.tratte.collectAsState()
    var trattaInModifica by remember { mutableStateOf<Tratta?>(null) }
    var mostraEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Libreria Tratte") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                trattaInModifica = null
                mostraEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuova tratta")
            }
        }
    ) { padding ->
        if (mostraEditor) {
            TrattaEditorScreen(
                trattaEsistente = trattaInModifica,
                nuovoId = viewModel::nuovoId,
                ordineIniziale = viewModel::prossimoOrdine,
                onSalva = { tratta ->
                    viewModel.salva(tratta)
                    mostraEditor = false
                },
                onAnnulla = { mostraEditor = false },
                padding = padding
            )
        } else {
            ListaTratte(
                tratte = tratte,
                padding = padding,
                onModifica = {
                    trattaInModifica = it
                    mostraEditor = true
                },
                onElimina = viewModel::elimina,
                onSposta = viewModel::sposta,
                onClona = viewModel::clona
            )
        }
    }
}

@Composable
private fun ListaTratte(
    tratte: List<Tratta>,
    padding: PaddingValues,
    onModifica: (Tratta) -> Unit,
    onElimina: (Tratta) -> Unit,
    onSposta: (Tratta, Int) -> Unit,
    onClona: (Tratta) -> Unit
) {
    var trattaDaEliminare by remember { mutableStateOf<Tratta?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tratte, key = { _, tratta -> tratta.id }) { indice, tratta ->
            Card(
                onClick = { onModifica(tratta) },
                modifier = Modifier.fillMaxWidth(),
                colors = tratta.colore?.let {
                    CardDefaults.cardColors(containerColor = Color(it), contentColor = Color(0xFF1B1B1B))
                } ?: CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            iconaPerTipo(tratta.tipo),
                            contentDescription = tratta.tipo.name,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            tratta.nome,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onSposta(tratta, -1) }, enabled = indice > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Sposta su")
                        }
                        IconButton(onClick = { onSposta(tratta, 1) }, enabled = indice < tratte.size - 1) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Sposta giù")
                        }
                        IconButton(onClick = { onClona(tratta) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Clona")
                        }
                        IconButton(onClick = { trattaDaEliminare = tratta }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    val luoghi = if (tratta.tipo == TipoTratta.RIUNIONE) {
                        tratta.luogoPartenza
                    } else {
                        "${tratta.luogoPartenza} → ${tratta.luogoArrivo}"
                    }
                    val durataMargine = if (tratta.tipo == TipoTratta.TRENO) {
                        "${tratta.margineMinuti} min"
                    } else {
                        "${tratta.durataMinutiReale}+${tratta.margineMinuti} min"
                    }
                    Text("${tratta.tipo} · $luoghi ($durataMargine)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    trattaDaEliminare?.let { tratta ->
        DialogConfermaEliminazione(
            nomeElemento = tratta.nome,
            onConferma = { onElimina(tratta); trattaDaEliminare = null },
            onAnnulla = { trattaDaEliminare = null }
        )
    }
}

private fun iconaPerTipo(tipo: TipoTratta): ImageVector = when (tipo) {
    TipoTratta.TRENO -> Icons.Filled.Train
    TipoTratta.AUTO -> Icons.Filled.DirectionsCar
    TipoTratta.RIUNIONE -> Icons.Filled.Groups
    TipoTratta.A_PIEDI -> Icons.AutoMirrored.Filled.DirectionsWalk
}
