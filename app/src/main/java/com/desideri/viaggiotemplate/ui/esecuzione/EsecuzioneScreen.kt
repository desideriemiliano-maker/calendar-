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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
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
                        RigaOrario(
                            inizio = inizioInput,
                            fine = fineInput,
                            onCambia = { i, f -> viewModel.aggiornaOrarioAncora(slot.id, i, f) }
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.calcola() }) { Text("Calcola orari") }
                        TextButton(onClick = { viewModel.pulisciRisultati() }) { Text("Pulisci") }
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
