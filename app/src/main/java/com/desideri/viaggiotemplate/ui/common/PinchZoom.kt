package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlin.math.hypot

/** Range e default del fattore di zoom di [ZoomableRoot]: 1f = nessuno zoom. */
const val ZOOM_UI_MINIMO = 0.8f
const val ZOOM_UI_MASSIMO = 2.0f
const val ZOOM_UI_DEFAULT = 1f

/**
 * Avvolge tutta la UI dell'app con un gesto di pinch-to-zoom generale: scala [LocalDensity]
 * (density + fontScale) invece di applicare una trasformazione puramente visiva, così il layout
 * viene ricalcolato alla nuova densità e tocchi/scroll restano coerenti con quello che si vede —
 * lo stesso meccanismo usato da Android per le impostazioni di sistema "dimensione carattere/
 * display". Essendo un CompositionLocal, la densità scalata raggiunge automaticamente anche
 * Dialog/DropdownMenu/Popup dichiarati più in basso nell'albero di composizione, senza bisogno di
 * propagarla a mano.
 *
 * Il rilevatore del gesto (vedi [rilevaPinchZoom]) non consuma mai i tocchi a un solo dito: scroll,
 * click e drag nelle schermate sottostanti restano invariati. Entra in azione solo quando rileva
 * almeno due puntatori contemporanei, cioè un vero pinch.
 *
 * Il fattore è persistito con lo stesso meccanismo di
 * [com.desideri.viaggiotemplate.data.local.ImpostazioniStore] solo a fine gesto (non ad ogni
 * variazione), per non scrivere su disco decine di volte al secondo durante un pinch.
 *
 * Reset a 1x: niente doppio tap globale (richiederebbe far attendere ~300ms ad ogni singolo tocco
 * in tutta l'app, per distinguerlo da un eventuale secondo tap — latenza percepibile su ogni
 * bottone). Un piccolo chip compare invece in basso a destra quando lo zoom non è 1x, a dimensione
 * fissa (dichiarato fuori dal CompositionLocalProvider) così resta un bersaglio comodo a qualunque
 * livello di zoom.
 */
@Composable
fun ZoomableRoot(content: @Composable () -> Unit) {
    val store = AppContainer.impostazioniStore
    var fattore by remember { mutableFloatStateOf(store.fattoreZoomUi.coerceIn(ZOOM_UI_MINIMO, ZOOM_UI_MASSIMO)) }

    val densitaBase = LocalDensity.current
    val densitaScalata = remember(densitaBase, fattore) {
        Density(density = densitaBase.density * fattore, fontScale = densitaBase.fontScale * fattore)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                rilevaPinchZoom(
                    onVariazione = { delta -> fattore = (fattore * delta).coerceIn(ZOOM_UI_MINIMO, ZOOM_UI_MASSIMO) },
                    onFineGesto = { store.fattoreZoomUi = fattore }
                )
            }
    ) {
        CompositionLocalProvider(LocalDensity provides densitaScalata) {
            content()
        }

        if (fattore != ZOOM_UI_DEFAULT) {
            ChipResetZoom(
                percentuale = (fattore * 100).toInt(),
                onClick = {
                    fattore = ZOOM_UI_DEFAULT
                    store.fattoreZoomUi = ZOOM_UI_DEFAULT
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Rileva un pinch a due dita e per ogni variazione riporta il rapporto di scala rispetto
 * all'evento precedente. Usa [PointerEventPass.Initial] (i genitori lo vedono prima dei figli)
 * per marcare l'evento come consumato prima che un eventuale scroll/click figlio lo intercetti —
 * ma solo quando i puntatori premuti sono almeno due: un tocco singolo non viene mai consumato qui,
 * quindi scroll e click nelle schermate sottostanti non ne risentono.
 */
private suspend fun PointerInputScope.rilevaPinchZoom(onVariazione: (Float) -> Unit, onFineGesto: () -> Unit) {
    awaitEachGesture {
        var distanzaPrecedente: Float? = null
        var pinchAttivo = false
        do {
            val evento = awaitPointerEvent(PointerEventPass.Initial)
            val puntatori = evento.changes.filter { it.pressed }
            if (puntatori.size >= 2) {
                val distanza = distanzaTra(puntatori[0].position, puntatori[1].position)
                val precedente = distanzaPrecedente
                if (precedente != null && precedente > 0f) {
                    val variazione = distanza / precedente
                    if (variazione != 1f) onVariazione(variazione)
                }
                distanzaPrecedente = distanza
                pinchAttivo = true
                evento.changes.forEach { it.consume() }
            } else {
                distanzaPrecedente = null
            }
        } while (evento.changes.any { it.pressed })
        if (pinchAttivo) onFineGesto()
    }
}

private fun distanzaTra(a: Offset, b: Offset): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

@Composable
private fun ChipResetZoom(percentuale: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset zoom", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("$percentuale% · reset", style = MaterialTheme.typography.labelMedium)
        }
    }
}
