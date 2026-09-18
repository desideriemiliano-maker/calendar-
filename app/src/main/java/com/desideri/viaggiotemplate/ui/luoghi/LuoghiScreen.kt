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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.model.IconaLuogo
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.repository.RuoloLuogoInTratta
import com.desideri.viaggiotemplate.ui.common.ContatoreElementi
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import com.desideri.viaggiotemplate.ui.common.dragDropItem
import com.desideri.viaggiotemplate.ui.common.dragHandle
import com.desideri.viaggiotemplate.ui.common.imageVector
import com.desideri.viaggiotemplate.ui.common.rememberDragDropListState

@OptIn(ExperimentalMaterial3Api::class)
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
                ordineIniziale = viewModel::prossimoOrdine,
                onSalva = { luogo ->
                    viewModel.salva(luogo)
                    mostraEditor = false
                },
                onAnnulla = { mostraEditor = false },
                padding = padding
            )
        } else {
            var testoRicerca by remember { mutableStateOf("") }
            var filtroIcona by remember { mutableStateOf<FiltroIconaLuogo>(FiltroIconaLuogo.Tutti) }
            var menuFiltroEspanso by remember { mutableStateOf(false) }

            val luoghiFiltrati = remember(luoghi, testoRicerca, filtroIcona) {
                luoghi.filter { luogo ->
                    (testoRicerca.isBlank() ||
                        luogo.nome.contains(testoRicerca, ignoreCase = true) ||
                        luogo.indirizzo?.contains(testoRicerca, ignoreCase = true) == true) &&
                        when (val filtro = filtroIcona) {
                            FiltroIconaLuogo.Tutti -> true
                            FiltroIconaLuogo.SenzaIcona -> luogo.icona == null
                            is FiltroIconaLuogo.Tipo -> luogo.icona == filtro.icona
                        }
                }
            }

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
                            value = filtroIcona.etichetta,
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
                                text = { Text(FiltroIconaLuogo.Tutti.etichetta) },
                                onClick = {
                                    filtroIcona = FiltroIconaLuogo.Tutti
                                    menuFiltroEspanso = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(FiltroIconaLuogo.SenzaIcona.etichetta) },
                                onClick = {
                                    filtroIcona = FiltroIconaLuogo.SenzaIcona
                                    menuFiltroEspanso = false
                                }
                            )
                            IconaLuogo.values().forEach { icona ->
                                DropdownMenuItem(
                                    text = { Text(icona.etichetta) },
                                    onClick = {
                                        filtroIcona = FiltroIconaLuogo.Tipo(icona)
                                        menuFiltroEspanso = false
                                    }
                                )
                            }
                        }
                    }
                }
                ContatoreElementi(mostrati = luoghiFiltrati.size, totale = luoghi.size)
                val lazyListState = rememberLazyListState()
                val dragDropListState = rememberDragDropListState(lazyListState) { da, a ->
                    viewModel.sposta(luoghiFiltrati[da], a - da)
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(luoghiFiltrati, key = { _, luogo -> luogo.id }) { indice, luogo ->
                        Card(
                            onClick = { luogoInModifica = luogo; mostraEditor = true },
                            modifier = Modifier.fillMaxWidth().dragDropItem(dragDropListState, indice),
                            colors = luogo.colore?.let {
                                CardDefaults.cardColors(containerColor = Color(it), contentColor = Color(0xFF1B1B1B))
                            } ?: CardDefaults.cardColors()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    luogo.icona?.imageVector() ?: Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(luogo.nome, style = MaterialTheme.typography.titleMedium)
                                    luogo.indirizzo?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Trascina per riordinare",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.dragHandle(dragDropListState, indice)
                                )
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

    erroreEliminazione?.let { tratte ->
        val massimoElencate = 10
        val messaggio = buildString {
            append(
                if (tratte.size == 1) "È usato da 1 tratta:" else "È usato da ${tratte.size} tratte:"
            )
            tratte.take(massimoElencate).forEach { uso ->
                val ruolo = when (uso.ruolo) {
                    RuoloLuogoInTratta.PARTENZA -> "partenza"
                    RuoloLuogoInTratta.ARRIVO -> "arrivo"
                    RuoloLuogoInTratta.ENTRAMBI -> "partenza e arrivo"
                }
                append("\n• ${uso.nomeTratta} ($ruolo)")
            }
            val altre = tratte.size - massimoElencate
            if (altre > 0) append("\n… e altre $altre")
            append("\n\nRimuovilo prima da lì (o assegna a quelle tratte un altro luogo).")
        }
        AlertDialog(
            onDismissRequest = viewModel::chiudiErroreEliminazione,
            title = { Text("Impossibile eliminare il luogo") },
            text = { Text(messaggio) },
            confirmButton = { TextButton(onClick = viewModel::chiudiErroreEliminazione) { Text("OK") } }
        )
    }
}

/**
 * Filtro per tipo nel menu a tendina accanto alla ricerca (stesso pattern di `filtroTipo` in
 * `TratteScreen`). `IconaLuogo?` da solo non basterebbe: null è già il valore di un Luogo senza
 * icona, quindi servirebbe a distinguere "nessun filtro attivo" da "filtra i luoghi senza icona"
 * — da qui una sealed class con un caso esplicito per ciascuno.
 */
private sealed class FiltroIconaLuogo(val etichetta: String) {
    data object Tutti : FiltroIconaLuogo("Tutti")
    data object SenzaIcona : FiltroIconaLuogo("Senza icona")
    data class Tipo(val icona: IconaLuogo) : FiltroIconaLuogo(icona.etichetta)
}
