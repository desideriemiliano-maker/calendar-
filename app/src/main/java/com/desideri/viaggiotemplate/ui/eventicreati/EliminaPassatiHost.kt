package com.desideri.viaggiotemplate.ui.eventicreati

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.viaggiotemplate.domain.calendar.EsecuzioneCreata
import com.desideri.viaggiotemplate.domain.calendar.RisultatoEliminazioneEventi
import com.desideri.viaggiotemplate.domain.calendar.dataViaggio
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_ELIMINA_PASSATI: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private const val MASSIMO_ELENCATE = 10

/**
 * Osserva [EliminaPassatiViewModel.stato] e mostra il dialog giusto per lo stato corrente (nessun
 * passato, conferma con elenco, progresso, esito, errore). Va incluso una volta sola nell'albero
 * della UI (accanto agli altri dialog del menu principale, vedi BackupDriveHost per lo stesso
 * pattern), non serve nessun parametro di visibilità: tutto è guidato dallo stato.
 */
@Composable
fun EliminaPassatiHost(viewModel: EliminaPassatiViewModel) {
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    when (val statoCorrente = stato) {
        StatoEliminaPassati.Inattivo -> Unit
        StatoEliminaPassati.Caricamento, StatoEliminaPassati.Eliminazione -> DialogElaborazione(
            testo = if (statoCorrente is StatoEliminaPassati.Eliminazione) "Eliminazione in corso…" else "Ricerca eventi passati…"
        )
        StatoEliminaPassati.NessunPassato -> DialogInfo(
            titolo = "Nessun evento passato",
            messaggio = "Non ci sono eventi creati con viaggio già passato da eliminare.",
            onChiudi = viewModel::annulla
        )
        is StatoEliminaPassati.Conferma -> DialogConfermaEliminaPassati(
            passate = statoCorrente.passate,
            onConferma = { eliminaAncheCalendario -> viewModel.conferma(context, statoCorrente.passate, eliminaAncheCalendario) },
            onAnnulla = viewModel::annulla
        )
        is StatoEliminaPassati.Completato -> DialogInfo(
            titolo = "Eliminazione completata",
            messaggio = if (statoCorrente.numero == 1) {
                "1 evento creato eliminato."
            } else {
                "${statoCorrente.numero} eventi creati eliminati."
            },
            onChiudi = viewModel::annulla
        )
        is StatoEliminaPassati.ErroreParziale -> DialogErroreParzialeEliminaPassati(esito = statoCorrente, onChiudi = viewModel::annulla)
        is StatoEliminaPassati.Errore -> DialogInfo(titolo = "Eliminazione non riuscita", messaggio = statoCorrente.messaggio, onChiudi = viewModel::annulla)
    }
}

@Composable
private fun DialogElaborazione(testo: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(testo)
            }
        }
    }
}

@Composable
private fun DialogInfo(titolo: String, messaggio: String, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = { Text(messaggio) },
        confirmButton = { TextButton(onClick = onChiudi) { Text("OK") } }
    )
}

/** Riga "Nome template — dd/MM/yyyy" per un'esecuzione, usata sia nella conferma sia nell'esito parziale. */
private fun EsecuzioneCreata.righeDescrittiva(): String =
    "${templateNome ?: "Senza nome template"} — ${dataViaggio().format(FORMATO_DATA_ELIMINA_PASSATI)}"

@Composable
private fun DialogConfermaEliminaPassati(
    passate: List<EsecuzioneCreata>,
    onConferma: (eliminaAncheCalendario: Boolean) -> Unit,
    onAnnulla: () -> Unit
) {
    var eliminaAncheCalendario by remember { mutableStateOf(false) }
    val altre = passate.size - MASSIMO_ELENCATE
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = {
            Text(if (passate.size == 1) "Eliminare 1 evento creato passato?" else "Eliminare ${passate.size} eventi creati passati?")
        },
        text = {
            Column {
                Text("Le registrazioni locali verranno eliminate e non potranno più essere recuperate:")
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    passate.take(MASSIMO_ELENCATE).forEach { esecuzione ->
                        Text("• ${esecuzione.righeDescrittiva()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (altre > 0) {
                        Text("… e altre $altre", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Checkbox(checked = eliminaAncheCalendario, onCheckedChange = { eliminaAncheCalendario = it })
                    Text("Elimina anche gli eventi dal calendario")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(eliminaAncheCalendario) }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

/**
 * Come [EventiCreatiScreen]'s DialogErroreEliminazioneCalendario ma per più esecuzioni in blocco:
 * elenca (troncando se sono molte) quali non hanno eliminato tutti gli eventi dal calendario, con
 * lo stesso dettaglio numerico per ciascuna (cancellati subito / fallback / mancanti).
 */
@Composable
private fun DialogErroreParzialeEliminaPassati(esito: StatoEliminaPassati.ErroreParziale, onChiudi: () -> Unit) {
    val nonCompletate = esito.nonCompletate
    val altre = nonCompletate.size - MASSIMO_ELENCATE
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Eliminazione parziale") },
        text = {
            Column {
                Text(
                    "${esito.eliminateConSuccesso} eventi creati eliminati. " +
                        "${nonCompletate.size} non sono stati eliminati perché il calendario non ha rimosso tutti i loro eventi: " +
                        "le rispettive registrazioni locali sono state mantenute, così puoi riprovare."
                )
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    nonCompletate.take(MASSIMO_ELENCATE).forEach { (esecuzione, risultato) ->
                        Text(
                            "• ${esecuzione.righeDescrittiva()}: ${risultato.dettaglio()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (altre > 0) {
                        Text("… e altre $altre", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("OK") } }
    )
}

private fun RisultatoEliminazioneEventi.dettaglio(): String {
    val fallback = if (fallbackTentato) "fallback +$cancellatiFallback" else "fallback non necessario"
    return "cancellati $cancellatiBatch/$idsRichiesti, $fallback, ${idsNonCancellati.size} ancora presenti"
}
