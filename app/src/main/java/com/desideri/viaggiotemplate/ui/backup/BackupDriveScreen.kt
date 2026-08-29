package com.desideri.viaggiotemplate.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.viaggiotemplate.data.remote.drive.FileDrive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_ORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

/**
 * Osserva [BackupDriveViewModel.stato] e mostra il dialog giusto per lo stato corrente (attesa,
 * conferma ripristino, esito, errore), oltre a lanciare la richiesta di consenso Google quando
 * necessaria — l'unico passo che deve avvenire da un `ActivityResultLauncher` e non dal
 * ViewModel. Va incluso una volta sola nell'albero della UI (es. accanto agli altri dialog del
 * menu principale), non serve nessun parametro di visibilità: tutto è guidato dallo stato.
 */
@Composable
fun BackupDriveHost(viewModel: BackupDriveViewModel) {
    val stato by viewModel.stato.collectAsState()
    val activity = LocalContext.current as? Activity

    val launcherConsenso = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { risultato ->
        activity?.let { viewModel.onRisultatoConsenso(it, risultato.resultCode == Activity.RESULT_OK, risultato.data) }
    }

    LaunchedEffect(stato) {
        val richiesta = stato as? StatoBackupDrive.RichiediConsenso ?: return@LaunchedEffect
        launcherConsenso.launch(richiesta.richiesta)
    }

    when (val statoCorrente = stato) {
        StatoBackupDrive.Elaborazione -> DialogElaborazioneBackupDrive()
        is StatoBackupDrive.ConfermaRipristino -> DialogConfermaRipristino(
            backup = statoCorrente.backup,
            onConferma = viewModel::confermaRipristino,
            onAnnulla = viewModel::annulla
        )
        StatoBackupDrive.BackupCompletato -> DialogInfoBackupDrive(
            titolo = "Backup completato",
            messaggio = "Il database è stato caricato nell'area privata dell'app su Google Drive.",
            onChiudi = viewModel::annulla
        )
        StatoBackupDrive.NessunBackupTrovato -> DialogInfoBackupDrive(
            titolo = "Nessun backup trovato",
            messaggio = "Non è stato trovato nessun backup su Google Drive per l'account scelto. Fai prima un backup da questo o da un altro dispositivo.",
            onChiudi = viewModel::annulla
        )
        is StatoBackupDrive.Errore -> DialogInfoBackupDrive(
            titolo = "Operazione non riuscita",
            messaggio = statoCorrente.messaggio,
            onChiudi = viewModel::annulla
        )
        StatoBackupDrive.Inattivo, is StatoBackupDrive.RichiediConsenso -> Unit
    }
}

@Composable
private fun DialogElaborazioneBackupDrive() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Comunicazione con Google Drive in corso…")
            }
        }
    }
}

@Composable
private fun DialogConfermaRipristino(backup: FileDrive, onConferma: () -> Unit, onAnnulla: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Ripristinare questo backup?") },
        text = {
            Column {
                Text("Backup trovato su Google Drive:")
                Text(
                    "Data: ${formattaDataBackup(backup.modifiedTimeIso)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("Dimensione: ${formattaDimensione(backup.dimensioneByte)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Tutti i dati attualmente sul dispositivo (tratte, template, eventi creati) verranno " +
                        "sostituiti con quelli del backup. L'operazione non è reversibile e l'app verrà riavviata.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onConferma) { Text("Ripristina") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

@Composable
private fun DialogInfoBackupDrive(titolo: String, messaggio: String, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = { Text(messaggio) },
        confirmButton = { TextButton(onClick = onChiudi) { Text("OK") } }
    )
}

private fun formattaDataBackup(modifiedTimeIso: String): String = try {
    FORMATO_DATA_ORA.format(Instant.parse(modifiedTimeIso))
} catch (_: Exception) {
    modifiedTimeIso
}

private fun formattaDimensione(bytes: Long?): String {
    if (bytes == null) return "sconosciuta"
    val kib = bytes / 1024.0
    return if (kib < 1024) "%.1f KB".format(kib) else "%.1f MB".format(kib / 1024.0)
}
