package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Campo data: apre un popup con calendario Material3 (che offre anche l'inserimento manuale tramite la sua icona tastiera). */
@Composable
fun CampoData(
    valore: LocalDate,
    onValoreCambiato: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    etichetta: String = "Data (gg/mm/aaaa)"
) {
    var mostraPopup by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valore.format(FORMATO_DATA),
        onValueChange = {},
        readOnly = true,
        label = { Text(etichetta) },
        trailingIcon = {
            IconButton(onClick = { mostraPopup = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Scegli data")
            }
        },
        modifier = modifier.fillMaxWidth()
    )

    if (mostraPopup) {
        DialogSelettoreData(
            dataIniziale = valore,
            onConferma = { onValoreCambiato(it); mostraPopup = false },
            onDismiss = { mostraPopup = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogSelettoreData(
    dataIniziale: LocalDate,
    onConferma: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Il DatePicker di Material3 lavora in millisecondi UTC riferiti alla mezzanotte UTC del giorno scelto.
    val statoData = rememberDatePickerState(
        initialSelectedDateMillis = dataIniziale.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = statoData.selectedDateMillis
                if (millis != null) {
                    onConferma(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    ) {
        DatePicker(state = statoData)
    }
}
