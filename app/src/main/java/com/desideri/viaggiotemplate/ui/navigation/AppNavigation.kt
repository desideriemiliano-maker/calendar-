package com.desideri.viaggiotemplate.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.desideri.viaggiotemplate.ui.eventicreati.EventiCreatiScreen
import com.desideri.viaggiotemplate.ui.impostazioni.DialogCalendario
import com.desideri.viaggiotemplate.ui.impostazioni.DialogVersioni
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModel
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModelFactory
import com.desideri.viaggiotemplate.ui.luoghi.LuoghiScreen
import com.desideri.viaggiotemplate.ui.template.TemplateScreen
import com.desideri.viaggiotemplate.ui.tratte.TratteScreen

private sealed class Sezione(val route: String, val titolo: String) {
    data object Tratte : Sezione("tratte", "Tratte")
    data object Luoghi : Sezione("luoghi", "Luoghi")
    data object Template : Sezione("template", "Template")
    data object Esecuzione : Sezione("esecuzione", "Esegui")
    data object EventiCreati : Sezione("eventi_creati", "Eventi creati")
}

private val sezioni = listOf(Sezione.EventiCreati, Sezione.Luoghi, Sezione.Tratte, Sezione.Template, Sezione.Esecuzione)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val impostazioniViewModel: ImpostazioniViewModel = viewModel(factory = ImpostazioniViewModelFactory.get())
    val backupDriveViewModel: BackupDriveViewModel = viewModel(factory = BackupDriveViewModelFactory.get())
    val activity = LocalContext.current as? Activity

    var menuEspanso by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
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
                            text = { Text("Versioni") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraVersioni = true }
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
        NavHost(
            navController = navController,
            startDestination = Sezione.EventiCreati.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
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
    if (mostraVersioni) {
        DialogVersioni(onDismiss = { mostraVersioni = false })
    }
    BackupDriveHost(viewModel = backupDriveViewModel)
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
