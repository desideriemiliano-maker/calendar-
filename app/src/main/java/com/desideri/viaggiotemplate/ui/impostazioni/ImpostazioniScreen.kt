package com.desideri.viaggiotemplate.ui.impostazioni

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.desideri.viaggiotemplate.BuildConfig
import com.desideri.viaggiotemplate.changelog.CHANGELOG
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DialogCalendario(viewModel: ImpostazioniViewModel, onDismiss: () -> Unit) {
    val stato by viewModel.stato.collectAsState()
    val context = LocalContext.current

    val permessiCalendario = remember { arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR) }
    fun permessiGiaConcessi() = permessiCalendario.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var permessoConcesso by remember { mutableStateOf(permessiGiaConcessi()) }
    val richiediPermessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { risultati ->
        permessoConcesso = risultati.values.all { it }
    }

    LaunchedEffect(permessoConcesso) {
        if (permessoConcesso) viewModel.caricaCalendari(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text("Calendario di destinazione", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scegli su quale calendario Google verranno scritti gli eventi generati da \"Esegui template\". " +
                        "Il dispositivo può avere più account Google, ognuno con un proprio calendario: scegli quello giusto qui, una volta sola.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                if (!permessoConcesso) {
                    Button(onClick = { richiediPermessi.launch(permessiCalendario) }) {
                        Text("Consenti accesso al calendario")
                    }
                } else if (stato.calendariDisponibili.isEmpty()) {
                    Text("Nessun calendario scrivibile trovato sul dispositivo.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stato.calendariDisponibili, key = { it.id }) { cal ->
                            val selezionato = cal.id == stato.calendarioSelezionatoId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selezionato, onClick = { viewModel.selezionaCalendario(cal.id) })
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selezionato, onClick = { viewModel.selezionaCalendario(cal.id) })
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text(cal.nome)
                                        Text(
                                            cal.account + if (cal.isPrimary) " · primario" else "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

private val FORMATO_DATA_VISUALIZZATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * I commit di questo repo non seguono la convenzione "feat:/fix:/chore:", ma prefissano
 * spesso il messaggio con l'area toccata ("Tratte: ...", "Esegui: ...") o con "Fix: " per le
 * correzioni. Si sfrutta questo stesso prefisso (parola prima dei ":") per raggruppare e
 * scegliere un'icona, invece di imporre una tassonomia che i messaggi non usano.
 */
private fun classificaMessaggio(messaggio: String): Pair<String?, String> {
    val match = Regex("^([\\wÀ-ÿ]+)\\s*:\\s*").find(messaggio) ?: return null to messaggio
    val prefisso = match.groupValues[1]
    return prefisso to messaggio.substring(match.range.last + 1).trim()
}

private fun iconaPrefisso(prefisso: String?): String = when {
    prefisso == null -> "•"
    prefisso.equals("fix", ignoreCase = true) -> "🐛"
    else -> "🔧"
}

@Composable
fun DialogVersioni(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Text(
                    "Versione ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Storico delle modifiche, raggruppato per data.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (CHANGELOG.isEmpty()) {
                    Text("Nessuno storico disponibile.", style = MaterialTheme.typography.bodySmall)
                } else {
                    // Le voci arrivano gia' ordinate dalla piu' recente alla piu' vecchia e i
                    // commit della stessa data sono sempre adiacenti (versionCode e data crescono
                    // insieme): raggrupparle preservando l'ordine di incontro basta ad avere gruppi
                    // di data corretti, senza bisogno di riordinare.
                    val vociOrdinate = remember { CHANGELOG.sortedByDescending { it.versionCode } }
                    val gruppiPerData = remember(vociOrdinate) { vociOrdinate.groupBy { it.data } }

                    LazyColumn {
                        gruppiPerData.forEach { (data, voci) ->
                            item {
                                Text(
                                    etichettaData(data),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            val classificati = voci.map { classificaMessaggio(it.messaggio) }
                            val perPrefisso = classificati.groupBy({ it.first }, { it.second })

                            perPrefisso.forEach { (prefisso, messaggi) ->
                                item {
                                    Text(
                                        "${iconaPrefisso(prefisso)} ${prefisso ?: "Generale"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(messaggi) { messaggio ->
                                    Text("• $messaggio", modifier = Modifier.padding(start = 16.dp, bottom = 2.dp))
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

private fun etichettaData(dataIso: String): String = try {
    LocalDate.parse(dataIso).format(FORMATO_DATA_VISUALIZZATA)
} catch (_: Exception) {
    dataIso
}
