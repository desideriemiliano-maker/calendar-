package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.data.remote.OrariTrasportiSvizzeriClient
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Corpo condiviso dei dialog "scarica orari da SBB": lancia la ricerca (subito se [avviaSubito],
 * altrimenti solo quando cambia [chiaveRicerca]), mostra caricamento/errore/risultati, e delega
 * a chi lo usa cosa succede quando l'utente sceglie una corsa (aggiungerla come orario fisso,
 * usarla per un solo evento, ecc. — vedi [testoAzione]/[azioneAbilitata]/[onAzione]).
 */
@Composable
fun RicercaOrariSbb(
    daStazione: String,
    aStazione: String,
    data: LocalDate,
    chiaveRicerca: Any?,
    testoAzione: (CorsaScaricata) -> String,
    azioneAbilitata: (CorsaScaricata) -> Boolean,
    onAzione: (CorsaScaricata) -> Unit
) {
    val client = remember { OrariTrasportiSvizzeriClient() }
    var inCorso by remember { mutableStateOf(true) }
    var errore by remember { mutableStateOf<String?>(null) }
    var risultati by remember { mutableStateOf<List<CorsaScaricata>>(emptyList()) }

    LaunchedEffect(daStazione, aStazione, data, chiaveRicerca) {
        inCorso = true
        errore = null
        try {
            val corse = withContext(Dispatchers.IO) { client.cercaCorse(daStazione, aStazione, data) }
            risultati = corse
            if (corse.isEmpty()) errore = "Nessuna corsa trovata per questa data."
        } catch (e: Exception) {
            errore = "Impossibile scaricare gli orari: ${e.message ?: "errore di rete"}"
        } finally {
            inCorso = false
        }
    }

    if (inCorso) {
        Row(modifier = Modifier.padding(top = 12.dp)) { CircularProgressIndicator() }
    }
    errore?.let { msg ->
        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    }

    LazyColumn(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(risultati) { corsa ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${corsa.partenza.toStringHHmm()} → ${corsa.arrivo.toStringHHmm()}" +
                        if (corsa.etichetta.isNotBlank()) " (${corsa.etichetta})" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { onAzione(corsa) }, enabled = azioneAbilitata(corsa)) {
                    Text(testoAzione(corsa))
                }
            }
        }
    }
}
