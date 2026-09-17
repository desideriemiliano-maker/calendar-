package com.desideri.viaggiotemplate.ui.tratte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.desideri.viaggiotemplate.ui.common.AzioneMenuCard
import com.desideri.viaggiotemplate.ui.common.ContatoreElementi
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import com.desideri.viaggiotemplate.ui.common.MenuAzioniCard
import com.desideri.viaggiotemplate.ui.common.dragDropItem
import com.desideri.viaggiotemplate.ui.common.dragHandle
import com.desideri.viaggiotemplate.ui.common.rememberDragDropListState
import com.desideri.viaggiotemplate.ui.mappa.TrattaMappaScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TratteScreen(viewModel: TratteViewModel = viewModel(factory = TratteViewModelFactory.get())) {
    val tratte by viewModel.tratte.collectAsState()
    val luoghi by viewModel.luoghi.collectAsState()
    var trattaInModifica by remember { mutableStateOf<Tratta?>(null) }
    var mostraEditor by remember { mutableStateOf(false) }
    var trattaInMappa by remember { mutableStateOf<Tratta?>(null) }

    BackHandler(enabled = mostraEditor) { mostraEditor = false }
    BackHandler(enabled = trattaInMappa != null) { trattaInMappa = null }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // Sopra l'editor cliccarlo scarterebbe silenziosamente le modifiche in corso aprendo
            // una tratta nuova; sopra la mappa non ha senso: visibile solo sulla lista.
            if (!mostraEditor && trattaInMappa == null) {
                FloatingActionButton(onClick = {
                    trattaInModifica = null
                    mostraEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuova tratta")
                }
            }
        }
    ) { padding ->
        when {
            mostraEditor -> TrattaEditorScreen(
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
            trattaInMappa != null -> TrattaMappaScreen(
                tratta = trattaInMappa!!,
                luoghi = luoghi,
                onChiudi = { trattaInMappa = null },
                padding = padding
            )
            else -> ListaTratte(
                tratte = tratte,
                padding = padding,
                onModifica = {
                    trattaInModifica = it
                    mostraEditor = true
                },
                onVisualizzaMappa = { trattaInMappa = it },
                onElimina = viewModel::elimina,
                onSposta = viewModel::sposta,
                onClona = viewModel::clona
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListaTratte(
    tratte: List<Tratta>,
    padding: PaddingValues,
    onModifica: (Tratta) -> Unit,
    onVisualizzaMappa: (Tratta) -> Unit,
    onElimina: (Tratta) -> Unit,
    onSposta: (Tratta, Int) -> Unit,
    onClona: (Tratta) -> Unit
) {
    var trattaDaEliminare by remember { mutableStateOf<Tratta?>(null) }
    var filtroTipo by remember { mutableStateOf<TipoTratta?>(null) }
    var testoRicerca by remember { mutableStateOf("") }
    var menuFiltroEspanso by remember { mutableStateOf(false) }

    val tratteFiltrate = remember(tratte, filtroTipo, testoRicerca) {
        tratte.filter { tratta ->
            (filtroTipo == null || tratta.tipo == filtroTipo) &&
                (testoRicerca.isBlank() ||
                    tratta.nome.contains(testoRicerca, ignoreCase = true) ||
                    tratta.luogoPartenza.contains(testoRicerca, ignoreCase = true) ||
                    tratta.luogoArrivo.contains(testoRicerca, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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
                        value = filtroTipo?.name ?: "Tutti",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuFiltroEspanso) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    DropdownMenu(
                        expanded = menuFiltroEspanso,
                        onDismissRequest = { menuFiltroEspanso = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tutti") },
                            onClick = {
                                filtroTipo = null
                                menuFiltroEspanso = false
                            }
                        )
                        TipoTratta.values().forEach { tipo ->
                            DropdownMenuItem(
                                text = { Text(tipo.name) },
                                onClick = {
                                    filtroTipo = tipo
                                    menuFiltroEspanso = false
                                }
                            )
                        }
                    }
                }
            }
        }

        ContatoreElementi(mostrati = tratteFiltrate.size, totale = tratte.size)

        val lazyListState = rememberLazyListState()
        val dragDropListState = rememberDragDropListState(lazyListState) { da, a ->
            onSposta(tratteFiltrate[da], a - da)
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tratteFiltrate, key = { _, tratta -> tratta.id }) { indice, tratta ->
            Card(
                onClick = { onModifica(tratta) },
                modifier = Modifier.fillMaxWidth().dragDropItem(dragDropListState, indice),
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
                        MenuAzioniCard(
                            listOf(
                                AzioneMenuCard("Modifica", Icons.Filled.Edit, onClick = { onModifica(tratta) }),
                                AzioneMenuCard("Visualizza su mappa", Icons.Filled.Map, onClick = { onVisualizzaMappa(tratta) }),
                                AzioneMenuCard("Clona", Icons.Filled.ContentCopy, onClick = { onClona(tratta) }),
                                AzioneMenuCard(
                                    "Elimina", Icons.Filled.Delete,
                                    onClick = { trattaDaEliminare = tratta },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            )
                        )
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Trascina per riordinare",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.dragHandle(dragDropListState, indice)
                        )
                    }
                    val luoghi = if (tratta.tipo == TipoTratta.RIUNIONE) {
                        tratta.luogoPartenza
                    } else {
                        "${tratta.luogoPartenza} → ${tratta.luogoArrivo}"
                    }
                    val durataMargine = if (tratta.tipo.usaOrariProgrammati) {
                        "${tratta.margineMinuti} min"
                    } else {
                        "${tratta.durataMinutiReale}+${tratta.margineMinuti} min"
                    }
                    val vettoreSuffisso = tratta.vettore?.let { " · $it" } ?: ""
                    Text("${tratta.tipo} · $luoghi ($durataMargine)$vettoreSuffisso", style = MaterialTheme.typography.bodySmall)
                }
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
    TipoTratta.AEREO -> Icons.Filled.Flight
    TipoTratta.AUTO -> Icons.Filled.DirectionsCar
    TipoTratta.RIUNIONE -> Icons.Filled.Groups
    TipoTratta.A_PIEDI -> Icons.AutoMirrored.Filled.DirectionsWalk
}
