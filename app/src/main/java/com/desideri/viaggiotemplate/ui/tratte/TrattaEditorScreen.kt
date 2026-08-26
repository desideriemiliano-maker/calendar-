package com.desideri.viaggiotemplate.ui.tratte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Vettore
import com.desideri.viaggiotemplate.ui.common.CampoData
import com.desideri.viaggiotemplate.ui.common.CampoOrario
import com.desideri.viaggiotemplate.ui.common.CampoOrarioOpzionale
import com.desideri.viaggiotemplate.ui.common.RicercaOrariSbb
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreNotifica
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
    padding: PaddingValues
) {
    var nome by remember { mutableStateOf(trattaEsistente?.nome ?: "") }
    var tipo by remember { mutableStateOf(trattaEsistente?.tipo ?: TipoTratta.TRENO) }
    var luogoPartenza by remember { mutableStateOf(trattaEsistente?.luogoPartenza ?: "") }
    var luogoArrivo by remember { mutableStateOf(trattaEsistente?.luogoArrivo ?: "") }
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
                OutlinedTextField(
                    value = luogoPartenza, onValueChange = { luogoPartenza = it },
                    label = { Text("Luogo") }, modifier = Modifier.fillMaxWidth()
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
                    OutlinedTextField(
                        value = luogoPartenza, onValueChange = { luogoPartenza = it },
                        label = { Text("Luogo partenza") }, modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    OutlinedTextField(
                        value = luogoArrivo, onValueChange = { luogoArrivo = it },
                        label = { Text("Luogo arrivo") }, modifier = Modifier.fillMaxWidth().weight(1f)
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
            if (tipo == TipoTratta.TRENO && vettore == Vettore.SBB) {
                item {
                    var mostraDialogSbb by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { mostraDialogSbb = true },
                        enabled = luogoPartenza.isNotBlank() && luogoArrivo.isNotBlank()
                    ) { Text("Scarica orari da SBB") }
                    if (mostraDialogSbb) {
                        DialogScaricaOrariSbb(
                            daStazione = luogoPartenza,
                            aStazione = luogoArrivo,
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
                            onDismiss = { mostraDialogSbb = false }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val tratta = Tratta(
                        id = trattaEsistente?.id ?: nuovoId(),
                        nome = nome,
                        tipo = tipo,
                        luogoPartenza = luogoPartenza,
                        luogoArrivo = if (tipo == TipoTratta.RIUNIONE) luogoPartenza else luogoArrivo,
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
 * Cerca le corse tra due stazioni su transport.opendata.ch (API pubblica dei trasporti svizzeri,
 * include SBB) per un giorno scelto, e permette di aggiungerle come Orari fissi con un tocco.
 */
@Composable
private fun DialogScaricaOrariSbb(
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
                Text("Scarica orari da SBB", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$daStazione → $aStazione, dati da transport.opendata.ch (trasporti pubblici svizzeri).",
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

                RicercaOrariSbb(
                    daStazione = daStazione,
                    aStazione = aStazione,
                    data = data,
                    oraRiferimento = oraRiferimento,
                    limite = 16,
                    chiaveRicerca = chiaveRicerca,
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
