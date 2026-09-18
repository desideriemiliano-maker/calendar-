package com.desideri.viaggiotemplate.domain.backup

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/** Errori tipizzati del backup/ripristino su Google Drive, con un messaggio già pronto per la UI. */
sealed class BackupDriveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NessunaRete : BackupDriveException("Nessuna connessione di rete disponibile.")
    class AutorizzazioneNegata(cause: Throwable? = null) :
        BackupDriveException(
            "Autorizzazione a Google Drive negata o annullata" +
                (cause?.let { " (${dettaglioCausa(it)})" } ?: "") + ".",
            cause
        )
    class NessunBackupTrovato : BackupDriveException("Nessun backup trovato su Google Drive.")
    class ErroreRete(operazione: String, cause: Throwable? = null) :
        BackupDriveException("Errore di rete durante $operazione: ${cause?.message ?: "motivo sconosciuto"}", cause)
    class BackupNonValido(motivo: String) : BackupDriveException("Il file scaricato non è un backup valido: $motivo")

    companion object {
        /**
         * Per un'`ApiException` di Play Services il messaggio grezzo è spesso solo il codice
         * numerico: qui lo si traduce nella costante nota (es. "DEVELOPER_ERROR", 10 — tipico di
         * un client OAuth non configurato o non ancora propagato lato Google), molto più utile
         * per capire la causa reale senza dover collegare un debugger/Logcat.
         */
        private fun dettaglioCausa(cause: Throwable): String = if (cause is ApiException) {
            "${CommonStatusCodes.getStatusCodeString(cause.statusCode)}, codice ${cause.statusCode}"
        } else {
            cause.message ?: cause::class.simpleName ?: "motivo sconosciuto"
        }
    }
}
