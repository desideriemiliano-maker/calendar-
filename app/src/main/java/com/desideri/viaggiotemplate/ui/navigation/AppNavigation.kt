package com.desideri.viaggiotemplate.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.desideri.viaggiotemplate.ui.backup.BackupDriveHost
import com.desideri.viaggiotemplate.ui.backup.BackupDriveViewModel
import com.desideri.viaggiotemplate.ui.backup.BackupDriveViewModelFactory
import com.desideri.viaggiotemplate.ui.esecuzione.EsecuzioneScreen
import com.desideri.viaggiotemplate.ui.eventicreati.EliminaPassatiHost
import com.desideri.viaggiotemplate.ui.eventicreati.EliminaPassatiViewModel
import com.desideri.viaggiotemplate.ui.eventicreati.EliminaPassatiViewModelFactory
import com.desideri.viaggiotemplate.ui.eventicreati.EventiCreatiScreen
import com.desideri.viaggiotemplate.ui.impostazioni.DialogCalendario
import com.desideri.viaggiotemplate.ui.impostazioni.DialogVersioni
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModel
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModelFactory
import com.desideri.viaggiotemplate.ui.luoghi.LuoghiScreen
import com.desideri.viaggiotemplate.ui.registro.RegistroAttivitaScreen
import com.desideri.viaggiotemplate.ui.template.TemplateScreen
import com.desideri.viaggiotemplate.ui.tratte.TratteScreen

private sealed class Sezione(val route: String, val titolo: String) {
    data object Tratte : Sezione("tratte", "Tratte")
    data object Luoghi : Sezione("luoghi", "Luoghi")
    data object Template : Sezione("template", "Template")
    data object Esecuzione : Sezione("esecuzione", "Esegui")
    data object EventiCreati : Sezione("eventi_creati", "Eventi")
}

private val sezioni = listOf(Sezione.EventiCreati, Sezione.Luoghi, Sezione.Tratte, Sezione.Template, Sezione.Esecuzione)

/** Frazione della larghezza dello schermo che uno swipe orizzontale deve superare per cambiare sezione: abbastanza da non scattare per un tocco impreciso o l'avvio di uno scroll verticale, non così tanta da sembrare poco reattivo. */
private const val SOGLIA_SWIPE_FRAZIONE_LARGHEZZA = 0.20f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(pinchZoomAbilitato: Boolean, onAlternaPinchZoom: () -> Unit) {
    val navController = rememberNavController()
    val impostazioniViewModel: ImpostazioniViewModel = viewModel(factory = ImpostazioniViewModelFactory.get())
    val backupDriveViewModel: BackupDriveViewModel = viewModel(factory = BackupDriveViewModelFactory.get())
    val eliminaPassatiViewModel: EliminaPassatiViewModel = viewModel(factory = EliminaPassatiViewModelFactory.get())
    val activity = LocalContext.current as? Activity

    var menuEspanso by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
    var mostraRegistroAttivita by remember { mutableStateOf(false) }
    var mostraVersioni by remember { mutableStateOf(false) }
    var mostraConfermaUscita by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val inHomeTab = currentDestination?.hierarchy?.any { it.route == Sezione.EventiCreati.route } == true

    fun navigaASezione(sezione: Sezione) {
        navController.navigate(sezione.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Con la navigazione a tab non c'è una vera pila da svuotare: il back di sistema deve prima
    // riportare alla tab Eventi creati (invece di uscire subito dall'app), e solo da lì chiedere
    // conferma prima di chiudere.
    BackHandler {
        if (inHomeTab) mostraConfermaUscita = true else navigaASezione(Sezione.EventiCreati)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendario++") },
                actions = {
                    IconButton(onClick = { menuEspanso = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Altre opzioni")
                    }
                    DropdownMenu(expanded = menuEspanso, onDismissRequest = { menuEspanso = false }) {
                        DropdownMenuItem(
                            text = { Text("Calendario") },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraCalendario = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Pinch-to-zoom") },
                            leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null) },
                            // Lo switch è solo indicativo (onCheckedChange = null): il tocco su tutta la riga
                            // alterna lo stato tramite onClick, per evitare un doppio toggle se anche lo switch
                            // rispondesse al tocco per conto suo. Il menu resta aperto dopo il tocco, così si
                            // vede subito il nuovo stato invece di doverlo riaprire per controllarlo.
                            trailingIcon = { Switch(checked = pinchZoomAbilitato, onCheckedChange = null) },
                            onClick = onAlternaPinchZoom
                        )
                        DropdownMenuItem(
                            text = { Text("Elimina eventi passati") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            onClick = { menuEspanso = false; eliminaPassatiViewModel.avvia() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Backup su Drive") },
                            leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                            onClick = { menuEspanso = false; activity?.let { backupDriveViewModel.avviaBackup(it) } }
                        )
                        DropdownMenuItem(
                            text = { Text("Ripristina da Drive") },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                            onClick = { menuEspanso = false; activity?.let { backupDriveViewModel.avviaRipristino(it) } }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Registro attività") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraRegistroAttivita = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Versioni") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraVersioni = true }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                sezioni.forEach { sezione ->
                    val selezionata = currentDestination?.hierarchy?.any { it.route == sezione.route } == true
                    NavigationBarItem(
                        selected = selezionata,
                        onClick = { navigaASezione(sezione) },
                        icon = {
                            val icona = when (sezione) {
                                Sezione.Tratte -> Icons.Filled.DirectionsCar
                                Sezione.Luoghi -> Icons.Filled.Place
                                Sezione.Template -> Icons.Filled.ListAlt
                                Sezione.Esecuzione -> Icons.Filled.Event
                                Sezione.EventiCreati -> Icons.Filled.History
                            }
                            Icon(icona, contentDescription = sezione.titolo)
                        },
                        label = { Text(sezione.titolo) }
                    )
                }
            }
        }
    ) { padding ->
        val indiceSezioneCorrente = sezioni.indexOfFirst { sezione ->
            currentDestination?.hierarchy?.any { it.route == sezione.route } == true
        }
        NavHost(
            navController = navController,
            startDestination = Sezione.EventiCreati.route,
            modifier = Modifier
                .padding(padding)
                // Cambia sezione con uno swipe orizzontale, nello stesso ordine della barra in
                // basso e sincronizzato con essa in entrambe le direzioni (richiama la stessa
                // navigaASezione dei tap sulla barra). Ri-chiavato su indiceSezioneCorrente: la
                // chiusura cattura altrimenti l'indice di quando il gesto è partito, non quello
                // corrente dopo un'eventuale navigazione nel frattempo.
                //
                // detectHorizontalDragGestures (passata Main, non Initial come invece fa
                // ZoomableRoot per il pinch) consuma solo dopo aver superato la soglia di
                // scorrimento ORIZZONTALE: una LazyRow o un componente scorrevole annidati (liste
                // orizzontali in un editor, la mappa OSMDroid embeddata via AndroidView) vedono
                // l'evento prima, nella stessa Main pass, e se lo consumano per un proprio
                // scroll/pan quello che arriva qui risulta già `isConsumed` — questo detector si
                // ferma da solo, non serve escludere esplicitamente nessuna schermata. Un drag
                // prevalentemente verticale (scroll di una lista) viene già risolto a monte dalla
                // stessa logica di rilevamento della soglia, orientation-aware in Compose.
                .pointerInput(indiceSezioneCorrente) {
                    var trascinamentoOrizzontale = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (indiceSezioneCorrente >= 0) {
                                val sogliaMinima = size.width * SOGLIA_SWIPE_FRAZIONE_LARGHEZZA
                                val direzione = when {
                                    trascinamentoOrizzontale <= -sogliaMinima -> 1
                                    trascinamentoOrizzontale >= sogliaMinima -> -1
                                    else -> 0
                                }
                                val nuovoIndice = (indiceSezioneCorrente + direzione).coerceIn(0, sezioni.lastIndex)
                                if (direzione != 0 && nuovoIndice != indiceSezioneCorrente) {
                                    navigaASezione(sezioni[nuovoIndice])
                                }
                            }
                            trascinamentoOrizzontale = 0f
                        },
                        onDragCancel = { trascinamentoOrizzontale = 0f }
                    ) { change, dragAmount ->
                        trascinamentoOrizzontale += dragAmount
                        change.consume()
                    }
                }
        ) {
            composable(Sezione.Tratte.route) { TratteScreen() }
            composable(Sezione.Luoghi.route) { LuoghiScreen() }
            composable(Sezione.Template.route) { TemplateScreen() }
            composable(Sezione.Esecuzione.route) { EsecuzioneScreen() }
            composable(Sezione.EventiCreati.route) { EventiCreatiScreen() }
        }
    }

    if (mostraCalendario) {
        DialogCalendario(viewModel = impostazioniViewModel, onDismiss = { mostraCalendario = false })
    }
    if (mostraRegistroAttivita) {
        RegistroAttivitaScreen(onChiudi = { mostraRegistroAttivita = false })
    }
    if (mostraVersioni) {
        DialogVersioni(onDismiss = { mostraVersioni = false })
    }
    BackupDriveHost(viewModel = backupDriveViewModel)
    EliminaPassatiHost(viewModel = eliminaPassatiViewModel)
    if (mostraConfermaUscita) {
        AlertDialog(
            onDismissRequest = { mostraConfermaUscita = false },
            title = { Text("Uscire da Calendario++?") },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) { Text("Esci") }
            },
            dismissButton = {
                TextButton(onClick = { mostraConfermaUscita = false }) { Text("Annulla") }
            }
        )
    }
}
