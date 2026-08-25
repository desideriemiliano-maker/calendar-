package com.desideri.viaggiotemplate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.desideri.viaggiotemplate.ui.esecuzione.EsecuzioneScreen
import com.desideri.viaggiotemplate.ui.impostazioni.DialogCalendario
import com.desideri.viaggiotemplate.ui.impostazioni.DialogVersioni
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModel
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniViewModelFactory
import com.desideri.viaggiotemplate.ui.template.TemplateScreen
import com.desideri.viaggiotemplate.ui.tratte.TratteScreen

private sealed class Sezione(val route: String, val titolo: String) {
    data object Tratte : Sezione("tratte", "Tratte")
    data object Template : Sezione("template", "Template")
    data object Esecuzione : Sezione("esecuzione", "Esegui")
}

private val sezioni = listOf(Sezione.Tratte, Sezione.Template, Sezione.Esecuzione)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val impostazioniViewModel: ImpostazioniViewModel = viewModel(factory = ImpostazioniViewModelFactory.get())

    var menuEspanso by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
    var mostraVersioni by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viaggio Template") },
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
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                sezioni.forEach { sezione ->
                    val selezionata = currentDestination?.hierarchy?.any { it.route == sezione.route } == true
                    NavigationBarItem(
                        selected = selezionata,
                        onClick = {
                            navController.navigate(sezione.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icona = when (sezione) {
                                Sezione.Tratte -> Icons.Filled.DirectionsCar
                                Sezione.Template -> Icons.Filled.ListAlt
                                Sezione.Esecuzione -> Icons.Filled.Event
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
            startDestination = Sezione.Tratte.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Sezione.Tratte.route) { TratteScreen() }
            composable(Sezione.Template.route) { TemplateScreen() }
            composable(Sezione.Esecuzione.route) { EsecuzioneScreen() }
        }
    }

    if (mostraCalendario) {
        DialogCalendario(viewModel = impostazioniViewModel, onDismiss = { mostraCalendario = false })
    }
    if (mostraVersioni) {
        DialogVersioni(onDismiss = { mostraVersioni = false })
    }
}
