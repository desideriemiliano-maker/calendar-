package com.desideri.viaggiotemplate.ui.tratte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.ui.common.CampoOrarioOpzionale
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
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
    var colore by remember { mutableStateOf(trattaEsistente?.colore) }
    var orarioInizioDefault by remember { mutableStateOf<LocalTime?>(trattaEsistente?.orarioInizioDefault) }

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
        if (tipo != TipoTratta.TRENO) {
            item {
                OutlinedTextField(
                    value = durataMinuti, onValueChange = { durataMinuti = it },
                    label = { Text("Durata reale (min)") }, modifier = Modifier.fillMaxWidth()
                )
            }
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

        if (tipo == TipoTratta.TRENO) {
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
        }

        item {
            SelettoreColore(coloreSelezionato = colore, onCambia = { colore = it })
        }

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
                        opzioniOrario = opzioni,
                        ordine = trattaEsistente?.ordine ?: ordineIniziale(),
                        colore = colore,
                        orarioInizioDefault = if (tipo == TipoTratta.RIUNIONE) orarioInizioDefault else null
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
