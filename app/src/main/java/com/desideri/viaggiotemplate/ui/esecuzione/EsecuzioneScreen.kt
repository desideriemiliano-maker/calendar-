package com.desideri.viaggiotemplate.ui.esecuzione

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.ui.common.CampoOrario
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsecuzioneScreen(viewModel: EsecuzioneViewModel = viewModel(factory = EsecuzioneViewModelFactory.get())) {
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    val permessiCalendario = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    fun permessiGiaConcessi() = permessiCalendario.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permessoConcesso by remember { mutableStateOf(permessiGiaConcessi()) }
    val richiediPermessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { risultati ->
        permessoConcesso = risultati.values.all { it }
    }

    LaunchedEffect(permessoConcesso) {
        if (permessoConcesso) viewModel.aggiornaCalendarioConfigurato(context)
    }

    var espansoTemplate by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Esegui template") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = { espansoTemplate = true }) {
                    Text(stato.templateSelezionato?.nome ?: "Scegli un template")
                }
                DropdownMenu(expanded = espansoTemplate, onDismissRequest = { espansoTemplate = false }) {
                    stato.templateEntities.forEach { entity ->
                        DropdownMenuItem(
                            text = { Text(entity.nome) },
                            onClick = { viewModel.selezionaTemplate(entity.id); espansoTemplate = false }
                        )
                    }
                }
            }

            if (stato.templateSelezionato != null) {
                item {
                    Text("Data del viaggio")
                    CampoData(valore = stato.data, onValoreCambiato = { viewModel.aggiornaData(it) })
                }
                val slotsAncora = stato.templateSelezionato?.slots?.filter { it.ancora } ?: emptyList()
                items(slotsAncora, key = { "ancora-${it.id}" }) { slot ->
                    val trattaAncora = stato.tratte[slot.trattaSelezionataId]
                    if (trattaAncora?.orarioInizioDefault != null) {
                        val fine = trattaAncora.orarioInizioDefault.plusMinutes(trattaAncora.durataMinutiReale.toLong())
                        Text(
                            "Ancora fissa: ${trattaAncora.nome} (${trattaAncora.luogoPartenza}) — " +
                                "${trattaAncora.orarioInizioDefault.toStringHHmm()} → ${fine.toStringHHmm()}"
                        )
                    } else {
                        Text(
                            if (trattaAncora != null) {
                                "Ancora: ${trattaAncora.nome} (${trattaAncora.luogoPartenza} → ${trattaAncora.luogoArrivo}) — orario partenza / arrivo reali"
                            } else {
                                "Orario tratta ancora (partenza / arrivo reali)"
                            }
                        )
                        val (inizioInput, fineInput) = stato.orariAncoreInput[slot.id]
                            ?: (LocalTime.of(9, 0) to LocalTime.of(9, 30))
                        if (trattaAncora != null && trattaAncora.orariFissi.isNotEmpty()) {
                            SelettoreOrarioAncora(
                                orariFissi = trattaAncora.orariFissi,
                                inizio = inizioInput,
                                fine = fineInput,
                                onCambia = { i, f -> viewModel.aggiornaOrarioAncora(slot.id, i, f) }
                            )
                        } else {
                            RigaOrario(
                                inizio = inizioInput,
                                fine = fineInput,
                                onCambia = { i, f -> viewModel.aggiornaOrarioAncora(slot.id, i, f) }
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.calcola() }) { Text("Calcola orari") }
                        TextButton(onClick = { viewModel.pulisciRisultati() }) { Text("Pulisci") }
                    }
                }
                item {
                    var mostraDialogAggiungi by remember { mutableStateOf(false) }
                    TextButton(onClick = { mostraDialogAggiungi = true }) { Text("+ Aggiungi tratta a questo viaggio") }
                    if (mostraDialogAggiungi) {
                        DialogAggiungiTratta(
                            libreria = stato.libreriaTratte,
                            slotsCorrenti = stato.templateSelezionato?.slotsOrdinati ?: emptyList(),
                            nomeTratta = { id -> stato.tratte[id]?.nome ?: id },
                            onConferma = { trattaId, dopoSlotId ->
                                viewModel.aggiungiTrattaExtra(trattaId, dopoSlotId)
                                mostraDialogAggiungi = false
                            },
                            onDismiss = { mostraDialogAggiungi = false }
                        )
                    }
                }
            }

            items(stato.eventiCalcolati, key = { it.templateSlotId }) { evento ->
                CardEvento(
                    evento = evento,
                    onScegliAlternativa = { nuovaTrattaId ->
                        viewModel.scegliAlternativa(evento.templateSlotId, nuovaTrattaId)
                    },
                    onModificaManuale = { inizio, fine ->
                        viewModel.sovrascriviEvento(evento.templateSlotId, inizio, fine)
                    },
                    onElimina = { viewModel.eliminaEvento(evento.templateSlotId) }
                )
            }

            if (stato.eventiCalcolati.isNotEmpty()) {
                if (!permessoConcesso) {
                    item {
                        Button(onClick = { richiediPermessi.launch(permessiCalendario) }) {
                            Text("Consenti accesso al calendario")
                        }
                    }
                } else if (!stato.calendarioConfiguratoVerificato) {
                    // permesso appena concesso, in attesa della prima lettura del calendario configurato
                } else if (stato.calendarioConfigurato == null) {
                    item {
                        Text("Nessun calendario configurato: vai nella sezione Impostazioni per sceglierne uno.")
                    }
                } else {
                    item {
                        Text("Verrà scritto su: ${stato.calendarioConfigurato!!.nome} (${stato.calendarioConfigurato!!.account})")
                    }
                    item {
                        Button(onClick = { viewModel.aggiungiAlCalendario(context) }) {
                            Text("Aggiungi al calendario")
                        }
                    }
                }
            }

            stato.messaggio?.let { msg ->
                item { Text(msg) }
            }
        }
    }
}

@Composable
private fun RigaOrario(
    inizio: LocalTime,
    fine: LocalTime,
    onCambia: (LocalTime, LocalTime) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CampoOrario(
            etichetta = "Partenza (HH:mm)",
            valore = inizio,
            onValoreCambiato = { onCambia(it, fine) },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        CampoOrario(
            etichetta = "Arrivo (HH:mm)",
            valore = fine,
            onValoreCambiato = { onCambia(inizio, it) },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

/**
 * Per una tratta-ancora TRENO con orari fissi configurati: scelta tra un orario fisso
 * (dropdown, tra quelli definiti sulla Tratta) e l'orario ricorrente inserito a mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreOrarioAncora(
    orariFissi: List<OrarioFisso>,
    inizio: LocalTime,
    fine: LocalTime,
    onCambia: (LocalTime, LocalTime) -> Unit
) {
    var usaFisso by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { usaFisso = false }) { Text(if (!usaFisso) "● Ricorrente" else "Ricorrente") }
            TextButton(onClick = {
                usaFisso = true
                orariFissi.firstOrNull()?.let { onCambia(it.partenza, it.arrivo) }
            }) { Text(if (usaFisso) "● Fisso" else "Fisso") }
        }
        if (usaFisso) {
            var espanso by remember { mutableStateOf(false) }
            val selezionato = orariFissi.firstOrNull { it.partenza == inizio && it.arrivo == fine } ?: orariFissi.first()
            fun etichetta(o: OrarioFisso) = (o.etichetta?.let { "$it — " } ?: "") + "${o.partenza.toStringHHmm()} → ${o.arrivo.toStringHHmm()}"
            ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }) {
                OutlinedTextField(
                    value = etichetta(selezionato),
                    onValueChange = {}, readOnly = true,
                    label = { Text("Orario fisso") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
                    orariFissi.forEach { fisso ->
                        DropdownMenuItem(
                            text = { Text(etichetta(fisso)) },
                            onClick = { onCambia(fisso.partenza, fisso.arrivo); espanso = false }
                        )
                    }
                }
            }
        } else {
            RigaOrario(inizio = inizio, fine = fine, onCambia = onCambia)
        }
    }
}

/** Dialog per aggiungere temporaneamente una tratta della libreria a questa sola esecuzione. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogAggiungiTratta(
    libreria: List<Tratta>,
    slotsCorrenti: List<TemplateSlot>,
    nomeTratta: (String) -> String,
    onConferma: (trattaId: String, dopoSlotId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var trattaSelezionata by remember { mutableStateOf(libreria.firstOrNull()) }
    var dopoSlotId by remember { mutableStateOf(slotsCorrenti.lastOrNull()?.id) }
    var espansoTratta by remember { mutableStateOf(false) }
    var espansoPosizione by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aggiungi una tratta a questo viaggio", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Valida solo per questa esecuzione: non modifica il template salvato.",
                    style = MaterialTheme.typography.bodySmall
                )

                ExposedDropdownMenuBox(expanded = espansoTratta, onExpandedChange = { espansoTratta = it }) {
                    OutlinedTextField(
                        value = trattaSelezionata?.nome ?: "Scegli una tratta",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Tratta") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espansoTratta) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    DropdownMenu(
                        expanded = espansoTratta,
                        onDismissRequest = { espansoTratta = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        libreria.forEach { tratta ->
                            DropdownMenuItem(
                                text = { Text(tratta.nome) },
                                onClick = { trattaSelezionata = tratta; espansoTratta = false }
                            )
                        }
                    }
                }

                val etichettaPosizione = dopoSlotId
                    ?.let { id -> slotsCorrenti.firstOrNull { it.id == id } }
                    ?.let { "Dopo: ${nomeTratta(it.trattaSelezionataId)}" }
                    ?: "All'inizio del viaggio"
                ExposedDropdownMenuBox(expanded = espansoPosizione, onExpandedChange = { espansoPosizione = it }) {
                    OutlinedTextField(
                        value = etichettaPosizione,
                        onValueChange = {}, readOnly = true,
                        label = { Text("Posizione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espansoPosizione) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )
                    DropdownMenu(
                        expanded = espansoPosizione,
                        onDismissRequest = { espansoPosizione = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All'inizio del viaggio") },
                            onClick = { dopoSlotId = null; espansoPosizione = false }
                        )
                        slotsCorrenti.forEach { slot ->
                            DropdownMenuItem(
                                text = { Text("Dopo: ${nomeTratta(slot.trattaSelezionataId)}") },
                                onClick = { dopoSlotId = slot.id; espansoPosizione = false }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annulla") }
                    TextButton(
                        onClick = { trattaSelezionata?.let { onConferma(it.id, dopoSlotId) } },
                        enabled = trattaSelezionata != null
                    ) { Text("Aggiungi") }
                }
            }
        }
    }
}

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Campo data: apre un popup con calendario Material3 (che offre anche l'inserimento manuale tramite la sua icona tastiera). */
@Composable
private fun CampoData(
    valore: LocalDate,
    onValoreCambiato: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var mostraPopup by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valore.format(FORMATO_DATA),
        onValueChange = {},
        readOnly = true,
        label = { Text("Data (gg/mm/aaaa)") },
        trailingIcon = {
            IconButton(onClick = { mostraPopup = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Scegli data")
            }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (mostraPopup) {
        DialogSelettoreData(
            dataIniziale = valore,
            onConferma = { onValoreCambiato(it); mostraPopup = false },
            onDismiss = { mostraPopup = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogSelettoreData(
    dataIniziale: LocalDate,
    onConferma: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Il DatePicker di Material3 lavora in millisecondi UTC riferiti alla mezzanotte UTC del giorno scelto.
    val statoData = rememberDatePickerState(
        initialSelectedDateMillis = dataIniziale.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = statoData.selectedDateMillis
                if (millis != null) {
                    onConferma(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    ) {
        DatePicker(state = statoData)
    }
}

@Composable
private fun CardEvento(
    evento: EventoCalcolato,
    onScegliAlternativa: (String) -> Unit,
    onModificaManuale: (LocalTime, LocalTime) -> Unit,
    onElimina: () -> Unit
) {
    var modificaManuale by remember { mutableStateOf(false) }
    var confermaEliminazione by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    evento.titolo(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { confermaEliminazione = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Rimuovi questa tratta", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "Blocco calendario: ${evento.inizioBlocco.toStringHHmm()} → ${evento.fineBlocco.toStringHHmm()}",
                style = MaterialTheme.typography.bodySmall
            )

            if (evento.alternative.isNotEmpty()) {
                Text("Alternative:", style = MaterialTheme.typography.labelMedium)
                evento.alternative.forEach { alt ->
                    TextButton(onClick = { onScegliAlternativa(alt.tratta.id) }) {
                        Text("${alt.tratta.nome}: ${alt.inizioReale.toStringHHmm()} → ${alt.fineReale.toStringHHmm()}")
                    }
                }
            }

            TextButton(onClick = { modificaManuale = !modificaManuale }) {
                Text(if (modificaManuale) "Chiudi modifica manuale" else "Modifica manualmente questo orario")
            }
            if (modificaManuale) {
                RigaOrario(
                    inizio = evento.inizioReale,
                    fine = evento.fineReale,
                    onCambia = onModificaManuale
                )
                Text(
                    "Modificando l'orario, questa tratta diventa un punto fisso: le altre verranno ricalcolate anche in base ad essa.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (confermaEliminazione) {
        DialogConfermaEliminazione(
            nomeElemento = evento.tratta.nome,
            onConferma = { onElimina(); confermaEliminazione = false },
            onAnnulla = { confermaEliminazione = false }
        )
    }
}
