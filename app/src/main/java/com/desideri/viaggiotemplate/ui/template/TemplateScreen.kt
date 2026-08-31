package com.desideri.viaggiotemplate.ui.template

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.ui.common.ContatoreElementi
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreNotificaConEreditarieta
import com.desideri.viaggiotemplate.ui.mappa.TemplateMappaScreen
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(viewModel: TemplateViewModel = viewModel(factory = TemplateViewModelFactory.get())) {
    val templateEntities by viewModel.templateEntities.collectAsState()
    val tratteDisponibili by viewModel.tratteDisponibili.collectAsState()
    val luoghi by viewModel.luoghi.collectAsState()

    var templateInModifica by remember { mutableStateOf<Template?>(null) }
    var mostraEditor by remember { mutableStateOf(false) }
    var caricamentoId by remember { mutableStateOf<String?>(null) }

    var templateInMappa by remember { mutableStateOf<Template?>(null) }
    var mostraMappa by remember { mutableStateOf(false) }
    var caricamentoMappaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(caricamentoId) {
        caricamentoId?.let { id ->
            templateInModifica = viewModel.getTemplate(id)
            mostraEditor = true
        }
    }

    LaunchedEffect(caricamentoMappaId) {
        caricamentoMappaId?.let { id ->
            templateInMappa = viewModel.getTemplate(id)
            mostraMappa = true
        }
    }

    BackHandler(enabled = mostraEditor) { mostraEditor = false }
    BackHandler(enabled = mostraMappa) { mostraMappa = false }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                templateInModifica = Template(id = viewModel.nuovoId(), nome = "", slots = emptyList(), ordine = viewModel.prossimoOrdine())
                mostraEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuovo template")
            }
        }
    ) { padding ->
        when {
            mostraEditor && templateInModifica != null -> TemplateEditorScreen(
                template = templateInModifica!!,
                tratteDisponibili = tratteDisponibili,
                onSalva = { viewModel.salva(it); mostraEditor = false },
                onAnnulla = { mostraEditor = false },
                padding = padding
            )
            mostraMappa && templateInMappa != null -> TemplateMappaScreen(
                template = templateInMappa!!,
                tratte = tratteDisponibili,
                luoghi = luoghi,
                onChiudi = { mostraMappa = false; caricamentoMappaId = null },
                padding = padding
            )
            else -> ListaTemplate(
                templateEntities = templateEntities,
                padding = padding,
                onModifica = { caricamentoId = it.id },
                onVisualizzaMappa = { caricamentoMappaId = it.id },
                onElimina = viewModel::elimina,
                onSposta = viewModel::sposta
            )
        }
    }
}

@Composable
private fun ListaTemplate(
    templateEntities: List<TemplateEntity>,
    padding: PaddingValues,
    onModifica: (TemplateEntity) -> Unit,
    onVisualizzaMappa: (TemplateEntity) -> Unit,
    onElimina: (TemplateEntity) -> Unit,
    onSposta: (TemplateEntity, Int) -> Unit
) {
    var templateDaEliminare by remember { mutableStateOf<TemplateEntity?>(null) }
    var testoRicerca by remember { mutableStateOf("") }
    // Filtra solo sul nome: la lista lavora su TemplateEntity, la proiezione leggera senza slot/tratte
    // usata apposta per non caricare ogni template per intero solo per mostrare l'elenco; includere
    // tratte/luoghi degli slot nel filtro richiederebbe caricare ogni Template completo qui.
    val templateFiltrati = remember(templateEntities, testoRicerca) {
        templateEntities.filter { testoRicerca.isBlank() || it.nome.contains(testoRicerca, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        ContatoreElementi(mostrati = templateFiltrati.size, totale = templateEntities.size)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        itemsIndexed(templateFiltrati, key = { _, entity -> entity.id }) { indice, entity ->
            Card(
                onClick = { onModifica(entity) },
                modifier = Modifier.fillMaxWidth(),
                colors = entity.colore?.let {
                    CardDefaults.cardColors(containerColor = Color(it), contentColor = Color(0xFF1B1B1B))
                } ?: CardDefaults.cardColors()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entity.nome,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onVisualizzaMappa(entity) }) {
                        Icon(Icons.Filled.Map, contentDescription = "Visualizza su mappa")
                    }
                    IconButton(onClick = { onSposta(entity, -1) }, enabled = indice > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Sposta su")
                    }
                    IconButton(onClick = { onSposta(entity, 1) }, enabled = indice < templateFiltrati.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Sposta giù")
                    }
                    IconButton(onClick = { templateDaEliminare = entity }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        }
    }

    templateDaEliminare?.let { entity ->
        DialogConfermaEliminazione(
            nomeElemento = entity.nome,
            onConferma = { onElimina(entity); templateDaEliminare = null },
            onAnnulla = { templateDaEliminare = null }
        )
    }
}

@Composable
private fun TemplateEditorScreen(
    template: Template,
    tratteDisponibili: List<Tratta>,
    onSalva: (Template) -> Unit,
    onAnnulla: () -> Unit,
    padding: PaddingValues
) {
    var nome by remember { mutableStateOf(template.nome) }
    var slots by remember { mutableStateOf(template.slotsOrdinati) }
    var colore by remember { mutableStateOf(template.colore) }

    LazyColumn(
        modifier = Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = nome, onValueChange = { nome = it },
                label = { Text("Nome template") }, modifier = Modifier.fillMaxWidth()
            )
        }

        itemsIndexed(slots) { index, slot ->
            RigaSlot(
                slot = slot,
                indice = index,
                totale = slots.size,
                tratteDisponibili = tratteDisponibili,
                onCambia = { aggiornato -> slots = slots.mapIndexed { i, s -> if (i == index) aggiornato else s } },
                onSposta = { direzione ->
                    val nuovoIndice = index + direzione
                    if (nuovoIndice in slots.indices) {
                        slots = slots.toMutableList().apply {
                            val tmp = this[index]
                            this[index] = this[nuovoIndice]
                            this[nuovoIndice] = tmp
                        }.mapIndexed { i, s -> s.copy(ordine = i) }
                    }
                },
                onRimuovi = {
                    slots = slots.filterIndexed { i, _ -> i != index }.mapIndexed { i, s -> s.copy(ordine = i) }
                }
            )
        }

        item {
            TextButton(onClick = {
                val nuovoSlot = TemplateSlot(
                    id = UUID.randomUUID().toString(),
                    ordine = slots.size,
                    ancora = slots.isEmpty(),
                    trattaCandidatiIds = emptyList(),
                    trattaSelezionataId = ""
                )
                slots = slots + nuovoSlot
            }) { Text("+ Aggiungi tratta al template") }
        }

        item {
            SelettoreColore(coloreSelezionato = colore, onCambia = { colore = it })
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = {
                    onSalva(template.copy(nome = nome, slots = slots, colore = colore))
                }) { Text("Salva template") }
                TextButton(onClick = onAnnulla) { Text("Annulla") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RigaSlot(
    slot: TemplateSlot,
    indice: Int,
    totale: Int,
    tratteDisponibili: List<Tratta>,
    onCambia: (TemplateSlot) -> Unit,
    onSposta: (Int) -> Unit,
    onRimuovi: () -> Unit
) {
    var espansoSelettore by remember { mutableStateOf(false) }
    var espansoCandidati by remember { mutableStateOf(false) }

    val trattaSelezionata = tratteDisponibili.firstOrNull { it.id == slot.trattaSelezionataId }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${indice + 1}.", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(
                    trattaSelezionata?.nome ?: "Scegli una tratta",
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = { onSposta(-1) }, enabled = indice > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Sposta su")
                }
                IconButton(onClick = { onSposta(1) }, enabled = indice < totale - 1) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Sposta giù")
                }
                IconButton(onClick = onRimuovi) {
                    Icon(Icons.Filled.Close, contentDescription = "Rimuovi")
                }
            }

            ExposedDropdownMenuBox(expanded = espansoSelettore, onExpandedChange = { espansoSelettore = it }) {
                OutlinedTextField(
                    value = trattaSelezionata?.nome ?: "Scegli una tratta",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tratta principale") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espansoSelettore) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                DropdownMenu(
                    expanded = espansoSelettore,
                    onDismissRequest = { espansoSelettore = false },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    tratteDisponibili.forEach { tratta ->
                        DropdownMenuItem(
                            text = { Text(tratta.nome) },
                            onClick = {
                                onCambia(
                                    slot.copy(
                                        trattaSelezionataId = tratta.id,
                                        trattaCandidatiIds = (slot.trattaCandidatiIds + tratta.id).distinct()
                                    )
                                )
                                espansoSelettore = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = slot.ancora, onCheckedChange = { onCambia(slot.copy(ancora = it)) })
                Text("Usa come ancora del calcolo")
            }

            SelettoreNotificaConEreditarieta(
                valore = slot.notificaOverride,
                notificaEreditata = trattaSelezionata?.notifica ?: Notifica.NESSUNA,
                onCambia = { onCambia(slot.copy(notificaOverride = it)) }
            )

            TextButton(onClick = { espansoCandidati = true }) {
                Text("Alternative per questo slot (${slot.trattaCandidatiIds.size})")
            }
            DropdownMenu(
                expanded = espansoCandidati,
                onDismissRequest = { espansoCandidati = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                tratteDisponibili.forEach { tratta ->
                    val selezionata = tratta.id in slot.trattaCandidatiIds
                    DropdownMenuItem(
                        text = { Text((if (selezionata) "✓ " else "") + tratta.nome) },
                        onClick = {
                            val nuoviCandidati = if (selezionata) {
                                slot.trattaCandidatiIds - tratta.id
                            } else {
                                slot.trattaCandidatiIds + tratta.id
                            }
                            onCambia(slot.copy(trattaCandidatiIds = nuoviCandidati.distinct()))
                        }
                    )
                }
            }
        }
    }
}

