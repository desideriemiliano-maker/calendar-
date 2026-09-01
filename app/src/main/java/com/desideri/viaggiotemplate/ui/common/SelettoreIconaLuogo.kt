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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.domain.model.IconaLuogo

/**
 * ImageVector Compose corrispondente a [IconaLuogo], per la lista Luoghi e il selettore
 * sottostante. La versione rasterizzata (per il marker sulla mappa, disegnato su un Canvas nativo
 * osmdroid) vive separatamente in `res/drawable/ic_luogo_*.xml` — vedi `TemplateMappaScreen.kt`.
 */
fun IconaLuogo.imageVector(): ImageVector = when (this) {
    IconaLuogo.UFFICIO -> Icons.Filled.Business
    IconaLuogo.CASA -> Icons.Filled.Home
    IconaLuogo.STAZIONE -> Icons.Filled.Train
    IconaLuogo.AEROPORTO -> Icons.Filled.Flight
    IconaLuogo.EDIFICIO -> Icons.Filled.Apartment
    IconaLuogo.PARCHEGGIO -> Icons.Filled.LocalParking
    IconaLuogo.SPORT -> Icons.Filled.FitnessCenter
}

/**
 * Selettore a pallini per l'icona di un Luogo, stessa forma di [SelettoreColore]: riga
 * orizzontale scorrevole di swatch circolari, il primo dei quali ("nessuna icona") corrisponde a
 * icona = null.
 */
@Composable
fun SelettoreIconaLuogo(
    iconaSelezionata: IconaLuogo?,
    onCambia: (IconaLuogo?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Icona", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SwatchIcona(icona = null, selezionato = iconaSelezionata == null, onClick = { onCambia(null) })
            IconaLuogo.values().forEach { icona ->
                SwatchIcona(icona = icona, selezionato = iconaSelezionata == icona, onClick = { onCambia(icona) })
            }
        }
    }
}

@Composable
private fun SwatchIcona(icona: IconaLuogo?, selezionato: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selezionato) 2.dp else 1.dp,
                color = if (selezionato) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icona != null) {
            Icon(icona.imageVector(), contentDescription = icona.etichetta, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Nessuna icona",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
