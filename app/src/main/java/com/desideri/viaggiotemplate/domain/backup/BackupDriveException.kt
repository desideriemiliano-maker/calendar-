package com.desideri.viaggiotemplate.domain.backup

/** Errori tipizzati del backup/ripristino su Google Drive, con un messaggio già pronto per la UI. */
sealed class BackupDriveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NessunaRete : BackupDriveException("Nessuna connessione di rete disponibile.")
    class AutorizzazioneNegata(cause: Throwable? = null) :
        BackupDriveException("Autorizzazione a Google Drive negata o annullata.", cause)
    class NessunBackupTrovato : BackupDriveException("Nessun backup trovato su Google Drive.")
    class ErroreRete(operazione: String, cause: Throwable? = null) :
        BackupDriveException("Errore di rete durante $operazione: ${cause?.message ?: "motivo sconosciuto"}", cause)
    class BackupNonValido(motivo: String) : BackupDriveException("Il file scaricato non è un backup valido: $motivo")
}
