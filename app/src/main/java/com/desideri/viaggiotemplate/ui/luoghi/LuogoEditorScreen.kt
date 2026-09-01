package com.desideri.viaggiotemplate.ui.luoghi

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.desideri.viaggiotemplate.domain.calendar.apriIndirizzoSuMaps
import com.desideri.viaggiotemplate.domain.calendar.apriPosizioneSuMaps
import com.desideri.viaggiotemplate.domain.location.RANGE_LATITUDINE
import com.desideri.viaggiotemplate.domain.location.RANGE_LONGITUDINE
import com.desideri.viaggiotemplate.domain.location.formattaCoordinateGps
import com.desideri.viaggiotemplate.domain.location.parseCoordinateGps
import com.desideri.viaggiotemplate.domain.location.posizioneAttuale
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.ui.common.SelettoreColore
import com.desideri.viaggiotemplate.ui.common.SelettoreIconaLuogo
import kotlinx.coroutines.launch

@Composable
fun LuogoEditorScreen(
    luogoEsistente: Luogo?,
    nuovoId: () -> String,
    ordineIniziale: () -> Int,
    onSalva: (Luogo) -> Unit,
    onAnnulla: () -> Unit,
    padding: PaddingValues
) {
    var nome by remember { mutableStateOf(luogoEsistente?.nome ?: "") }
    var indirizzo by remember { mutableStateOf(luogoEsistente?.indirizzo ?: "") }
    var colore by remember { mutableStateOf(luogoEsistente?.colore) }
    var icona by remember { mutableStateOf(luogoEsistente?.icona) }
    var coordinateTesto by remember {
        mutableStateOf(
            luogoEsistente?.latitudine?.let { lat ->
                luogoEsistente.longitudine?.let { lng -> formattaCoordinateGps(lat, lng) }
            } ?: ""
        )
    }

    val coordinateParse = parseCoordinateGps(coordinateTesto)
    val erroreCoordinate: String? = when {
        coordinateTesto.isBlank() -> null
        coordinateParse == null -> "Formato non valido: usa \"lat, lng\" (es. 45.4642, 9.1900 oppure 45,4642, 9,1900)"
        coordinateParse.first !in RANGE_LATITUDINE -> "Latitudine fuori range: deve essere tra -90 e 90"
        coordinateParse.second !in RANGE_LONGITUDINE -> "Longitudine fuori range: deve essere tra -180 e 180"
        else -> null
    }
    val coordinateValide = coordinateTesto.isBlank() || erroreCoordinate == null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caricandoPosizione by remember { mutableStateOf(false) }
    var messaggioErrorePosizione by remember { mutableStateOf<String?>(null) }

    val permessiPosizione = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    fun permessoPosizioneConcesso() = permessiPosizione.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun avviaRecuperoPosizione() {
        caricandoPosizione = true
        scope.launch {
            val posizione = posizioneAttuale(context)
            caricandoPosizione = false
            if (posizione != null) {
                coordinateTesto = formattaCoordinateGps(posizione.latitude, posizione.longitude)
            } else {
                messaggioErrorePosizione = "Posizione non disponibile: verifica che il GPS sia attivo e riprova."
            }
        }
    }
    val richiediPermessoPosizione = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { risultati ->
        if (risultati.values.any { it }) {
            avviaRecuperoPosizione()
        } else {
            messaggioErrorePosizione = "Permesso posizione negato: impossibile rilevare la posizione attuale."
        }
    }

    LazyColumn(
        modifier = Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = nome, onValueChange = { nome = it },
                label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = indirizzo, onValueChange = { indirizzo = it },
                label = { Text("Indirizzo (opzionale, per la navigazione da tratte AUTO)") },
                trailingIcon = {
                    if (indirizzo.isNotBlank()) {
                        IconButton(onClick = { apriIndirizzoSuMaps(context, indirizzo) }) {
                            Icon(Icons.Filled.Map, contentDescription = "Apri su Google Maps")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            // Niente keyboardOptions/KeyboardType.Decimal qui: su molti IME quella tastiera mostra
            // un solo tasto decimale, quello della lingua del dispositivo (virgola in italiano),
            // impedendo di digitare l'altro separatore. La tastiera di testo di default espone
            // entrambi i simboli, coerente col parsing tollerante di parseCoordinateGps.
            OutlinedTextField(
                value = coordinateTesto,
                onValueChange = { coordinateTesto = it },
                label = { Text("Coordinate GPS (opzionali, hanno priorità sull'indirizzo)") },
                placeholder = { Text("45.4642, 9.1900") },
                isError = erroreCoordinate != null,
                supportingText = { erroreCoordinate?.let { Text(it) } },
                trailingIcon = {
                    Row {
                        IconButton(
                            enabled = !caricandoPosizione,
                            onClick = {
                                if (permessoPosizioneConcesso()) avviaRecuperoPosizione() else richiediPermessoPosizione.launch(permessiPosizione)
                            }
                        ) {
                            if (caricandoPosizione) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.MyLocation, contentDescription = "Usa posizione attuale")
                            }
                        }
                        if (erroreCoordinate == null && coordinateParse != null) {
                            IconButton(
                                onClick = {
                                    apriPosizioneSuMaps(context, coordinateParse.first, coordinateParse.second, nome.ifBlank { null })
                                }
                            ) {
                                Icon(Icons.Filled.Map, contentDescription = "Apri su Google Maps")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SelettoreColore(coloreSelezionato = colore, onCambia = { colore = it })
        }
        item {
            SelettoreIconaLuogo(iconaSelezionata = icona, onCambia = { icona = it })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = nome.isNotBlank() && coordinateValide,
                    onClick = {
                        onSalva(
                            Luogo(
                                id = luogoEsistente?.id ?: nuovoId(),
                                nome = nome.trim(),
                                indirizzo = indirizzo.trim().ifBlank { null },
                                latitudine = coordinateParse?.first,
                                longitudine = coordinateParse?.second,
                                colore = colore,
                                ordine = luogoEsistente?.ordine ?: ordineIniziale(),
                                icona = icona
                            )
                        )
                    }
                ) { Text("Salva") }
                TextButton(onClick = onAnnulla) { Text("Annulla") }
            }
        }
    }

    messaggioErrorePosizione?.let { messaggio ->
        AlertDialog(
            onDismissRequest = { messaggioErrorePosizione = null },
            title = { Text("Impossibile rilevare la posizione") },
            text = { Text(messaggio) },
            confirmButton = { TextButton(onClick = { messaggioErrorePosizione = null }) { Text("OK") } }
        )
    }
}
