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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

/**
 * Login con un account Italo reale, alternativo a quello "guest" di default usato da
 * `OrariItaloClient`: quest'ultimo e' bloccato dal gateway anti-bot di Italo (HTTP 403), un vero
 * account potrebbe non esserlo, ma non c'e' alcuna garanzia (l'endpoint non e' ufficiale e puo'
 * bloccare comunque il traffico non da browser). Username e password restano solo su questo
 * dispositivo, cifrati (vedi `ItaloCredentialsStore`).
 */
@Composable
fun DialogLoginItalo(viewModel: ImpostazioniViewModel, onDismiss: () -> Unit) {
    val stato by viewModel.stato.collectAsState()
    var username by remember { mutableStateOf(stato.italoUsername ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisibile by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text("Accedi a Italo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usato al posto del login guest (bloccato da Italo) per cercare gli orari reali. " +
                        "Endpoint non ufficiale: anche con un account vero non è garantito che funzioni. " +
                        "Le credenziali restano cifrate solo su questo dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                if (stato.italoUsername != null) {
                    Text(
                        "Account salvato: ${stato.italoUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / email Italo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisibile) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisibile = !passwordVisibile }) {
                            Icon(
                                if (passwordVisibile) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisibile) "Nascondi password" else "Mostra password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (stato.italoUsername != null) {
                        TextButton(onClick = { viewModel.rimuoviCredenzialiItalo(); password = "" }) { Text("Rimuovi") }
                    }
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                    Button(
                        onClick = { viewModel.salvaCredenzialiItalo(username.trim(), password) },
                        enabled = username.isNotBlank() && password.isNotBlank()
                    ) { Text("Salva") }
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Versione ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                }
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

                    // weight(fill = false): la lista prende solo lo spazio che le serve entro il
                    // limite del dialog, cosi' il pulsante "Chiudi" sottostante resta sempre visibile
                    // invece di essere spinto fuori dai bound quando lo storico e' lungo.
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
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
