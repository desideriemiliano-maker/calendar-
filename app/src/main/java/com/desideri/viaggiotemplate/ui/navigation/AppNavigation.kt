package com.desideri.viaggiotemplate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.desideri.viaggiotemplate.ui.esecuzione.EsecuzioneScreen
import com.desideri.viaggiotemplate.ui.impostazioni.ImpostazioniScreen
import com.desideri.viaggiotemplate.ui.template.TemplateScreen
import com.desideri.viaggiotemplate.ui.tratte.TratteScreen

private sealed class Sezione(val route: String, val titolo: String) {
    data object Tratte : Sezione("tratte", "Tratte")
    data object Template : Sezione("template", "Template")
    data object Esecuzione : Sezione("esecuzione", "Esegui")
    data object Impostazioni : Sezione("impostazioni", "Impostazioni")
}

private val sezioni = listOf(Sezione.Tratte, Sezione.Template, Sezione.Esecuzione, Sezione.Impostazioni)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
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
                                Sezione.Impostazioni -> Icons.Filled.Settings
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
            composable(Sezione.Impostazioni.route) { ImpostazioniScreen() }
        }
    }
}
