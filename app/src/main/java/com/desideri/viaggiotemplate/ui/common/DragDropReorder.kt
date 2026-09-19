package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

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

    /**
     * Velocità (px/frame) dell'autoscroll quando l'item trascinato è vicino al bordo del
     * viewport: negativa verso l'alto, positiva verso il basso, 0 quando non serve. Letta da un
     * ticker esterno (vedi [rememberDragDropListState]) che esegue lo scroll vero e proprio —
     * qui si calcola solo QUANTO scrollare, non lo scroll stesso, perché questa classe non ha
     * accesso a una coroutine scope propria.
     */
    var velocitaAutoScroll by mutableFloatStateOf(0f)
        private set

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
        rivalutaPosizione()
    }

    /**
     * Rileva scambi con l'item vicino e aggiorna [velocitaAutoScroll] in base alla posizione
     * corrente — richiamata sia da [onDrag] (il dito si muove) sia dal ticker di autoscroll
     * (vedi [rememberDragDropListState]) ad ogni frame in cui la lista sta scorrendo da sola,
     * così gli scambi vengono rilevati anche quando ci si avvicina al bordo e ci si ferma: senza
     * questo, l'autoscroll rivelerebbe nuovi elementi senza mai proporli come bersaglio finché il
     * dito non si muove di nuovo.
     */
    internal fun rivalutaPosizione() {
        val currentIndex = draggingItemIndex
        val currentItem = draggingItemLayoutInfo
        if (currentIndex == null || currentItem == null) {
            velocitaAutoScroll = 0f
            return
        }
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

        val layoutInfo = lazyListState.layoutInfo
        val distanzaSuperiore = inizio - layoutInfo.viewportStartOffset
        val distanzaInferiore = layoutInfo.viewportEndOffset - fine
        velocitaAutoScroll = when {
            distanzaSuperiore < SOGLIA_AUTOSCROLL_PX ->
                -((SOGLIA_AUTOSCROLL_PX - distanzaSuperiore).coerceIn(0f, SOGLIA_AUTOSCROLL_PX) / SOGLIA_AUTOSCROLL_PX) * VELOCITA_MASSIMA_PX
            distanzaInferiore < SOGLIA_AUTOSCROLL_PX ->
                ((SOGLIA_AUTOSCROLL_PX - distanzaInferiore).coerceIn(0f, SOGLIA_AUTOSCROLL_PX) / SOGLIA_AUTOSCROLL_PX) * VELOCITA_MASSIMA_PX
            else -> 0f
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggingItemInitialOffset = 0
        draggedDistance = 0f
        velocitaAutoScroll = 0f
    }

    private companion object {
        /** Distanza (px) dal bordo del viewport entro cui scatta l'autoscroll. */
        const val SOGLIA_AUTOSCROLL_PX = 250f

        /** Velocità massima (px/frame) dell'autoscroll, raggiunta quando si è già oltre il bordo. */
        const val VELOCITA_MASSIMA_PX = 20f
    }
}

/**
 * [onMove] è avvolto in [rememberUpdatedState]: la lambda passata a [DragDropListState] resta
 * stabile fra le ricomposizioni (necessario perché [remember] la fissi una sola volta), ma
 * delega sempre alla versione più recente — altrimenti un trascinamento userebbe la lista
 * catturata alla primissima composizione, non quella filtrata/aggiornata corrente.
 *
 * Il [LaunchedEffect] è il "ticker" dell'autoscroll: ad ogni frame, se [DragDropListState] segnala
 * una [DragDropListState.velocitaAutoScroll] diversa da zero (item trascinato vicino al bordo del
 * viewport), scorre la lista di quella quantità e ricontrolla scambi/bordo — necessario perché
 * `userScrollEnabled = false` sulla LazyColumn durante un trascinamento (per evitare che lo scroll
 * manuale confligga col riordino) blocca solo lo scroll da gesto utente, non quello programmatico
 * via [LazyListState].
 */
@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit
): DragDropListState {
    val onMoveAggiornato by rememberUpdatedState(onMove)
    val dragDropListState = remember(lazyListState) { DragDropListState(lazyListState) { from, to -> onMoveAggiornato(from, to) } }
    // Il ticker gira solo mentre si sta trascinando (chiave = null/non-null di draggingItemIndex,
    // non l'indice stesso: cambia ad ogni scambio, riavviarlo ogni volta sarebbe inutile): un
    // while(true)+withFrameMillis attivo per tutta la vita della schermata richiederebbe frame in
    // continuazione anche da fermo, sprecando batteria.
    LaunchedEffect(dragDropListState.draggingItemIndex != null) {
        if (dragDropListState.draggingItemIndex == null) return@LaunchedEffect
        while (isActive) {
            withFrameMillis { }
            if (dragDropListState.velocitaAutoScroll != 0f) {
                lazyListState.scrollBy(dragDropListState.velocitaAutoScroll)
                dragDropListState.rivalutaPosizione()
            }
        }
    }
    return dragDropListState
}

/**
 * Vista locale di [fonte] usata per il rendering di una lista riordinabile via trascinamento, più
 * lo stato del trascinamento stesso già collegato ad essa. [onSposta] è la persistenza vera e
 * propria (tipicamente una scrittura asincrona su database via ViewModel): qui viene solo
 * invocata, mai atteso il suo completamento, perché la vista mostrata a schermo si aggiorna
 * SUBITO e in modo sincrono ad ogni scambio, senza aspettare quel giro di andata/ritorno — se
 * dipendesse da [fonte] per riflettere ogni scambio durante un trascinamento veloce, i tempi del
 * database (non immediati) causerebbero disallineamenti fra indice trascinato e layout reale,
 * con salti e sovrapposizioni visive.
 *
 * La vista si risincronizza da [fonte] ogni volta che questa cambia MA solo quando non si sta
 * trascinando: un aggiornamento di [fonte] arrivato a metà trascinamento (magari riflette solo
 * una parte degli scambi già fatti localmente) andrebbe altrimenti a sovrascrivere la vista
 * corrente con uno stato intermedio incoerente.
 */
@Composable
fun <T> rememberListaRiordinabile(
    fonte: List<T>,
    lazyListState: LazyListState,
    onSposta: (elemento: T, direzione: Int) -> Unit
): Pair<List<T>, DragDropListState> {
    var vista by remember { mutableStateOf(fonte) }
    val dragDropListState = rememberDragDropListState(lazyListState) { da, a ->
        val elemento = vista[da]
        vista = vista.toMutableList().apply {
            val temp = this[da]
            this[da] = this[a]
            this[a] = temp
        }
        onSposta(elemento, a - da)
    }
    LaunchedEffect(fonte) {
        if (dragDropListState.draggingItemIndex == null) vista = fonte
    }
    return vista to dragDropListState
}

/** Da applicare alla Card/riga di ogni item: la solleva sopra le altre e la sposta mentre viene trascinata. */
fun Modifier.dragDropItem(dragDropListState: DragDropListState, index: Int): Modifier =
    if (dragDropListState.draggingItemIndex == index) {
        graphicsLayer { translationY = dragDropListState.draggingItemOffset }.zIndex(1f)
    } else {
        this
    }

/**
 * Da applicare alla sola maniglia (icona [Icons.Filled.DragHandle]): avvia e guida il trascinamento.
 *
 * Il rilevatore del gesto è tenuto vivo con [pointerInput] agganciato a [dragDropListState] (stabile
 * per tutta la vita della schermata), MAI a [index]: [index] cambia proprio a causa degli scambi che
 * il trascinamento in corso produce, quindi tenerlo come chiave riavvierebbe il gesto — e con lui
 * `detectDragGestures` — a ogni scambio, interrompendo il trascinamento a metà (le schede restavano
 * "incastrate" sovrapposte perché il gesto veniva abbandonato dopo il primo scambio mentre il dito
 * restava premuto). [index] resta comunque sempre aggiornato tramite [rememberUpdatedState]: serve
 * fresco solo all'inizio di ogni NUOVO trascinamento (`onDragStart`), non durante uno già in corso.
 */
fun Modifier.dragHandle(dragDropListState: DragDropListState, index: Int): Modifier = composed {
    val indiceAggiornato by rememberUpdatedState(index)
    pointerInput(dragDropListState) {
        detectDragGestures(
            onDragStart = { dragDropListState.onDragStart(indiceAggiornato) },
            onDragEnd = { dragDropListState.onDragEnd() },
            onDragCancel = { dragDropListState.onDragEnd() },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropListState.onDrag(dragAmount.y)
            }
        )
    }
}
