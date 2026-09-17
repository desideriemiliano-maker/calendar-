package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Stato di un riordino via trascinamento su una LazyColumn i cui item sono TUTTI riordinabili
 * (nessun item fisso come header/pulsante in mezzo) — il caso delle liste di Luoghi, Tratte e
 * Template, che sostituisce le vecchie frecce su/giù.
 *
 * [onMove] riceve indici sempre adiacenti (l'item trascinato scambia con un vicino alla volta man
 * mano che lo supera), quindi la stessa firma usata dai vecchi pulsanti freccia — "sposta questo
 * elemento di ±1 posizione" — basta a implementarlo: vedi `onSposta` nelle tre schermate.
 */
class DragDropListState(
    private val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemInitialOffset by mutableIntStateOf(0)
    private var draggedDistance by mutableFloatStateOf(0f)

    private val draggingItemLayoutInfo
        get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** Spostamento verticale (px) da applicare all'item attualmente trascinato. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item -> draggingItemInitialOffset + draggedDistance - item.offset } ?: 0f

    fun onDragStart(index: Int) {
        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let {
            draggingItemIndex = index
            draggingItemInitialOffset = it.offset
            draggedDistance = 0f
        }
    }

    fun onDrag(deltaY: Float) {
        draggedDistance += deltaY

        val currentIndex = draggingItemIndex ?: return
        val currentItem = draggingItemLayoutInfo ?: return
        val inizio = currentItem.offset + draggingItemOffset
        val fine = inizio + currentItem.size
        val centro = inizio + (fine - inizio) / 2f

        val target = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex && centro.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            onMove(currentIndex, target.index)
            draggingItemIndex = target.index
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggingItemInitialOffset = 0
        draggedDistance = 0f
    }
}

/**
 * [onMove] è avvolto in [rememberUpdatedState]: la lambda passata a [DragDropListState] resta
 * stabile fra le ricomposizioni (necessario perché [remember] la fissi una sola volta), ma
 * delega sempre alla versione più recente — altrimenti un trascinamento userebbe la lista
 * catturata alla primissima composizione, non quella filtrata/aggiornata corrente.
 */
@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): DragDropListState {
    val onMoveAggiornato by rememberUpdatedState(onMove)
    return remember(lazyListState) { DragDropListState(lazyListState) { from, to -> onMoveAggiornato(from, to) } }
}

/** Da applicare alla Card/riga di ogni item: la solleva sopra le altre e la sposta mentre viene trascinata. */
fun Modifier.dragDropItem(dragDropListState: DragDropListState, index: Int): Modifier =
    if (dragDropListState.draggingItemIndex == index) {
        graphicsLayer { translationY = dragDropListState.draggingItemOffset }.zIndex(1f)
    } else {
        this
    }

/** Da applicare alla sola maniglia (icona [Icons.Filled.DragHandle]): avvia e guida il trascinamento. */
fun Modifier.dragHandle(dragDropListState: DragDropListState, index: Int): Modifier =
    pointerInput(index) {
        detectDragGestures(
            onDragStart = { dragDropListState.onDragStart(index) },
            onDragEnd = { dragDropListState.onDragEnd() },
            onDragCancel = { dragDropListState.onDragEnd() },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropListState.onDrag(dragAmount.y)
            }
        )
    }
