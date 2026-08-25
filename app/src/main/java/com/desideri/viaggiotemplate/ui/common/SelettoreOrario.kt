package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import java.time.LocalTime

/** Campo orario: digitabile a mano (HH:mm) oppure tramite il popup a orologio (icona a destra). */
@Composable
fun CampoOrario(
    etichetta: String,
    valore: LocalTime,
    onValoreCambiato: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var testo by remember(valore) { mutableStateOf(valore.toStringHHmm()) }
    var mostraPopup by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = testo,
        onValueChange = {
            testo = it
            parseOraOppureNull(it)?.let { t -> onValoreCambiato(t) }
        },
        label = { Text(etichetta) },
        trailingIcon = {
            IconButton(onClick = { mostraPopup = true }) {
                Icon(Icons.Filled.Schedule, contentDescription = "Scegli orario")
            }
        },
        modifier = modifier
    )

    if (mostraPopup) {
        DialogSelettoreOrario(
            orarioIniziale = valore,
            onConferma = { onValoreCambiato(it); mostraPopup = false },
            onDismiss = { mostraPopup = false }
        )
    }
}

/** Variante opzionale di [CampoOrario]: il campo vuoto corrisponde a `null` (nessun orario impostato). */
@Composable
fun CampoOrarioOpzionale(
    etichetta: String,
    valore: LocalTime?,
    onValoreCambiato: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier
) {
    var testo by remember(valore) { mutableStateOf(valore?.toStringHHmm() ?: "") }
    var mostraPopup by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = testo,
        onValueChange = {
            testo = it
            if (it.isBlank()) {
                onValoreCambiato(null)
            } else {
                parseOraOppureNull(it)?.let { t -> onValoreCambiato(t) }
            }
        },
        label = { Text(etichetta) },
        trailingIcon = {
            IconButton(onClick = { mostraPopup = true }) {
                Icon(Icons.Filled.Schedule, contentDescription = "Scegli orario")
            }
        },
        modifier = modifier
    )

    if (mostraPopup) {
        DialogSelettoreOrario(
            orarioIniziale = valore ?: LocalTime.of(9, 0),
            onConferma = { onValoreCambiato(it); mostraPopup = false },
            onDismiss = { mostraPopup = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogSelettoreOrario(
    orarioIniziale: LocalTime,
    onConferma: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val statoOrologio = rememberTimePickerState(
        initialHour = orarioIniziale.hour,
        initialMinute = orarioIniziale.minute,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = statoOrologio)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Annulla") }
                    TextButton(onClick = {
                        onConferma(LocalTime.of(statoOrologio.hour, statoOrologio.minute))
                    }) { Text("OK") }
                }
            }
        }
    }
}

fun parseOraOppureNull(testo: String): LocalTime? = try {
    val (h, m) = testo.split(":").map { it.trim().toInt() }
    LocalTime.of(h, m)
} catch (e: Exception) {
    null
}
