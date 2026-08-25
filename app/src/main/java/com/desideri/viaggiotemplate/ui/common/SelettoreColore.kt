package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Palette fissa di colori tra cui scegliere per le card di Tratte e Template. */
val PALETTE_COLORI_CARD: List<Color> = listOf(
    Color(0xFFE57373), // rosso
    Color(0xFFFFB74D), // arancione
    Color(0xFFFFF59D), // giallo
    Color(0xFF81C784), // verde
    Color(0xFF4FC3F7), // azzurro
    Color(0xFF7986CB), // blu
    Color(0xFFBA68C8), // viola
    Color(0xFFA1887F)  // marrone
)

/**
 * Selettore a pallini colorati per la card di una Tratta/Template. Il primo pallino
 * ("default") corrisponde a colore = null, cioè il grigio di default del tema — è il
 * comportamento che avevano tutte le card prima di questa funzionalità.
 */
@Composable
fun SelettoreColore(
    coloreSelezionato: Int?,
    onCambia: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Colore card", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SwatchColore(colore = null, selezionato = coloreSelezionato == null, onClick = { onCambia(null) })
            PALETTE_COLORI_CARD.forEach { colore ->
                SwatchColore(
                    colore = colore,
                    selezionato = coloreSelezionato == colore.toArgb(),
                    onClick = { onCambia(colore.toArgb()) }
                )
            }
        }
    }
}

@Composable
private fun SwatchColore(colore: Color?, selezionato: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colore ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selezionato) 2.dp else 1.dp,
                color = if (selezionato) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selezionato) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selezionato",
                tint = if (colore != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
