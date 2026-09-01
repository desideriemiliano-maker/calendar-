package com.desideri.viaggiotemplate.ui.backup

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.data.remote.drive.DriveAppDataClient
import com.desideri.viaggiotemplate.data.remote.drive.FileDrive
import com.desideri.viaggiotemplate.domain.backup.BackupDriveException
import com.desideri.viaggiotemplate.domain.backup.DatabaseBackupManager
import com.desideri.viaggiotemplate.domain.backup.GoogleDriveAutorizzatore
import com.desideri.viaggiotemplate.domain.backup.NOME_FILE_BACKUP_DRIVE
import com.desideri.viaggiotemplate.domain.backup.RisultatoAutorizzazioneDrive
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AzioneRichiesta { BACKUP, RIPRISTINO }

sealed interface StatoBackupDrive {
    data object Inattivo : StatoBackupDrive
    /** Autorizzazione o chiamata di rete in corso: nessuna azione utile da mostrare oltre a un'attesa. */
    data object Elaborazione : StatoBackupDrive
    /** Va lanciata con un `ActivityResultLauncher<IntentSenderRequest>`; il risultato torna a [BackupDriveViewModel.onRisultatoConsenso]. */
    data class RichiediConsenso(val richiesta: androidx.activity.result.IntentSenderRequest) : StatoBackupDrive
    /** Backup trovato su Drive per una richiesta di ripristino: va mostrata data/dimensione e chiesta conferma esplicita prima di sovrascrivere i dati locali. */
    data class ConfermaRipristino(val backup: FileDrive) : StatoBackupDrive
    /** Dimensione (byte) e istante del file .db appena caricato, per mostrarli nel dialog di conferma. */
    data class BackupCompletato(val dimensioneByte: Long, val dataOra: java.time.Instant = java.time.Instant.now()) : StatoBackupDrive
    data object NessunBackupTrovato : StatoBackupDrive
    data class Errore(val messaggio: String) : StatoBackupDrive
}

/**
 * Orchestra, dal lato UI, il flusso di autorizzazione Google (scope `drive.appdata`) e le due
 * azioni possibili — backup (carica il .db locale su Drive) e ripristino (scarica, valida e
 * sostituisce il .db locale) — esponendo un solo [StatoBackupDrive] da cui la UI decide quale
 * dialog mostrare. La UI resta responsabile di lanciare l'eventuale richiesta di consenso
 * (system UI di Play Services, non lanciabile da qui) e di richiamare [onRisultatoConsenso] col
 * suo esito.
 */
class BackupDriveViewModel(
    private val databaseBackupManager: DatabaseBackupManager,
    private val driveClient: DriveAppDataClient = DriveAppDataClient()
) : ViewModel() {

    private val _stato = MutableStateFlow<StatoBackupDrive>(StatoBackupDrive.Inattivo)
    val stato: StateFlow<StatoBackupDrive> = _stato.asStateFlow()

    private var azioneInCorso: AzioneRichiesta? = null
    private var backupPerRipristino: FileDrive? = null
    /** Token ottenuto quando è stato trovato [backupPerRipristino]: riusato da [confermaRipristino] per il download, senza richiedere di nuovo il consenso. */
    private var accessTokenPerRipristino: String? = null

    fun avviaBackup(activity: Activity) = avviaFlusso(activity, AzioneRichiesta.BACKUP)

    fun avviaRipristino(activity: Activity) = avviaFlusso(activity, AzioneRichiesta.RIPRISTINO)

    /** Torna a stato [StatoBackupDrive.Inattivo]: chiude qualunque dialog di esito/errore/conferma sia in mostra. */
    fun annulla() {
        azioneInCorso = null
        backupPerRipristino = null
        accessTokenPerRipristino = null
        _stato.value = StatoBackupDrive.Inattivo
    }

    /** Chiamata dopo la conferma esplicita dell'utente nel dialog che mostra data/dimensione del backup trovato. */
    fun confermaRipristino() {
        val backup = backupPerRipristino ?: return
        val accessToken = accessTokenPerRipristino ?: return
        _stato.value = StatoBackupDrive.Elaborazione
        // Loggato PRIMA del download: in caso di successo il processo viene riavviato subito dopo
        // (vedi sotto), quindi un log "completato" dopo validaEApplicaBackup non verrebbe mai scritto.
        AttivitaLogger.azioneUtente("Ripristino da Drive avviato (backup del ${backup.modifiedTimeIso})")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = AttivitaLogger.misura("Google Drive: download backup") {
                        driveClient.scarica(accessToken, backup.id)
                    }
                    // In caso di successo l'app viene riavviata da qui dentro (vedi
                    // DatabaseBackupManager.riavviaApp): questa coroutine non riprende mai oltre questa riga.
                    databaseBackupManager.validaEApplicaBackup(bytes)
                }
            } catch (e: BackupDriveException) {
                AttivitaLogger.errore("Ripristino da Drive fallito", e.message)
                _stato.value = StatoBackupDrive.Errore(e.message ?: "Errore durante il ripristino.")
            }
        }
    }

    private fun avviaFlusso(activity: Activity, azione: AzioneRichiesta) {
        if (!databaseBackupManager.reteDisponibile()) {
            _stato.value = StatoBackupDrive.Errore(BackupDriveException.NessunaRete().message!!)
            return
        }
        azioneInCorso = azione
        _stato.value = StatoBackupDrive.Elaborazione
        viewModelScope.launch {
            try {
                when (val risultato = GoogleDriveAutorizzatore.richiedi(activity)) {
                    is RisultatoAutorizzazioneDrive.Autorizzato -> prosegui(risultato.accessToken)
                    is RisultatoAutorizzazioneDrive.RichiedeConsenso ->
                        _stato.value = StatoBackupDrive.RichiediConsenso(risultato.richiesta)
                }
            } catch (e: BackupDriveException) {
                _stato.value = StatoBackupDrive.Errore(e.message ?: "Autorizzazione a Google Drive negata.")
            }
        }
    }

    /** Da chiamare con il risultato del launcher lanciato per lo stato [StatoBackupDrive.RichiediConsenso]. */
    fun onRisultatoConsenso(activity: Activity, esitoOk: Boolean, dati: Intent?) {
        _stato.value = StatoBackupDrive.Elaborazione
        viewModelScope.launch {
            try {
                val token = GoogleDriveAutorizzatore.completaConsenso(activity, esitoOk, dati)
                prosegui(token)
            } catch (e: BackupDriveException) {
                _stato.value = StatoBackupDrive.Errore(e.message ?: "Autorizzazione a Google Drive negata.")
            }
        }
    }

    private suspend fun prosegui(accessToken: String) {
        when (azioneInCorso) {
            AzioneRichiesta.BACKUP -> eseguiBackup(accessToken)
            AzioneRichiesta.RIPRISTINO -> cercaBackupPerRipristino(accessToken)
            null -> Unit
        }
    }

    private suspend fun eseguiBackup(accessToken: String) {
        try {
            val dimensioneByte = withContext(Dispatchers.IO) {
                val bytes = databaseBackupManager.leggiBytesDatabasePerBackup()
                val esistente = AttivitaLogger.misura("Google Drive: ricerca backup esistente") {
                    driveClient.trovaBackup(accessToken, NOME_FILE_BACKUP_DRIVE)
                }
                AttivitaLogger.misura("Google Drive: caricamento backup (${bytes.size} byte)") {
                    driveClient.carica(accessToken, esistente?.id, NOME_FILE_BACKUP_DRIVE, bytes)
                }
                bytes.size.toLong()
            }
            AttivitaLogger.azioneUtente("Backup su Drive completato ($dimensioneByte byte)")
            _stato.value = StatoBackupDrive.BackupCompletato(dimensioneByte)
        } catch (e: BackupDriveException) {
            AttivitaLogger.errore("Backup su Drive fallito", e.message)
            _stato.value = StatoBackupDrive.Errore(e.message ?: "Errore durante il backup.")
        }
    }

    private suspend fun cercaBackupPerRipristino(accessToken: String) {
        try {
            val backup = withContext(Dispatchers.IO) {
                AttivitaLogger.misura("Google Drive: ricerca backup per ripristino") {
                    driveClient.trovaBackup(accessToken, NOME_FILE_BACKUP_DRIVE)
                }
            }
            if (backup == null) {
                _stato.value = StatoBackupDrive.NessunBackupTrovato
            } else {
                backupPerRipristino = backup
                accessTokenPerRipristino = accessToken
                _stato.value = StatoBackupDrive.ConfermaRipristino(backup)
            }
        } catch (e: BackupDriveException) {
            AttivitaLogger.errore("Ricerca backup su Drive fallita", e.message)
            _stato.value = StatoBackupDrive.Errore(e.message ?: "Errore durante la ricerca del backup.")
        }
    }
}

object BackupDriveViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupDriveViewModel(AppContainer.databaseBackupManager) as T
    }
}
