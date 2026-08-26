package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.desideri.viaggiotemplate.domain.model.Notifica

/** Selettore del promemoria calendario (Nessuna / 15 min / 30 min / 1 ora). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelettoreNotifica(
    valore: Notifica,
    modifier: Modifier = Modifier,
    etichetta: String = "Promemoria",
    onCambia: (Notifica) -> Unit
) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }, modifier = modifier) {
        OutlinedTextField(
            value = valore.etichetta, onValueChange = {}, readOnly = true,
            label = { Text(etichetta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            Notifica.values().forEach { opzione ->
                DropdownMenuItem(text = { Text(opzione.etichetta) }, onClick = { onCambia(opzione); espanso = false })
            }
        }
    }
}

/**
 * Selettore del promemoria per uno slot di template: in più rispetto a [SelettoreNotifica] ha
 * l'opzione "Eredita dalla tratta" (valore `null`), che mostra tra parentesi il promemoria
 * effettivo ereditato dalla tratta selezionata in quello slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelettoreNotificaConEreditarieta(
    valore: Notifica?,
    notificaEreditata: Notifica,
    modifier: Modifier = Modifier,
    onCambia: (Notifica?) -> Unit
) {
    var espanso by remember { mutableStateOf(false) }
    val etichettaEredita = "Eredita dalla tratta (${notificaEreditata.etichetta})"
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }, modifier = modifier) {
        OutlinedTextField(
            value = valore?.etichetta ?: etichettaEredita, onValueChange = {}, readOnly = true,
            label = { Text("Promemoria per questa tratta") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        DropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            DropdownMenuItem(text = { Text(etichettaEredita) }, onClick = { onCambia(null); espanso = false })
            Notifica.values().forEach { opzione ->
                DropdownMenuItem(text = { Text(opzione.etichetta) }, onClick = { onCambia(opzione); espanso = false })
            }
        }
    }
}
