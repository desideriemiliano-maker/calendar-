package com.desideri.viaggiotemplate.ui.template

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.ui.common.AzioneMenuCard
import com.desideri.viaggiotemplate.ui.common.ContatoreElementi
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import com.desideri.viaggiotemplate.ui.common.DragDropListState
import com.desideri.viaggiotemplate.ui.common.MenuAzioniCard
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreNotificaConEreditarieta
import com.desideri.viaggiotemplate.ui.common.dragDropItem
import com.desideri.viaggiotemplate.ui.common.dragHandle
import com.desideri.viaggiotemplate.ui.common.rememberDragDropListState
import com.desideri.viaggiotemplate.ui.common.rememberListaRiordinabile
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
            // Visibile solo sulla lista: sopra la mappa (e sopra l'editor) non ha senso, e in
            // editor cliccarlo scarterebbe silenziosamente le modifiche in corso aprendone uno nuovo.
            if (!mostraEditor && !mostraMappa) {
                FloatingActionButton(onClick = {
                    templateInModifica = Template(id = viewModel.nuovoId(), nome = "", slots = emptyList(), ordine = viewModel.prossimoOrdine())
                    mostraEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuovo template")
                }
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
        val lazyListState = rememberLazyListState()
        val (templateVisibili, dragDropListState) = rememberListaRiordinabile(templateFiltrati, lazyListState) { entity, direzione ->
            onSposta(entity, direzione)
        }
        LazyColumn(
            state = lazyListState,
            userScrollEnabled = dragDropListState.draggingItemIndex == null,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        itemsIndexed(templateVisibili, key = { _, entity -> entity.id }) { indice, entity ->
            Card(
                onClick = { onModifica(entity) },
                modifier = Modifier.fillMaxWidth().dragDropItem(dragDropListState, indice),
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
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Trascina per riordinare",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.dragHandle(dragDropListState, indice)
                    )
                    MenuAzioniCard(
                        listOf(
                            AzioneMenuCard("Modifica", Icons.Filled.Edit, onClick = { onModifica(entity) }),
                            AzioneMenuCard("Visualizza su mappa", Icons.Filled.Map, onClick = { onVisualizzaMappa(entity) }),
                            AzioneMenuCard(
                                "Elimina", Icons.Filled.Delete,
                                onClick = { templateDaEliminare = entity },
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                            )
                        )
                    )
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

    // Un solo item {} (il nome) precede gli slot in questa LazyColumn: l'indice di layout di
    // uno slot è quindi sempre il suo indice nella lista +1 — il delta usato per tradurre gli
    // indici (adiacenti, vedi DragDropListState) ricevuti da onMove in indici di `slots`.
    val indiceLayoutSlot = 1
    val lazyListState = rememberLazyListState()
    val dragDropListState = rememberDragDropListState(lazyListState) { da, a ->
        val indiceOrigine = da - indiceLayoutSlot
        val indiceDestinazione = a - indiceLayoutSlot
        if (indiceOrigine in slots.indices && indiceDestinazione in slots.indices) {
            slots = slots.toMutableList().apply {
                val tmp = this[indiceOrigine]
                this[indiceOrigine] = this[indiceDestinazione]
                this[indiceDestinazione] = tmp
            }.mapIndexed { i, s -> s.copy(ordine = i) }
        }
    }

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = dragDropListState.draggingItemIndex == null,
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
            // Tratte adiacenti nell'ordine visualizzato (non necessariamente l'"ordine" salvato,
            // che qui coincide comunque con esso): usate solo per suggerire l'ordinamento per
            // rilevanza nel selettore tratta di questo slot, vedi ordinatePerRilevanza.
            val trattaPrecedente = slots.getOrNull(index - 1)
                ?.let { prec -> tratteDisponibili.firstOrNull { it.id == prec.trattaSelezionataId } }
            val trattaSuccessiva = slots.getOrNull(index + 1)
                ?.let { succ -> tratteDisponibili.firstOrNull { it.id == succ.trattaSelezionataId } }
            RigaSlot(
                slot = slot,
                indice = index,
                dragDropListState = dragDropListState,
                indiceLayout = index + indiceLayoutSlot,
                tratteDisponibili = tratteDisponibili,
                trattaPrecedente = trattaPrecedente,
                trattaSuccessiva = trattaSuccessiva,
                onCambia = { aggiornato -> slots = slots.mapIndexed { i, s -> if (i == index) aggiornato else s } },
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

/**
 * Ordina per rilevanza rispetto alle tratte adiacenti nel template ([trattaPrecedente]/
 * [trattaSuccessiva], entrambe opzionali — uno slot in testa/coda o appena aggiunto può non averne):
 * prima le tratte la cui partenza combacia con l'arrivo della tratta precedente (il proseguimento
 * "naturale" del viaggio), poi quelle il cui arrivo combacia con la partenza della tratta
 * successiva, poi tutte le altre — ciascun gruppo alfabetico per nome. Aiuta a trovare subito la
 * tratta giusta in una libreria grande quando si sta componendo una catena di tappe.
 */
private fun List<Tratta>.ordinatePerRilevanza(trattaPrecedente: Tratta?, trattaSuccessiva: Tratta?): List<Tratta> {
    fun punteggio(t: Tratta): Int {
        var p = 0
        if (trattaPrecedente != null && t.luogoPartenzaId == trattaPrecedente.luogoArrivoId) p -= 2
        if (trattaSuccessiva != null && t.luogoArrivoId == trattaSuccessiva.luogoPartenzaId) p -= 1
        return p
    }
    return sortedWith(compareBy({ punteggio(it) }, { it.nome.lowercase() }))
}

@Composable
private fun RigaSlot(
    slot: TemplateSlot,
    indice: Int,
    dragDropListState: DragDropListState,
    indiceLayout: Int,
    tratteDisponibili: List<Tratta>,
    trattaPrecedente: Tratta?,
    trattaSuccessiva: Tratta?,
    onCambia: (TemplateSlot) -> Unit,
    onRimuovi: () -> Unit
) {
    var mostraSelettore by remember { mutableStateOf(false) }
    var mostraCandidati by remember { mutableStateOf(false) }

    val trattaSelezionata = tratteDisponibili.firstOrNull { it.id == slot.trattaSelezionataId }
    val tratteOrdinate = remember(tratteDisponibili, trattaPrecedente, trattaSuccessiva) {
        tratteDisponibili.ordinatePerRilevanza(trattaPrecedente, trattaSuccessiva)
    }

    Card(modifier = Modifier.fillMaxWidth().dragDropItem(dragDropListState, indiceLayout)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${indice + 1}.", style = MaterialTheme.typography.titleSmall)
                Text(
                    trattaSelezionata?.nome ?: "Scegli una tratta",
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Trascina per riordinare",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.dragHandle(dragDropListState, indiceLayout)
                )
                IconButton(onClick = onRimuovi) {
                    Icon(Icons.Filled.Close, contentDescription = "Rimuovi")
                }
            }

            OutlinedTextField(
                value = trattaSelezionata?.nome ?: "Scegli una tratta",
                onValueChange = {},
                readOnly = true,
                label = { Text("Tratta principale") },
                trailingIcon = {
                    IconButton(onClick = { mostraSelettore = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Cerca tratta")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (mostraSelettore) {
                DialogSelettoreTratta(
                    titolo = "Scegli una tratta",
                    tratte = tratteOrdinate,
                    onSeleziona = { tratta ->
                        onCambia(
                            slot.copy(
                                trattaSelezionataId = tratta.id,
                                trattaCandidatiIds = (slot.trattaCandidatiIds + tratta.id).distinct()
                            )
                        )
                        mostraSelettore = false
                    },
                    onDismiss = { mostraSelettore = false }
                )
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

            TextButton(onClick = { mostraCandidati = true }) {
                Text("Alternative per questo slot (${slot.trattaCandidatiIds.size})")
            }
            if (mostraCandidati) {
                DialogSelettoreAlternative(
                    tratte = tratteOrdinate,
                    candidatiIds = slot.trattaCandidatiIds,
                    onAlterna = { tratta ->
                        val selezionata = tratta.id in slot.trattaCandidatiIds
                        val nuoviCandidati = if (selezionata) {
                            slot.trattaCandidatiIds - tratta.id
                        } else {
                            slot.trattaCandidatiIds + tratta.id
                        }
                        onCambia(slot.copy(trattaCandidatiIds = nuoviCandidati.distinct()))
                    },
                    onDismiss = { mostraCandidati = false }
                )
            }
        }
    }
}

/**
 * Popup con campo di ricerca per scegliere la tratta principale di uno slot: stesso pattern del
 * selettore template in Esegui (vedi DialogSelettoreTemplate), un Dialog con una LazyColumn vera
 * al posto del DropdownMenu precedente (che non scorreva con tante tratte in libreria). [tratte]
 * arriva già ordinata per rilevanza (vedi ordinatePerRilevanza) — qui si applica solo il filtro
 * testuale, senza toccare quell'ordine.
 */
@Composable
private fun DialogSelettoreTratta(
    titolo: String,
    tratte: List<Tratta>,
    onSeleziona: (Tratta) -> Unit,
    onDismiss: () -> Unit
) {
    var ricerca by remember { mutableStateOf("") }
    val tratteFiltrate = remember(tratte, ricerca) {
        if (ricerca.isBlank()) tratte else tratte.filter { it.nome.contains(ricerca, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text(titolo, style = MaterialTheme.typography.titleMedium)
                CampoRicercaTratte(ricerca = ricerca, onRicercaCambiata = { ricerca = it })
                if (tratteFiltrate.isEmpty()) {
                    Text("Nessuna tratta trovata.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(tratteFiltrate, key = { it.id }) { tratta ->
                            Text(
                                tratta.nome,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeleziona(tratta) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annulla") }
                }
            }
        }
    }
}

/**
 * Popup con campo di ricerca per scegliere le tratte alternative di uno slot (multi-selezione a
 * spunta, vedi TemplateSlot.trattaCandidatiIds): resta aperto finché non lo si chiude esplicitamente,
 * a differenza di [DialogSelettoreTratta] che si chiude alla prima scelta. Stesso ordinamento per
 * rilevanza già applicato a [tratte] dal chiamante.
 */
@Composable
private fun DialogSelettoreAlternative(
    tratte: List<Tratta>,
    candidatiIds: List<String>,
    onAlterna: (Tratta) -> Unit,
    onDismiss: () -> Unit
) {
    var ricerca by remember { mutableStateOf("") }
    val tratteFiltrate = remember(tratte, ricerca) {
        if (ricerca.isBlank()) tratte else tratte.filter { it.nome.contains(ricerca, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text("Alternative per questo slot", style = MaterialTheme.typography.titleMedium)
                CampoRicercaTratte(ricerca = ricerca, onRicercaCambiata = { ricerca = it })
                if (tratteFiltrate.isEmpty()) {
                    Text("Nessuna tratta trovata.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(tratteFiltrate, key = { it.id }) { tratta ->
                            val selezionata = tratta.id in candidatiIds
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlterna(tratta) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(checked = selezionata, onCheckedChange = { onAlterna(tratta) })
                                Text(tratta.nome)
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

/** Campo di ricerca condiviso dai due dialog sopra: stesso stile (icona, pulsante per cancellare) del filtro già usato per la libreria Template/Luoghi. */
@Composable
private fun CampoRicercaTratte(ricerca: String, onRicercaCambiata: (String) -> Unit) {
    OutlinedTextField(
        value = ricerca,
        onValueChange = onRicercaCambiata,
        label = { Text("Cerca") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (ricerca.isNotEmpty()) {
                IconButton(onClick = { onRicercaCambiata("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancella ricerca")
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
    )
}

