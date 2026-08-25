package com.desideri.viaggiotemplate.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Popup di conferma prima di un'eliminazione definitiva (Tratte/Template). */
@Composable
fun DialogConfermaEliminazione(
    nomeElemento: String,
    onConferma: () -> Unit,
    onAnnulla: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Eliminare \"$nomeElemento\"?") },
        text = { Text("L'eliminazione è definitiva e non può essere annullata.") },
        confirmButton = { TextButton(onClick = onConferma) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
