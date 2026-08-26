package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.data.remote.clientOrariPer
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import com.desideri.viaggiotemplate.domain.model.Vettore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Breve descrizione della fonte dati non ufficiale usata per scaricare gli orari di questo vettore, da mostrare nei dialog di ricerca. */
fun descrizioneFonteOrari(vettore: Vettore): String = when (vettore) {
    Vettore.SBB -> "dati da transport.opendata.ch (trasporti pubblici svizzeri)"
    Vettore.TRENITALIA -> "dati dal sito trenitalia.com/lefrecce.it (integrazione non ufficiale)"
    Vettore.ITALO -> "dati dal sistema di prenotazione Italo (integrazione non ufficiale)"
    Vettore.ALTRO -> "fonte non ufficiale"
}

/**
 * Corpo condiviso dei dialog "scarica orari reali" (SBB, Trenitalia, ...): lancia la ricerca sul
 * client giusto per [vettore] (vedi [clientOrariPer]) ogni volta che cambia [chiaveRicerca] (o uno
 * degli altri parametri di ricerca), mostra caricamento/errore/risultati, e delega a chi lo usa
 * cosa succede quando l'utente sceglie una corsa (aggiungerla come orario fisso, usarla per un
 * solo evento, ecc. — vedi [testoAzione]/[azioneAbilitata]/[onAzione]).
 */
@Composable
fun RicercaOrariTreno(
    vettore: Vettore,
    daStazione: String,
    aStazione: String,
    data: LocalDate,
    oraRiferimento: LocalTime = LocalTime.MIDNIGHT,
    limite: Int = 16,
    chiaveRicerca: Any?,
    testoAzione: (CorsaScaricata) -> String,
    azioneAbilitata: (CorsaScaricata) -> Boolean,
    onAzione: (CorsaScaricata) -> Unit
) {
    val client = remember(vettore) { clientOrariPer(vettore) }
    var inCorso by remember { mutableStateOf(true) }
    var errore by remember { mutableStateOf<String?>(null) }
    var risultati by remember { mutableStateOf<List<CorsaScaricata>>(emptyList()) }

    LaunchedEffect(vettore, daStazione, aStazione, data, oraRiferimento, chiaveRicerca) {
        if (client == null) {
            inCorso = false
            errore = "Nessuna integrazione disponibile per il vettore $vettore."
            return@LaunchedEffect
        }
        inCorso = true
        errore = null
        try {
            val corse = withContext(Dispatchers.IO) {
                client.cercaCorse(daStazione, aStazione, data, oraRiferimento, limite)
            }
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
            val abilitata = azioneAbilitata(corsa)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = abilitata) { onAzione(corsa) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val colore = if (abilitata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    "${corsa.partenza.toStringHHmm()} → ${corsa.arrivo.toStringHHmm()}" +
                        if (corsa.etichetta.isNotBlank()) " (${corsa.etichetta})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colore
                )
                val etichettaAzione = testoAzione(corsa)
                if (etichettaAzione.isNotBlank()) {
                    Text(etichettaAzione, style = MaterialTheme.typography.bodySmall, color = colore)
                }
            }
        }
    }
}
