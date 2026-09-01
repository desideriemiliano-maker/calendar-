package com.desideri.viaggiotemplate.ui.tratte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.data.remote.clientOrariPer
import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Vettore
import com.desideri.viaggiotemplate.ui.AppContainer
import com.desideri.viaggiotemplate.ui.common.CampoData
import com.desideri.viaggiotemplate.ui.common.CampoOrario
import com.desideri.viaggiotemplate.ui.common.CampoOrarioOpzionale
import com.desideri.viaggiotemplate.ui.common.RicercaOrariTreno
import com.desideri.viaggiotemplate.ui.common.descrizioneFonteOrari
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreNotifica
import com.desideri.viaggiotemplate.ui.common.imageVector
import com.desideri.viaggiotemplate.ui.luoghi.LuoghiViewModel
import com.desideri.viaggiotemplate.ui.luoghi.LuoghiViewModelFactory
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Composable
fun TrattaEditorScreen(
    trattaEsistente: Tratta?,
    nuovoId: () -> String,
    ordineIniziale: () -> Int,
    onSalva: (Tratta) -> Unit,
    onAnnulla: () -> Unit,
    padding: PaddingValues,
    luoghiViewModel: LuoghiViewModel = viewModel(factory = LuoghiViewModelFactory.get())
) {
    val luoghi by luoghiViewModel.luoghi.collectAsState()
    var nome by remember { mutableStateOf(trattaEsistente?.nome ?: "") }
    var tipo by remember { mutableStateOf(trattaEsistente?.tipo ?: TipoTratta.TRENO) }
    var luogoPartenzaId by remember { mutableStateOf(trattaEsistente?.luogoPartenzaId ?: "") }
    var luogoArrivoId by remember { mutableStateOf(trattaEsistente?.luogoArrivoId ?: "") }
    val nomeLuogoPartenza = luoghi.find { it.id == luogoPartenzaId }?.nome ?: ""
    val nomeLuogoArrivo = luoghi.find { it.id == luogoArrivoId }?.nome ?: ""
    val luogoArrivoSelezionato = luoghi.find { it.id == luogoArrivoId }
    var durataMinuti by remember { mutableStateOf((trattaEsistente?.durataMinutiReale ?: 15).toString()) }
    var margine by remember { mutableStateOf((trattaEsistente?.margineMinuti ?: Tratta.margineDefaultPerTipo(tipo)).toString()) }
    var arrotondaInizio by remember { mutableStateOf(trattaEsistente?.arrotondaInizio ?: Arrotondamento.DIFETTO) }
    var arrotondaFine by remember { mutableStateOf(trattaEsistente?.arrotondaFine ?: Arrotondamento.ECCESSO) }
    var step by remember { mutableStateOf((trattaEsistente?.stepArrotondamentoMinuti ?: 10).toString()) }
    var titoloTemplate by remember {
        mutableStateOf(trattaEsistente?.titoloTemplate ?: "{oraPartenza} {luogoPartenza} / {luogoArrivo} {oraArrivo}")
    }
    var opzioni by remember { mutableStateOf(trattaEsistente?.opzioniOrario ?: emptyList()) }
    var orariFissi by remember { mutableStateOf(trattaEsistente?.orariFissi ?: emptyList()) }
    var vettore by remember { mutableStateOf(trattaEsistente?.vettore) }
    var colore by remember { mutableStateOf(trattaEsistente?.colore) }
    var orarioInizioDefault by remember { mutableStateOf<LocalTime?>(trattaEsistente?.orarioInizioDefault) }
    var notifica by remember { mutableStateOf(trattaEsistente?.notifica ?: Notifica.NESSUNA) }

    LazyColumn(
        modifier = Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = nome, onValueChange = { nome = it },
                label = { Text("Nome tratta") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item { SelettoreTipo(tipo) { tipo = it; margine = Tratta.margineDefaultPerTipo(it).toString() } }
        if (tipo == TipoTratta.RIUNIONE) {
            item {
                SelettoreLuogo(
                    etichetta = "Luogo",
                    luoghi = luoghi,
                    luogoSelezionatoId = luogoPartenzaId,
                    onSeleziona = { luogoPartenzaId = it.id },
                    onCreaLuogo = luoghiViewModel::salva,
                    nuovoId = luoghiViewModel::nuovoId,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CampoOrarioOpzionale(
                    etichetta = "Orario di inizio predefinito (opzionale)",
                    valore = orarioInizioDefault,
                    onValoreCambiato = { orarioInizioDefault = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelettoreLuogo(
                        etichetta = "Luogo partenza",
                        luoghi = luoghi,
                        luogoSelezionatoId = luogoPartenzaId,
                        onSeleziona = { luogoPartenzaId = it.id },
                        onCreaLuogo = luoghiViewModel::salva,
                        nuovoId = luoghiViewModel::nuovoId,
                        modifier = Modifier.weight(1f)
                    )
                    SelettoreLuogo(
                        etichetta = "Luogo arrivo",
                        luoghi = luoghi,
                        luogoSelezionatoId = luogoArrivoId,
                        onSeleziona = { luogoArrivoId = it.id },
                        onCreaLuogo = luoghiViewModel::salva,
                        nuovoId = luoghiViewModel::nuovoId,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (tipo == TipoTratta.AUTO) {
                item {
                    val indirizzo = luogoArrivoSelezionato?.indirizzo?.takeIf { it.isNotBlank() }
                    Text(
                        if (indirizzo != null) {
                            "Indirizzo per la navigazione: $indirizzo"
                        } else {
                            "Nessun indirizzo per la navigazione: impostalo sul luogo di arrivo nella scheda Luoghi."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (!tipo.usaOrariProgrammati) {
            item {
                OutlinedTextField(
                    value = durataMinuti, onValueChange = { durataMinuti = it },
                    label = { Text("Durata reale (min)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (tipo == TipoTratta.TRENO) {
            item { SelettoreVettore(vettore) { vettore = it } }
        }
        item {
            OutlinedTextField(
                value = margine, onValueChange = { margine = it },
                label = { Text("Margine di cambio (min)") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelettoreArrotondamento("Arrotonda inizio", arrotondaInizio, Modifier.weight(1f)) { arrotondaInizio = it }
                SelettoreArrotondamento("Arrotonda fine", arrotondaFine, Modifier.weight(1f)) { arrotondaFine = it }
            }
        }
        item {
            OutlinedTextField(
                value = step, onValueChange = { step = it },
                label = { Text("Step arrotondamento (min)") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = titoloTemplate, onValueChange = { titoloTemplate = it },
                label = { Text("Titolo evento (placeholder: {oraPartenza} {luogoPartenza} {luogoArrivo} {oraArrivo})") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (tipo.usaOrariProgrammati) {
            item { Text("Opzioni orario", style = androidx.compose.material3.MaterialTheme.typography.titleSmall) }
            items(opzioni, key = { it.id }) { opzione ->
                EditorOpzioneOrario(
                    opzione = opzione,
                    onCambia = { aggiornata -> opzioni = opzioni.map { if (it.id == aggiornata.id) aggiornata else it } },
                    onElimina = { opzioni = opzioni.filterNot { it.id == opzione.id } }
                )
            }
            item {
                TextButton(onClick = {
                    opzioni = opzioni + OpzioneOrario(
                        id = UUID.randomUUID().toString(),
                        minutoPartenza = 0,
                        cadenzaOre = 1,
                        parita = null,
                        offsetOreArrivo = 0,
                        minutoArrivo = 15
                    )
                }) { Text("+ Aggiungi opzione orario") }
            }

            item {
                Text("Orari fissi (opzionali)", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(
                    "Orari non ricorrenti (es. un treno straordinario), in alternativa ai pattern sopra: " +
                        "in Esegui potrai scegliere quale usare per la tratta ancora, mentre per le altre tratte " +
                        "il calcolo sceglie automaticamente il migliore tra fisso e ricorrente.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
            items(orariFissi, key = { it.id }) { fisso ->
                EditorOrarioFisso(
                    orario = fisso,
                    onCambia = { aggiornato -> orariFissi = orariFissi.map { if (it.id == aggiornato.id) aggiornato else it } },
                    onElimina = { orariFissi = orariFissi.filterNot { it.id == fisso.id } }
                )
            }
            item {
                TextButton(onClick = {
                    orariFissi = orariFissi + OrarioFisso(
                        id = UUID.randomUUID().toString(),
                        partenza = LocalTime.of(0, 0),
                        arrivo = LocalTime.of(0, 15)
                    )
                }) { Text("+ Aggiungi orario fisso") }
            }
            val vettoreCorrente = vettore
            if (tipo == TipoTratta.TRENO && vettoreCorrente != null && clientOrariPer(vettoreCorrente) != null) {
                item {
                    var mostraDialogOrariReali by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { mostraDialogOrariReali = true },
                        enabled = nomeLuogoPartenza.isNotBlank() && nomeLuogoArrivo.isNotBlank()
                    ) { Text("Scarica orari da ${vettoreCorrente.name}") }
                    if (mostraDialogOrariReali) {
                        DialogScaricaOrariTreno(
                            vettore = vettoreCorrente,
                            daStazione = nomeLuogoPartenza,
                            aStazione = nomeLuogoArrivo,
                            onAggiungi = { corsa ->
                                val giaPresente = orariFissi.any { it.partenza == corsa.partenza && it.arrivo == corsa.arrivo }
                                if (!giaPresente) {
                                    orariFissi = orariFissi + OrarioFisso(
                                        id = UUID.randomUUID().toString(),
                                        partenza = corsa.partenza,
                                        arrivo = corsa.arrivo,
                                        etichetta = corsa.etichetta.ifBlank { null }
                                    )
                                }
                            },
                            onDismiss = { mostraDialogOrariReali = false }
                        )
                    }
                }
            }
        }

        item {
            SelettoreColore(coloreSelezionato = colore, onCambia = { colore = it })
        }

        item { SelettoreNotifica(notifica) { notifica = it } }

        item {
            val luogoArrivoIdFinale = if (tipo == TipoTratta.RIUNIONE) luogoPartenzaId else luogoArrivoId
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = luogoPartenzaId.isNotBlank() && luogoArrivoIdFinale.isNotBlank(),
                    onClick = {
                    val tratta = Tratta(
                        id = trattaEsistente?.id ?: nuovoId(),
                        nome = nome,
                        tipo = tipo,
                        luogoPartenzaId = luogoPartenzaId,
                        luogoArrivoId = luogoArrivoIdFinale,
                        durataMinutiReale = durataMinuti.toIntOrNull() ?: 0,
                        margineMinuti = margine.toIntOrNull() ?: 0,
                        arrotondaInizio = arrotondaInizio,
                        arrotondaFine = arrotondaFine,
                        stepArrotondamentoMinuti = step.toIntOrNull() ?: 10,
                        titoloTemplate = titoloTemplate,
                        opzioniOrario = if (tipo.usaOrariProgrammati) opzioni else emptyList(),
                        orariFissi = if (tipo.usaOrariProgrammati) orariFissi else emptyList(),
                        vettore = if (tipo == TipoTratta.TRENO) vettore else null,
                        ordine = trattaEsistente?.ordine ?: ordineIniziale(),
                        colore = colore,
                        orarioInizioDefault = if (tipo == TipoTratta.RIUNIONE) orarioInizioDefault else null,
                        notifica = notifica
                    )
                    onSalva(tratta)
                }) { Text("Salva") }
                TextButton(onClick = onAnnulla) { Text("Annulla") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreTipo(tipo: TipoTratta, onCambia: (TipoTratta) -> Unit) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
        OutlinedTextField(
            value = tipo.name, onValueChange = {}, readOnly = true,
            label = { Text("Tipo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            TipoTratta.values().forEach { opzione ->
                DropdownMenuItem(text = { Text(opzione.name) }, onClick = { onCambia(opzione); espanso = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreVettore(vettore: Vettore?, onCambia: (Vettore?) -> Unit) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
        OutlinedTextField(
            value = vettore?.name ?: "Non specificato", onValueChange = {}, readOnly = true,
            label = { Text("Vettore") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            DropdownMenuItem(text = { Text("Non specificato") }, onClick = { onCambia(null); espanso = false })
            Vettore.values().forEach { opzione ->
                DropdownMenuItem(text = { Text(opzione.name) }, onClick = { onCambia(opzione); espanso = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreArrotondamento(
    etichetta: String,
    valore: Arrotondamento,
    modifier: Modifier = Modifier,
    onCambia: (Arrotondamento) -> Unit
) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }, modifier = modifier) {
        OutlinedTextField(
            value = valore.name, onValueChange = {}, readOnly = true,
            label = { Text(etichetta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            Arrotondamento.values().forEach { opzione ->
                DropdownMenuItem(text = { Text(opzione.name) }, onClick = { onCambia(opzione); espanso = false })
            }
        }
    }
}

/**
 * Dropdown ricercabile per scegliere un Luogo tra quelli esistenti: con molti luoghi in libreria
 * scorrere un menu non filtrato diventa scomodo, quindi il campo è editabile e filtra la lista
 * (per nome o indirizzo) mentre si digita, invece del solo testo readonly di prima. Mantiene
 * sempre una voce per crearne uno al volo senza uscire dall'editor tratta — necessario perché la
 * lista Luoghi può anche essere vuota al primo utilizzo — precompilata col testo digitato quando
 * non corrisponde a nessun luogo esistente.
 *
 * Elenco ordinato alfabeticamente per nome (non per l'ordine manuale della schermata Luoghi):
 * con la ricerca a disposizione l'ordinamento serve soprattutto quando il campo è ancora vuoto
 * (appena aperto), e in quel caso l'alfabetico è quello che permette di saltare subito alla
 * lettera giusta — l'ordine manuale è pensato per scorrere la lista intera in Luoghi, non per
 * uno sguardo rapido qui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreLuogo(
    etichetta: String,
    luoghi: List<Luogo>,
    luogoSelezionatoId: String,
    onSeleziona: (Luogo) -> Unit,
    onCreaLuogo: (Luogo) -> Unit,
    nuovoId: () -> String,
    modifier: Modifier = Modifier
) {
    var espanso by remember { mutableStateOf(false) }
    var mostraDialogNuovo by remember { mutableStateOf(false) }
    val selezionato = luoghi.find { it.id == luogoSelezionatoId }
    // Riparte sempre vuota quando il menu si apre (vedi onExpandedChange sotto): mostra subito la
    // lista intera invece di filtrarla sul nome già selezionato, che nasconderebbe le alternative.
    var ricerca by remember { mutableStateOf("") }

    val luoghiOrdinati = remember(luoghi) { luoghi.sortedBy { it.nome.lowercase() } }
    val luoghiFiltrati = remember(luoghiOrdinati, ricerca) {
        if (ricerca.isBlank()) {
            luoghiOrdinati
        } else {
            luoghiOrdinati.filter {
                it.nome.contains(ricerca, ignoreCase = true) || it.indirizzo?.contains(ricerca, ignoreCase = true) == true
            }
        }
    }
    val corrispondenzaEsatta = luoghiFiltrati.any { it.nome.equals(ricerca.trim(), ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = espanso,
        onExpandedChange = { nuovoEspanso -> espanso = nuovoEspanso; if (nuovoEspanso) ricerca = "" },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (espanso) ricerca else (selezionato?.nome ?: ""),
            onValueChange = { ricerca = it; espanso = true },
            label = { Text(etichetta) },
            placeholder = { Text("Cerca…") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        )
        DropdownMenu(
            expanded = espanso,
            onDismissRequest = { espanso = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            if (luoghiFiltrati.isEmpty()) {
                DropdownMenuItem(text = { Text("Nessun luogo trovato") }, onClick = {}, enabled = false)
            }
            luoghiFiltrati.forEach { luogo ->
                val luogoSelezionato = luogo.id == luogoSelezionatoId
                DropdownMenuItem(
                    text = { VoceLuogo(luogo, selezionato = luogoSelezionato) },
                    onClick = { onSeleziona(luogo); espanso = false },
                    modifier = if (luogoSelezionato) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
            }
            if (luoghiFiltrati.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (ricerca.isNotBlank() && !corrispondenzaEsatta) "+ Nuovo luogo \"${ricerca.trim()}\"…" else "+ Nuovo luogo…") },
                onClick = { espanso = false; mostraDialogNuovo = true }
            )
        }
    }

    if (mostraDialogNuovo) {
        DialogNuovoLuogo(
            nomeIniziale = if (ricerca.isNotBlank() && !corrispondenzaEsatta) ricerca.trim() else "",
            onConferma = { nome, indirizzo ->
                val luogo = Luogo(id = nuovoId(), nome = nome, indirizzo = indirizzo.ifBlank { null })
                onCreaLuogo(luogo)
                onSeleziona(luogo)
                mostraDialogNuovo = false
            },
            onAnnulla = { mostraDialogNuovo = false }
        )
    }
}

/** Riga di un luogo nel dropdown ricercabile: icona e colore come nella card della lista Luoghi, nome bold + segno di spunta quando è quello già selezionato. */
@Composable
private fun VoceLuogo(luogo: Luogo, selezionato: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(luogo.colore?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                luogo.icona?.imageVector() ?: Icons.Filled.Place,
                contentDescription = null,
                tint = if (luogo.colore != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                luogo.nome,
                fontWeight = if (selezionato) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            luogo.indirizzo?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selezionato) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = "Selezionato", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DialogNuovoLuogo(nomeIniziale: String = "", onConferma: (nome: String, indirizzo: String) -> Unit, onAnnulla: () -> Unit) {
    var nome by remember { mutableStateOf(nomeIniziale) }
    var indirizzo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Nuovo luogo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = indirizzo, onValueChange = { indirizzo = it }, label = { Text("Indirizzo (opzionale)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = { onConferma(nome.trim(), indirizzo.trim()) }
            ) { Text("Crea") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

@Composable
private fun EditorOpzioneOrario(
    opzione: OpzioneOrario,
    onCambia: (OpzioneOrario) -> Unit,
    onElimina: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = opzione.minutoPartenza.toString(),
                    onValueChange = { onCambia(opzione.copy(minutoPartenza = it.toIntOrNull() ?: 0)) },
                    label = { Text("Min. partenza") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = opzione.offsetOreArrivo.toString(),
                    onValueChange = { onCambia(opzione.copy(offsetOreArrivo = it.toIntOrNull() ?: 0)) },
                    label = { Text("Offset ore arrivo") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = opzione.minutoArrivo.toString(),
                    onValueChange = { onCambia(opzione.copy(minutoArrivo = it.toIntOrNull() ?: 0)) },
                    label = { Text("Min. arrivo") }, modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = opzione.cadenzaOre.toString(),
                    onValueChange = { onCambia(opzione.copy(cadenzaOre = it.toIntOrNull() ?: 1)) },
                    label = { Text("Cadenza (ore)") }, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = opzione.parita?.toString() ?: "",
                    onValueChange = { onCambia(opzione.copy(parita = it.toIntOrNull())) },
                    label = { Text("Parità (0=pari,1=dispari)") }, modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = opzione.etichetta ?: "",
                onValueChange = { onCambia(opzione.copy(etichetta = it.ifBlank { null })) },
                label = { Text("Etichetta (opzionale)") }, modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onElimina) { Text("Rimuovi questa opzione") }
        }
    }
}

@Composable
private fun EditorOrarioFisso(
    orario: OrarioFisso,
    onCambia: (OrarioFisso) -> Unit,
    onElimina: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoOrario(
                    etichetta = "Partenza",
                    valore = orario.partenza,
                    onValoreCambiato = { onCambia(orario.copy(partenza = it)) },
                    modifier = Modifier.weight(1f)
                )
                CampoOrario(
                    etichetta = "Arrivo",
                    valore = orario.arrivo,
                    onValoreCambiato = { onCambia(orario.copy(arrivo = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = orario.etichetta ?: "",
                onValueChange = { onCambia(orario.copy(etichetta = it.ifBlank { null })) },
                label = { Text("Etichetta (opzionale)") }, modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onElimina) { Text("Rimuovi questo orario fisso") }
        }
    }
}

/**
 * Cerca le corse tra due stazioni per un giorno scelto sulla fonte non ufficiale associata a
 * [vettore] (vedi [descrizioneFonteOrari]), e permette di aggiungerle come Orari fissi con un tocco.
 */
@Composable
private fun DialogScaricaOrariTreno(
    vettore: Vettore,
    daStazione: String,
    aStazione: String,
    onAggiungi: (CorsaScaricata) -> Unit,
    onDismiss: () -> Unit
) {
    var data by remember { mutableStateOf(LocalDate.now()) }
    var oraRiferimento by remember { mutableStateOf(LocalTime.of(6, 0)) }
    var chiaveRicerca by remember { mutableStateOf(0) }
    var aggiunte by remember { mutableStateOf(setOf<CorsaScaricata>()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 520.dp)) {
                Text("Scarica orari da ${vettore.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$daStazione → $aStazione, ${descrizioneFonteOrari(vettore)}.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoData(valore = data, onValoreCambiato = { data = it }, modifier = Modifier.weight(1f))
                    CampoOrario(
                        etichetta = "A partire dalle",
                        valore = oraRiferimento,
                        onValoreCambiato = { oraRiferimento = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { chiaveRicerca++ }) { Text("Cerca") }
                }

                RicercaOrariTreno(
                    vettore = vettore,
                    daStazione = daStazione,
                    aStazione = aStazione,
                    data = data,
                    oraRiferimento = oraRiferimento,
                    limite = 16,
                    chiaveRicerca = chiaveRicerca,
                    credenzialiItalo = AppContainer.italoCredentialsStore.credenziali,
                    testoAzione = { corsa -> if (corsa in aggiunte) "Aggiunta" else "+ Aggiungi" },
                    azioneAbilitata = { corsa -> corsa !in aggiunte },
                    onAzione = { corsa -> onAggiungi(corsa); aggiunte = aggiunte + corsa }
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}
