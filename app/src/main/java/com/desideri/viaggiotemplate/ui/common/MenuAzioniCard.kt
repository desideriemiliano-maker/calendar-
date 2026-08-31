package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Una voce del menu a tre puntini di una card: vedi [MenuAzioniCard]. */
data class AzioneMenuCard(
    val etichetta: String,
    val icona: ImageVector,
    val onClick: () -> Unit,
    /** null = colore di testo/icona di default del tema; usato per evidenziare un'azione distruttiva (es. elimina) in rosso. */
    val tint: Color? = null
)

/**
 * Icona a tre puntini che apre un menu con le azioni secondarie di una card (Tratte/Template):
 * solo sposta su/giù restano icone dirette sulla card, tutto il resto (modifica, mappa, clona,
 * elimina...) vive qui, per non affollare la riga della card man mano che le azioni disponibili
 * crescono.
 */
@Composable
fun MenuAzioniCard(azioni: List<AzioneMenuCard>) {
    var espanso by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { espanso = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Altre azioni")
        }
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            azioni.forEach { azione ->
                DropdownMenuItem(
                    text = { Text(azione.etichetta, color = azione.tint ?: Color.Unspecified) },
                    leadingIcon = { Icon(azione.icona, contentDescription = null, tint = azione.tint ?: LocalContentColor.current) },
                    onClick = {
                        espanso = false
                        azione.onClick()
                    }
                )
            }
        }
    }
}
