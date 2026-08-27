package com.desideri.viaggiotemplate.ui.esecuzione

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.data.remote.clientOrariPer
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Vettore
import com.desideri.viaggiotemplate.ui.AppContainer
import com.desideri.viaggiotemplate.ui.common.CampoData
import com.desideri.viaggiotemplate.ui.common.CampoOrario
import com.desideri.viaggiotemplate.ui.common.DialogConfermaEliminazione
import com.desideri.viaggiotemplate.ui.common.RicercaOrariTreno
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreNotifica
import com.desideri.viaggiotemplate.ui.common.descrizioneFonteOrari
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
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
                        val puoScaricareOrarioReale = trattaAncora?.tipo == TipoTratta.TRENO &&
                            trattaAncora.vettore?.let { clientOrariPer(it) != null } == true
                        if (trattaAncora != null && trattaAncora.tipo == TipoTratta.TRENO && trattaAncora.vettore == Vettore.ALTRO) {
                            SelettoreOrarioAncoraVettoreAltro(
                                tratta = trattaAncora,
                                data = stato.data,
                                vettoreScelto = stato.vettoriScelti[slot.id],
                                orarioIndicativo = stato.orariIndicativi[slot.id] ?: LocalTime.of(9, 0),
                                inizio = inizioInput,
                                fine = fineInput,
                                onSceltaVettore = { viewModel.sceglieVettorePerTrattaAltro(slot.id, it) },
                                onCambiaOrarioIndicativo = { viewModel.aggiornaOrarioIndicativo(slot.id, it) },
                                onCambia = { i, f -> viewModel.aggiornaOrarioAncora(slot.id, i, f) }
                            )
                        } else if (trattaAncora != null && (trattaAncora.orariFissi.isNotEmpty() || puoScaricareOrarioReale)) {
                            SelettoreOrarioAncora(
                                tratta = trattaAncora,
                                data = stato.data,
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

            itemsIndexed(stato.eventiCalcolati, key = { _, evento -> evento.templateSlotId }) { indice, evento ->
                if (indice > 0) {
                    RigaAttesa(
                        fine = stato.eventiCalcolati[indice - 1].fineReale,
                        inizio = evento.inizioReale
                    )
                }
                val eAncora = stato.templateSelezionato?.slots?.firstOrNull { it.id == evento.templateSlotId }?.ancora == true
                CardEvento(
                    evento = evento,
                    data = stato.data,
                    eAncora = eAncora,
                    fineEventoPrecedente = if (indice > 0) stato.eventiCalcolati[indice - 1].fineReale else null,
                    notifica = stato.notificheSelezionate[evento.templateSlotId] ?: Notifica.NESSUNA,
                    descrizione = stato.descrizioni[evento.templateSlotId] ?: "",
                    colore = stato.coloriSelezionati[evento.templateSlotId] ?: evento.tratta.colore,
                    vettoreScelto = stato.vettoriScelti[evento.templateSlotId],
                    onScegliAlternativa = { nuovaTrattaId, nuovoInizio, nuovaFine ->
                        viewModel.scegliAlternativa(evento.templateSlotId, nuovaTrattaId, nuovoInizio, nuovaFine)
                    },
                    onModificaManuale = { inizio, fine ->
                        viewModel.sovrascriviEvento(evento.templateSlotId, inizio, fine)
                    },
                    onElimina = { viewModel.eliminaEvento(evento.templateSlotId) },
                    suggerisciScelta = { corse ->
                        viewModel.calcolaSceltaConsigliata(evento.templateSlotId, evento.tratta.id, corse)
                    },
                    onConfermaOrarioReale = { corse, scelta ->
                        viewModel.confermaOrarioReale(evento.templateSlotId, evento.tratta.id, corse, scelta)
                    },
                    suggerisciSceltaPer = { trattaId, corse ->
                        viewModel.calcolaSceltaConsigliata(evento.templateSlotId, trattaId, corse)
                    },
                    onSceltaCorsaReale = { trattaId, corse, scelta ->
                        viewModel.confermaOrarioReale(evento.templateSlotId, trattaId, corse, scelta)
                    },
                    onCambiaNotifica = { viewModel.aggiornaNotifica(evento.templateSlotId, it) },
                    onCambiaDescrizione = { viewModel.aggiornaDescrizione(evento.templateSlotId, it) },
                    onCambiaColore = { viewModel.aggiornaColoreEvento(evento.templateSlotId, it) },
                    onScegliVettoreAltro = { viewModel.sceglieVettorePerTrattaAltro(evento.templateSlotId, it) }
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
                    if (stato.eventiCalcolati.any { it.orarioDaConfermare }) {
                        item {
                            Text(
                                "Una o più tratte non hanno ancora un orario reale confermato (vedi sopra): " +
                                    "verrebbero scritte a calendario con partenza e arrivo uguali.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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

/** Formatta il tempo tra [fine] (arrivo della tratta precedente) e [inizio] (partenza di questa), es. "45min", "1h", "1h 20min". */
private fun formattaAttesa(fine: LocalTime, inizio: LocalTime): String {
    var minuti = Duration.between(fine, inizio).toMinutes()
    if (minuti < 0) minuti += 24 * 60
    return when {
        minuti < 60 -> "${minuti}min"
        minuti % 60 == 0L -> "${minuti / 60}h"
        else -> "${minuti / 60}h ${minuti % 60}min"
    }
}

/** Mostra il tempo di attesa tra l'arrivo di una tratta e la partenza della successiva. */
@Composable
private fun RigaAttesa(fine: LocalTime, inizio: LocalTime) {
    val testo = "Attesa: " + formattaAttesa(fine, inizio)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        Text(testo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private enum class ModoOrarioAncora { RICORRENTE, FISSO, TEMPO_REALE }

/**
 * Per una tratta-ancora TRENO con orari fissi configurati e/o un vettore con integrazione orari
 * reali (vedi [clientOrariPer]): scelta tra l'orario ricorrente inserito a mano, un orario fisso
 * (dropdown, tra quelli definiti sulla Tratta) e gli orari reali scaricati per la data del viaggio
 * (gia' nota qui).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelettoreOrarioAncora(
    tratta: Tratta,
    data: LocalDate,
    inizio: LocalTime,
    fine: LocalTime,
    onCambia: (LocalTime, LocalTime) -> Unit
) {
    val orariFissi = tratta.orariFissi
    val vettoreConOrariReali = tratta.vettore?.takeIf { tratta.tipo == TipoTratta.TRENO && clientOrariPer(it) != null }
    var modo by remember { mutableStateOf(ModoOrarioAncora.RICORRENTE) }
    var mostraDialogOrarioReale by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { modo = ModoOrarioAncora.RICORRENTE }) {
                Text(if (modo == ModoOrarioAncora.RICORRENTE) "● Ricorrente" else "Ricorrente")
            }
            if (orariFissi.isNotEmpty()) {
                TextButton(onClick = {
                    modo = ModoOrarioAncora.FISSO
                    orariFissi.firstOrNull()?.let { onCambia(it.partenza, it.arrivo) }
                }) { Text(if (modo == ModoOrarioAncora.FISSO) "● Fisso" else "Fisso") }
            }
            if (vettoreConOrariReali != null) {
                TextButton(onClick = { modo = ModoOrarioAncora.TEMPO_REALE; mostraDialogOrarioReale = true }) {
                    Text(if (modo == ModoOrarioAncora.TEMPO_REALE) "● ${vettoreConOrariReali.name}" else vettoreConOrariReali.name)
                }
            }
        }
        when (modo) {
            ModoOrarioAncora.RICORRENTE -> RigaOrario(inizio = inizio, fine = fine, onCambia = onCambia)
            ModoOrarioAncora.FISSO -> {
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
            }
            ModoOrarioAncora.TEMPO_REALE -> {
                Text("${inizio.toStringHHmm()} → ${fine.toStringHHmm()}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { mostraDialogOrarioReale = true }) {
                    Text("Cerca su ${vettoreConOrariReali?.name} per il ${data.format(FORMATO_DATA)}")
                }
            }
        }
    }

    if (mostraDialogOrarioReale && vettoreConOrariReali != null) {
        DialogScaricaOrarioTrenoSingolo(
            vettore = vettoreConOrariReali,
            daStazione = tratta.luogoPartenza,
            aStazione = tratta.luogoArrivo,
            data = data,
            oraRiferimento = inizio.minusHours(2),
            onScelto = { corsa -> onCambia(corsa.partenza, corsa.arrivo); mostraDialogOrarioReale = false },
            onDismiss = { mostraDialogOrarioReale = false }
        )
    }
}

/**
 * Riga di scelta del vettore reale (Trenitalia/Italo/SBB) per una Tratta TRENO con vettore ALTRO:
 * la Tratta di libreria resta generica, ma per una singola esecuzione l'utente puo' precisare
 * quale operatore sta effettivamente prendendo, sbloccando (per Trenitalia/SBB) la ricerca in
 * tempo reale — vedi [SelettoreOrarioAncoraVettoreAltro] e l'uso analogo in [CardEvento].
 */
@Composable
private fun SelettoreVettorePerAltro(vettoreScelto: Vettore?, onScegli: (Vettore) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Che vettore è questo treno?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Vettore.TRENITALIA, Vettore.ITALO, Vettore.SBB).forEach { v ->
                TextButton(onClick = { onScegli(v) }) {
                    Text(if (vettoreScelto == v) "● ${v.name}" else v.name)
                }
            }
        }
    }
}

/**
 * Variante di [SelettoreOrarioAncora] per una tratta-ancora TRENO con vettore ALTRO: prima si
 * sceglie il vettore reale per questa esecuzione (vedi [SelettoreVettorePerAltro]). Se risolve a
 * un'integrazione con orari reali (Trenitalia/SBB), si richiede solo un orario indicativo — non
 * la coppia partenza/arrivo — e la ricerca parte da 2h prima di quell'orario; l'orario reale del
 * treno scelto dall'utente diventa poi l'orario dell'ancora. Se Italo (o nessuna scelta ancora
 * fatta), resta il meccanismo ricorrente/fisso di sempre, delegato a [SelettoreOrarioAncora].
 */
@Composable
private fun SelettoreOrarioAncoraVettoreAltro(
    tratta: Tratta,
    data: LocalDate,
    vettoreScelto: Vettore?,
    orarioIndicativo: LocalTime,
    inizio: LocalTime,
    fine: LocalTime,
    onSceltaVettore: (Vettore) -> Unit,
    onCambiaOrarioIndicativo: (LocalTime) -> Unit,
    onCambia: (LocalTime, LocalTime) -> Unit
) {
    var mostraDialogOrarioReale by remember { mutableStateOf(false) }
    val vettoreConRicerca = vettoreScelto?.takeIf { clientOrariPer(it) != null }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelettoreVettorePerAltro(vettoreScelto = vettoreScelto, onScegli = onSceltaVettore)

        if (vettoreConRicerca != null) {
            CampoOrario(
                etichetta = "Orario indicativo (HH:mm)",
                valore = orarioIndicativo,
                onValoreCambiato = onCambiaOrarioIndicativo,
                modifier = Modifier.fillMaxWidth()
            )
            Text("${inizio.toStringHHmm()} → ${fine.toStringHHmm()}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { mostraDialogOrarioReale = true }) {
                Text("Cerca su ${vettoreConRicerca.name} per il ${data.format(FORMATO_DATA)}")
            }
            if (mostraDialogOrarioReale) {
                DialogScaricaOrarioTrenoSingolo(
                    vettore = vettoreConRicerca,
                    daStazione = tratta.luogoPartenza,
                    aStazione = tratta.luogoArrivo,
                    data = data,
                    oraRiferimento = orarioIndicativo.minusHours(2),
                    onScelto = { corsa -> onCambia(corsa.partenza, corsa.arrivo); mostraDialogOrarioReale = false },
                    onDismiss = { mostraDialogOrarioReale = false }
                )
            }
        } else {
            SelettoreOrarioAncora(tratta = tratta, data = data, inizio = inizio, fine = fine, onCambia = onCambia)
        }
    }
}

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Cerca gli orari reali per una data gia' nota (quella del viaggio) e ne fa scegliere uno. */
@Composable
private fun DialogScaricaOrarioTrenoSingolo(
    vettore: Vettore,
    daStazione: String,
    aStazione: String,
    data: LocalDate,
    oraRiferimento: LocalTime,
    onScelto: (CorsaScaricata) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 520.dp)) {
                Text("Orari ${vettore.name} per il ${data.format(FORMATO_DATA)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$daStazione → $aStazione, ${descrizioneFonteOrari(vettore)}.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                RicercaOrariTreno(
                    vettore = vettore,
                    daStazione = daStazione,
                    aStazione = aStazione,
                    data = data,
                    oraRiferimento = oraRiferimento,
                    limite = 16,
                    chiaveRicerca = Unit,
                    credenzialiItalo = AppContainer.italoCredentialsStore.credenziali,
                    testoAzione = { "" },
                    azioneAbilitata = { true },
                    onAzione = { corsa -> onScelto(corsa) }
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
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

@Composable
private fun CardEvento(
    evento: EventoCalcolato,
    data: LocalDate,
    eAncora: Boolean,
    fineEventoPrecedente: LocalTime?,
    notifica: Notifica,
    descrizione: String,
    colore: Int?,
    vettoreScelto: Vettore?,
    onScegliAlternativa: (String, LocalTime, LocalTime) -> Unit,
    onModificaManuale: (LocalTime, LocalTime) -> Unit,
    onElimina: () -> Unit,
    suggerisciScelta: (List<CorsaScaricata>) -> CorsaScaricata?,
    onConfermaOrarioReale: (corse: List<CorsaScaricata>, scelta: CorsaScaricata) -> Unit,
    suggerisciSceltaPer: (trattaId: String, corse: List<CorsaScaricata>) -> CorsaScaricata?,
    onSceltaCorsaReale: (trattaId: String, corse: List<CorsaScaricata>, scelta: CorsaScaricata) -> Unit,
    onCambiaNotifica: (Notifica) -> Unit,
    onCambiaDescrizione: (String) -> Unit,
    onCambiaColore: (Int?) -> Unit,
    onScegliVettoreAltro: (Vettore) -> Unit
) {
    var modificaManuale by remember { mutableStateOf(false) }
    var confermaEliminazione by remember { mutableStateOf(false) }
    var mostraConfrontoAlternative by remember { mutableStateOf(false) }
    var caricamentoOrarioReale by remember { mutableStateOf(false) }
    var erroreOrarioReale by remember { mutableStateOf<String?>(null) }
    var corseDaConfermare by remember { mutableStateOf<List<CorsaScaricata>?>(null) }
    // Per la tratta ancora l'orario non passa dalla scelta tra candidati (è l'input diretto del
    // calcolo, gestito dal selettore Ricorrente/Fisso/tempo reale sopra): qui il pulsante non
    // avrebbe alcun effetto, quindi va mostrato solo per le tratte calcolate, non per l'ancora.
    // Se il vettore della Tratta e' ALTRO, si usa il vettore scelto dall'utente per questa
    // esecuzione (vedi SelettoreVettorePerAltro) al posto di quello (assente) della Tratta.
    val vettoreTratta = evento.tratta.vettore
    val vettoreEffettivo = if (vettoreTratta == Vettore.ALTRO) vettoreScelto else vettoreTratta
    val vettoreConOrariReali = vettoreEffettivo?.takeIf {
        !eAncora && evento.tratta.tipo == TipoTratta.TRENO && clientOrariPer(it) != null
    }
    val credenzialiItalo = AppContainer.italoCredentialsStore.credenziali
    val client = remember(vettoreConOrariReali, credenzialiItalo) {
        vettoreConOrariReali?.let { clientOrariPer(it, credenzialiItalo) }
    }
    val scope = rememberCoroutineScope()

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
            if (evento.orarioDaConfermare) {
                Text(
                    "Orario non ancora noto: questa tratta non ha nessun orario configurato. " +
                        "Aggiorna con l'orario reale qui sotto prima di aggiungere al calendario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            SelettoreNotifica(valore = notifica, onCambia = onCambiaNotifica)

            OutlinedTextField(
                value = descrizione,
                onValueChange = onCambiaDescrizione,
                label = { Text("Descrizione (opzionale)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SelettoreColore(coloreSelezionato = colore, onCambia = onCambiaColore)

            if (evento.alternative.isNotEmpty()) {
                TextButton(onClick = { mostraConfrontoAlternative = true }) {
                    Text("Confronta alternative (${evento.alternative.size})")
                }
                if (mostraConfrontoAlternative) {
                    DialogConfrontoAlternative(
                        evento = evento,
                        fineEventoPrecedente = fineEventoPrecedente,
                        vettoreScelto = vettoreScelto,
                        data = data,
                        suggerisciScelta = suggerisciSceltaPer,
                        onSceltaCorsaReale = { trattaId, corse, scelta ->
                            onSceltaCorsaReale(trattaId, corse, scelta)
                            mostraConfrontoAlternative = false
                        },
                        onSceltaSemplice = { trattaId, inizio, fine ->
                            onScegliAlternativa(trattaId, inizio, fine)
                            mostraConfrontoAlternative = false
                        },
                        onDismiss = { mostraConfrontoAlternative = false }
                    )
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
            if (!eAncora && evento.tratta.tipo == TipoTratta.TRENO && vettoreTratta == Vettore.ALTRO) {
                SelettoreVettorePerAltro(vettoreScelto = vettoreScelto, onScegli = onScegliVettoreAltro)
            }
            if (vettoreConOrariReali != null && client != null) {
                TextButton(
                    onClick = {
                        erroreOrarioReale = null
                        caricamentoOrarioReale = true
                        scope.launch {
                            try {
                                val corse = withContext(Dispatchers.IO) {
                                    client.cercaCorse(
                                        daStazione = evento.tratta.luogoPartenza,
                                        aStazione = evento.tratta.luogoArrivo,
                                        data = data,
                                        oraRiferimento = evento.inizioReale.minusHours(3),
                                        limite = 16
                                    )
                                }
                                if (corse.isEmpty()) {
                                    erroreOrarioReale = "Nessuna corsa trovata per questa data."
                                } else {
                                    corseDaConfermare = corse
                                }
                            } catch (e: Exception) {
                                erroreOrarioReale = "Impossibile scaricare gli orari: ${e.message ?: "errore di rete"}"
                            } finally {
                                caricamentoOrarioReale = false
                            }
                        }
                    },
                    enabled = !caricamentoOrarioReale
                ) {
                    Text(
                        if (caricamentoOrarioReale) "Ricerca su ${vettoreConOrariReali.name}…"
                        else "Aggiorna con orario reale ${vettoreConOrariReali.name} per il ${data.format(FORMATO_DATA)}"
                    )
                }
                erroreOrarioReale?.let { msg -> Text(msg, style = MaterialTheme.typography.bodySmall) }
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

    corseDaConfermare?.let { corse ->
        val consigliata = remember(corse) { suggerisciScelta(corse) }
        DialogConfermaOrarioReale(
            vettoreNome = vettoreConOrariReali?.name ?: "",
            corse = corse,
            consigliata = consigliata,
            onConferma = { scelta ->
                onConfermaOrarioReale(corse, scelta)
                corseDaConfermare = null
            },
            onDismiss = { corseDaConfermare = null }
        )
    }
}

/**
 * Dialog di confronto tra la tratta scelta per un evento e le sue alternative (vedi [CardEvento]),
 * trattando la scelta attuale come una riga in più invece che come testo statico: anche lei, se
 * il suo vettore ha un'integrazione orari reali (SBB/Trenitalia/Italo, vedi [clientOrariPer]),
 * mostra la ricerca live delle corse vere — non solo l'orario già calcolato dal motore, che puo'
 * essere un placeholder mai confermato con l'integrazione (vedi `EventoCalcolato.orarioDaConfermare`).
 * Ogni riga con integrazione evidenzia la corsa che il motore sceglierebbe automaticamente (vedi
 * [suggerisciScelta]/[EsecuzioneViewModel.calcolaSceltaConsigliata]); le righe senza integrazione
 * (bus, vettore ALTRO senza scelta, nessuna integrazione) mostrano l'orario già calcolato dal
 * motore, cliccabile solo per le alternative (la riga attuale non avrebbe alcun effetto).
 */
@Composable
private fun DialogConfrontoAlternative(
    evento: EventoCalcolato,
    fineEventoPrecedente: LocalTime?,
    vettoreScelto: Vettore?,
    data: LocalDate,
    suggerisciScelta: (trattaId: String, corse: List<CorsaScaricata>) -> CorsaScaricata?,
    onSceltaCorsaReale: (trattaId: String, corse: List<CorsaScaricata>, scelta: CorsaScaricata) -> Unit,
    onSceltaSemplice: (trattaId: String, inizio: LocalTime, fine: LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Confronta alternative", style = MaterialTheme.typography.titleMedium)

                RigaOpzioneConfronto(
                    opzione = evento,
                    etichetta = "Attuale: ${evento.tratta.nome}",
                    eCorrente = true,
                    fineEventoPrecedente = fineEventoPrecedente,
                    vettoreScelto = vettoreScelto,
                    data = data,
                    suggerisciScelta = suggerisciScelta,
                    onSceltaCorsaReale = onSceltaCorsaReale,
                    onSceltaSemplice = onSceltaSemplice
                )

                evento.alternative.forEach { alt ->
                    RigaOpzioneConfronto(
                        opzione = alt,
                        etichetta = alt.tratta.nome,
                        eCorrente = false,
                        fineEventoPrecedente = fineEventoPrecedente,
                        vettoreScelto = vettoreScelto,
                        data = data,
                        suggerisciScelta = suggerisciScelta,
                        onSceltaCorsaReale = onSceltaCorsaReale,
                        onSceltaSemplice = onSceltaSemplice
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

/**
 * Una riga del dialog di confronto (vedi [DialogConfrontoAlternative]): se [opzione] ha un
 * vettore con integrazione orari reali cerca le corse vere ed evidenzia quella consigliata dal
 * motore (["Consigliata"], via [suggerisciScelta]); altrimenti mostra il solo orario già
 * calcolato, cliccabile per scegliere quella tratta se non e' [eCorrente] (sceglierla di nuovo
 * non avrebbe alcun effetto).
 */
@Composable
private fun RigaOpzioneConfronto(
    opzione: EventoCalcolato,
    etichetta: String,
    eCorrente: Boolean,
    fineEventoPrecedente: LocalTime?,
    vettoreScelto: Vettore?,
    data: LocalDate,
    suggerisciScelta: (trattaId: String, corse: List<CorsaScaricata>) -> CorsaScaricata?,
    onSceltaCorsaReale: (trattaId: String, corse: List<CorsaScaricata>, scelta: CorsaScaricata) -> Unit,
    onSceltaSemplice: (trattaId: String, inizio: LocalTime, fine: LocalTime) -> Unit
) {
    val vettoreTratta = opzione.tratta.vettore
    val vettoreEffettivo = if (vettoreTratta == Vettore.ALTRO) vettoreScelto else vettoreTratta
    val credenzialiItalo = AppContainer.italoCredentialsStore.credenziali
    val client = vettoreEffettivo?.takeIf { opzione.tratta.tipo == TipoTratta.TRENO }?.let { clientOrariPer(it, credenzialiItalo) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etichetta, style = MaterialTheme.typography.labelMedium)
        if (client != null && vettoreEffettivo != null) {
            var corseCaricate by remember(opzione.tratta.id) { mutableStateOf<List<CorsaScaricata>>(emptyList()) }
            val consigliata = remember(opzione.tratta.id, corseCaricate) { suggerisciScelta(opzione.tratta.id, corseCaricate) }
            Text(descrizioneFonteOrari(vettoreEffettivo), style = MaterialTheme.typography.bodySmall)
            RicercaOrariTreno(
                vettore = vettoreEffettivo,
                daStazione = opzione.tratta.luogoPartenza,
                aStazione = opzione.tratta.luogoArrivo,
                data = data,
                oraRiferimento = opzione.inizioReale.minusHours(2),
                limite = 16,
                chiaveRicerca = opzione.tratta.id,
                credenzialiItalo = credenzialiItalo,
                onRisultati = { corseCaricate = it },
                testoAzione = { corsa -> if (corsa == consigliata) "Consigliata" else "" },
                sottotesto = fineEventoPrecedente?.let { fine -> { corsa: CorsaScaricata -> "Attesa: ${formattaAttesa(fine, corsa.partenza)}" } },
                azioneAbilitata = { true },
                onAzione = { corsa -> onSceltaCorsaReale(opzione.tratta.id, corseCaricate, corsa) },
                modifier = Modifier.heightIn(max = 220.dp)
            )
        } else if (eCorrente) {
            Text("${opzione.inizioReale.toStringHHmm()} → ${opzione.fineReale.toStringHHmm()}", style = MaterialTheme.typography.bodyMedium)
            fineEventoPrecedente?.let { fine ->
                Text(
                    "Attesa: ${formattaAttesa(fine, opzione.inizioReale)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSceltaSemplice(opzione.tratta.id, opzione.inizioReale, opzione.fineReale) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "${opzione.inizioReale.toStringHHmm()} → ${opzione.fineReale.toStringHHmm()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                fineEventoPrecedente?.let { fine ->
                    Text(
                        "Attesa: ${formattaAttesa(fine, opzione.inizioReale)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Pannello di conferma mostrato dopo aver scaricato gli orari reali di una tratta (vedi
 * [CardEvento]): elenca le corse trovate evidenziando quella che il motore userebbe
 * automaticamente (vedi [EsecuzioneViewModel.calcolaSceltaConsigliata]), ma l'utente puo'
 * confermarla o scegliere una qualunque altra corsa dell'elenco.
 */
@Composable
private fun DialogConfermaOrarioReale(
    vettoreNome: String,
    corse: List<CorsaScaricata>,
    consigliata: CorsaScaricata?,
    onConferma: (CorsaScaricata) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 520.dp)) {
                Text("Conferma orario reale $vettoreNome", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (consigliata != null)
                        "Verrà usata la corsa evidenziata. Puoi scegliere un'altra corsa dall'elenco."
                    else
                        "Scegli quale corsa usare per questo orario.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(corse) { corsa ->
                        val eConsigliata = corsa == consigliata
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (eConsigliata) {
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${corsa.partenza.toStringHHmm()} → ${corsa.arrivo.toStringHHmm()}" +
                                            if (corsa.etichetta.isNotBlank()) " (${corsa.etichetta})" else "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (eConsigliata) {
                                        Text(
                                            "Verrà usata questa",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                TextButton(onClick = { onConferma(corsa) }) {
                                    Text(if (eConsigliata) "Conferma" else "Usa")
                                }
                            }
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
