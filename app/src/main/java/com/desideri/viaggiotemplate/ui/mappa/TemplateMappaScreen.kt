package com.desideri.viaggiotemplate.ui.mappa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.desideri.viaggiotemplate.BuildConfig
import com.desideri.viaggiotemplate.domain.mappa.PercorsoTemplate
import com.desideri.viaggiotemplate.domain.mappa.risolviPercorso
import com.desideri.viaggiotemplate.domain.mappa.risolviPercorsoTratta
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Tratta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

/** Un nodo del percorso già geolocalizzato (coordinate dirette o geocodificate), pronto per il marker. */
private data class TappaGeolocalizzata(
    val numero: Int,
    val luogo: Luogo,
    val lat: Double,
    val lng: Double,
    /** Attesa configurata in questo luogo (vedi [com.desideri.viaggiotemplate.domain.mappa.NodoPercorso]); null = da omettere. */
    val attesaMinuti: Int?
)

/** Un arco già geolocalizzato tra due tappe consecutivamente mostrate (possono non essere nodi adiacenti nel percorso originale, se uno intermedio è stato escluso). */
private data class SegmentoGeolocalizzato(
    val da: GeoPoint,
    val a: GeoPoint,
    /** Durata (certa o indicativa); null se non determinabile — un nodo intermedio escluso, o una tratta TRENO/AEREO senza alcun orario configurato. */
    val durataMinuti: Int?,
    val durataIndicativa: Boolean,
    /**
     * Tipo della tratta di questo arco (null solo per un "salto" senza tratta diretta): mostrato
     * sempre come icona sull'arco accanto alla durata, non solo quando la durata manca — vedi
     * [EtichettaSegmento].
     */
    val tipoTratta: TipoTratta?
)

/** Etichetta di un arco: icona del tipo sempre presente, durata accanto quando nota. */
private data class EtichettaSegmento(
    val da: GeoPoint,
    val a: GeoPoint,
    val tipo: TipoTratta,
    val testoDurata: String?
)

private data class RisoluzioneMappa(
    val tappe: List<TappaGeolocalizzata>,
    val segmenti: List<SegmentoGeolocalizzato>,
    val senzaDati: Int,
    val geocodingFallito: Int
)

private data class NodoRisolto(val luogo: Luogo, val lat: Double, val lng: Double)

/**
 * Risolve un percorso già calcolato (vedi [risolviPercorso]/[risolviPercorsoTratta]) in coordinate
 * mostrabili su mappa. Per i luoghi con coordinate GPS già salvate le usa direttamente; per quelli
 * con solo un indirizzo tenta un geocoding "best effort" con [Geocoder] (integrato in Android,
 * gratuito, ma non garantito su tutti i dispositivi). Se anche questo fallisce, o se il luogo non
 * ha né coordinate né indirizzo, il nodo viene escluso e conteggiato (mai un errore silenzioso).
 *
 * Un nodo intermedio escluso "salta" semplicemente il segmento: la linea retta successiva collega
 * direttamente i due punti mostrabili più vicini, ma senza durata etichettata (non c'è una singola
 * tratta a spiegare quel segmento combinato).
 */
private suspend fun risolviMappa(context: Context, percorso: PercorsoTemplate, luoghi: List<Luogo>): RisoluzioneMappa {
    val luoghiPerId = luoghi.associateBy { it.id }
    val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

    var senzaDati = 0
    var geocodingFallito = 0

    val risolti: List<NodoRisolto?> = percorso.nodi.map { nodo ->
        val luogo = luoghiPerId[nodo.luogoId]
        val lat = luogo?.latitudine
        val lng = luogo?.longitudine
        when {
            luogo == null -> { senzaDati++; null }
            lat != null && lng != null -> NodoRisolto(luogo, lat, lng)
            !luogo.indirizzo.isNullOrBlank() -> {
                val geocodificato = geocodificaBestEffort(geocoder, luogo.indirizzo)
                if (geocodificato != null) NodoRisolto(luogo, geocodificato.first, geocodificato.second)
                else { geocodingFallito++; null }
            }
            else -> { senzaDati++; null }
        }
    }

    val indiciValidi = risolti.indices.filter { risolti[it] != null }
    val tappe = indiciValidi.mapIndexed { pos, indiceOriginale ->
        val risolto = risolti[indiceOriginale]!!
        TappaGeolocalizzata(pos + 1, risolto.luogo, risolto.lat, risolto.lng, percorso.nodi[indiceOriginale].attesaMinuti)
    }
    val segmenti = (0 until indiciValidi.size - 1).map { pos ->
        val i0 = indiciValidi[pos]
        val i1 = indiciValidi[pos + 1]
        val r0 = risolti[i0]!!
        val r1 = risolti[i1]!!
        // L'arco è noto solo se non abbiamo saltato nessun nodo escluso tra i due (i1 == i0 + 1):
        // altrimenti il segmento disegnato copre più di un arco originale, e nessuna singola tratta lo spiega.
        val arco = if (i1 == i0 + 1) percorso.archi.getOrNull(i0) else null
        SegmentoGeolocalizzato(
            da = GeoPoint(r0.lat, r0.lng),
            a = GeoPoint(r1.lat, r1.lng),
            durataMinuti = arco?.durataMinuti,
            durataIndicativa = arco?.durataIndicativa ?: false,
            tipoTratta = arco?.tipoTratta
        )
    }

    return RisoluzioneMappa(tappe, segmenti, senzaDati, geocodingFallito)
}

/**
 * `Geocoder.getFromLocationName` è bloccante e deprecata da API 33 (sostituita da una variante
 * con callback), ma resta l'unica disponibile su tutto l'intervallo supportato dall'app (minSdk 26):
 * usarla su Dispatchers.IO evita di bloccare il thread principale su entrambe le versioni.
 * Può lanciare IOException (nessuna rete o servizio di geocoding non disponibile) o
 * IllegalArgumentException (indirizzo non valido): in entrambi i casi il fallimento è "normale" e
 * va semplicemente segnalato come tappa esclusa, non propagato come crash.
 */
@Suppress("DEPRECATION")
private suspend fun geocodificaBestEffort(geocoder: Geocoder?, indirizzo: String): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        if (geocoder == null) return@withContext null
        try {
            geocoder.getFromLocationName(indirizzo, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }

/** Formatta minuti in "1h 25m" / "40m" / "2h", senza mai mostrare zero implicito su una parte assente. */
private fun formattaDurataMinuti(minuti: Int): String {
    val ore = minuti / 60
    val resto = minuti % 60
    return when {
        ore == 0 -> "${resto}m"
        resto == 0 -> "${ore}h"
        else -> "${ore}h ${resto}m"
    }
}

/**
 * Vista mappa (OpenStreetMap via osmdroid) dei luoghi di un template: marker numerati nell'ordine
 * di viaggio, collegati da linee rette, con durata sulle linee e attesa sui luoghi dove è nota.
 * Nessun itinerario reale: le tratte di questo template possono essere treno/aereo/auto/a piedi, e
 * Maps/OSM non permettono di rappresentare un percorso con modalità di trasporto miste in un solo
 * tracciato — qui si mostra solo la sequenza geografica delle tappe, non le indicazioni per
 * raggiungerle.
 */
@Composable
fun TemplateMappaScreen(
    template: Template,
    tratte: List<Tratta>,
    luoghi: List<Luogo>,
    onChiudi: () -> Unit,
    padding: PaddingValues
) {
    val percorso = remember(template, tratte) { risolviPercorso(template, tratte) }
    MappaPercorsoScreen(
        titolo = template.nome.ifBlank { "Mappa template" },
        percorso = percorso,
        luoghi = luoghi,
        onChiudi = onChiudi,
        padding = padding
    )
}

/**
 * Stessa vista di [TemplateMappaScreen], per una singola tratta isolata (partenza + arrivo) aperta
 * dalla schermata Tratte: nessuna schermata/componente duplicata, solo un percorso diverso in
 * ingresso (vedi [risolviPercorsoTratta]).
 */
@Composable
fun TrattaMappaScreen(
    tratta: Tratta,
    luoghi: List<Luogo>,
    onChiudi: () -> Unit,
    padding: PaddingValues
) {
    val percorso = remember(tratta) { risolviPercorsoTratta(tratta) }
    MappaPercorsoScreen(
        titolo = tratta.nome.ifBlank { "Mappa tratta" },
        percorso = percorso,
        luoghi = luoghi,
        onChiudi = onChiudi,
        padding = padding
    )
}

@Composable
private fun MappaPercorsoScreen(
    titolo: String,
    percorso: PercorsoTemplate,
    luoghi: List<Luogo>,
    onChiudi: () -> Unit,
    padding: PaddingValues
) {
    val context = LocalContext.current
    var risoluzione by remember { mutableStateOf<RisoluzioneMappa?>(null) }

    LaunchedEffect(percorso, luoghi) {
        risoluzione = risolviMappa(context, percorso, luoghi)
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onChiudi) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Chiudi mappa")
            }
            Text(titolo, style = MaterialTheme.typography.titleMedium)
        }

        val esito = risoluzione
        when {
            esito == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            esito.tappe.isEmpty() -> MessaggioNessunaTappa()
            else -> {
                if (esito.senzaDati > 0 || esito.geocodingFallito > 0) {
                    BannerTappeEscluse(esito.senzaDati, esito.geocodingFallito)
                }
                MappaOsm(esito = esito, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun MessaggioNessunaTappa() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Nessun luogo di questo template ha coordinate GPS, e nessun indirizzo è risultato " +
                "geolocalizzabile automaticamente su questo dispositivo.\n\n" +
                "Aggiungi coordinate o un indirizzo ai luoghi usati da questo template (schermata " +
                "Luoghi) per poterli vedere qui.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BannerTappeEscluse(senzaDati: Int, geocodingFallito: Int) {
    val motivi = buildList {
        if (senzaDati > 0) add("$senzaDati senza indirizzo né coordinate")
        if (geocodingFallito > 0) add("$geocodingFallito con indirizzo non geolocalizzabile su questo dispositivo")
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Tappe non mostrate: " + motivi.joinToString(", ") + ".",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Soglia di zoom di osmdroid sotto la quale le etichette di durata/attesa vengono nascoste: a zoom
 * bassi (template su scala regionale/nazionale) i marker sono troppo vicini tra loro sullo schermo
 * e il testo diventerebbe illeggibile, sovrapposto. Sopra la soglia, le etichette compaiono anche
 * su uno sfondo semitrasparente (vedi [OverlayEtichette]) per restare leggibili sopra qualunque
 * tile di sfondo: le due misure insieme, non l'una in alternativa all'altra.
 */
private const val SOGLIA_ZOOM_ETICHETTE = 11.0

/**
 * Il MapView osmdroid, come qualunque View Android embeddata via AndroidView, va agganciata al
 * ciclo di vita (onResume/onPause) per non continuare a scaricare tile in background e non
 * perdere risorse quando la schermata è nascosta; onDetach() al rilascio libera la cache in RAM
 * dei tile di questa istanza.
 */
@Composable
private fun MappaOsm(esito: RisoluzioneMappa, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            configuraOsmdroid(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 2.0
                mapViewRef = this
            }
        },
        update = { mapView -> aggiornaOverlay(mapView, esito) },
        onRelease = { it.onDetach() }
    )
}

/**
 * Imposta uno User-Agent che identifica questa app (invece del default generico del client HTTP),
 * come richiesto dalla usage policy dei tile OpenStreetMap (https://operations.osmfoundation.org/policies/tiles/),
 * e sposta la cache dei tile nello storage privato dell'app: evita così di richiedere il permesso
 * di scrittura sullo storage esterno, non necessario dato minSdk 26.
 */
private fun configuraOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "${context.packageName}/${BuildConfig.VERSION_NAME}"
        osmdroidBasePath = context.cacheDir
        osmdroidTileCache = File(context.cacheDir, "osmdroid-tiles").apply { mkdirs() }
    }
}

private fun aggiornaOverlay(mapView: MapView, esito: RisoluzioneMappa) {
    mapView.overlays.clear()
    val tappe = esito.tappe
    val punti = tappe.map { GeoPoint(it.lat, it.lng) }

    if (punti.size >= 2) {
        mapView.overlays.add(
            Polyline(mapView).apply {
                setPoints(punti)
                outlinePaint.color = Color.parseColor("#1976D2")
                outlinePaint.strokeWidth = 6f
            }
        )
    }

    tappe.forEachIndexed { indice, tappa ->
        mapView.overlays.add(
            Marker(mapView).apply {
                position = punti[indice]
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "${tappa.numero}. ${tappa.luogo.nome}"
                icon = iconaMarkerNumerato(mapView.context, tappa.numero, tappa.luogo.colore)
            }
        )
    }

    val etichetteSegmento = esito.segmenti.mapNotNull { segmento ->
        // tipoTratta è null solo per un "salto" (nessuna tratta diretta): niente da etichettare.
        val tipo = segmento.tipoTratta ?: return@mapNotNull null
        val testoDurata = segmento.durataMinuti?.let { minuti ->
            (if (segmento.durataIndicativa) "~" else "") + formattaDurataMinuti(minuti)
        }
        EtichettaSegmento(segmento.da, segmento.a, tipo, testoDurata)
    }
    val etichetteNodo = tappe.mapNotNull { tappa ->
        tappa.attesaMinuti?.let { minuti -> GeoPoint(tappa.lat, tappa.lng) to "Attesa ${formattaDurataMinuti(minuti)}" }
    }
    if (etichetteSegmento.isNotEmpty() || etichetteNodo.isNotEmpty()) {
        mapView.overlays.add(OverlayEtichette(etichetteSegmento, etichetteNodo))
    }

    mapView.invalidate()
    mapView.post {
        when {
            punti.size == 1 -> {
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(punti.first())
            }
            punti.size >= 2 -> mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(punti), false, 128)
        }
    }
}

/**
 * Overlay unico per le etichette di durata (sui segmenti) e attesa (sui nodi): un `Overlay`
 * osmdroid ridisegna ad ogni frame, quindi legge lo zoom corrente direttamente in `draw()` invece
 * di dover propagare lo stato dello zoom fino a Compose solo per nascondere/mostrare il testo.
 * Sotto [SOGLIA_ZOOM_ETICHETTE] non disegna nulla; sopra, disegna ogni etichetta su un fondino
 * semitrasparente per restare leggibile sopra qualunque tile.
 *
 * L'etichetta di un arco NON è ancorata al punto medio geografico del segmento: su un arco lungo
 * (es. Milano-Roma) il punto medio è quasi sempre fuori dalla porzione di mappa effettivamente
 * visibile una volta superata la soglia di zoom, rendendo l'etichetta di fatto irraggiungibile
 * senza spostarsi esattamente lì. Si ritaglia invece il segmento contro il rettangolo dello
 * schermo (Cohen-Sutherland, in coordinate pixel) e si etichetta il punto medio della sola parte
 * visibile: l'etichetta resta raggiungibile ovunque ci si trovi lungo la linea, e sparisce solo
 * quando l'intera linea è fuori dallo schermo (nulla da etichettare).
 */
private class OverlayEtichette(
    private val etichetteSegmento: List<EtichettaSegmento>,
    private val etichetteNodo: List<Pair<GeoPoint, String>>
) : Overlay() {
    private val paintSfondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
    private val paintTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val paintIconaRiempita = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val paintIconaTratto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || mapView.zoomLevelDouble < SOGLIA_ZOOM_ETICHETTE) return
        val densita = mapView.context.resources.displayMetrics.density
        paintTesto.textSize = 12f * densita
        paintIconaTratto.strokeWidth = 1.6f * densita
        val proiezione = mapView.projection
        val larghezza = mapView.width.toFloat()
        val altezza = mapView.height.toFloat()
        val p0 = Point()
        val p1 = Point()

        etichetteSegmento.forEach { etichetta ->
            proiezione.toPixels(etichetta.da, p0)
            proiezione.toPixels(etichetta.a, p1)
            val visibile = clipSegmentoAlViewport(p0.x.toFloat(), p0.y.toFloat(), p1.x.toFloat(), p1.y.toFloat(), larghezza, altezza)
            if (visibile != null) {
                disegnaEtichettaSegmento(canvas, (visibile[0] + visibile[2]) / 2, (visibile[1] + visibile[3]) / 2, etichetta.tipo, etichetta.testoDurata)
            }
        }
        val offsetNodo = 34f * densita
        etichetteNodo.forEach { (geo, testo) ->
            proiezione.toPixels(geo, p0)
            disegnaEtichettaTesto(canvas, p0.x.toFloat(), p0.y.toFloat() + offsetNodo, testo)
        }
    }

    /** Etichetta di un nodo (attesa): solo testo su fondino, come prima. */
    private fun disegnaEtichettaTesto(canvas: Canvas, cx: Float, cy: Float, testo: String) {
        val larghezzaTesto = paintTesto.measureText(testo)
        val padding = paintTesto.textSize * 0.4f
        val rect = RectF(
            cx - larghezzaTesto / 2 - padding,
            cy - paintTesto.textSize / 2 - padding / 2,
            cx + larghezzaTesto / 2 + padding,
            cy + paintTesto.textSize / 2 + padding / 2
        )
        canvas.drawRoundRect(rect, padding, padding, paintSfondo)
        canvas.drawText(testo, cx, cy + paintTesto.textSize / 3, paintTesto)
    }

    /**
     * Etichetta di un arco: icona del tipo sempre presente, testo della durata accanto solo se
     * nota (vedi [EtichettaSegmento]) — mai un arco muto quando conosciamo almeno il tipo.
     */
    private fun disegnaEtichettaSegmento(canvas: Canvas, cx: Float, cy: Float, tipo: TipoTratta, testoDurata: String?) {
        val dimensioneIcona = paintTesto.textSize
        val larghezzaTesto = testoDurata?.let { paintTesto.measureText(it) } ?: 0f
        val spazioIconaTesto = if (testoDurata != null) dimensioneIcona * 0.35f else 0f
        val padding = paintTesto.textSize * 0.4f
        val larghezzaContenuto = dimensioneIcona + spazioIconaTesto + larghezzaTesto
        val altezzaPill = dimensioneIcona + padding

        val rect = RectF(
            cx - larghezzaContenuto / 2 - padding,
            cy - altezzaPill / 2,
            cx + larghezzaContenuto / 2 + padding,
            cy + altezzaPill / 2
        )
        canvas.drawRoundRect(rect, padding, padding, paintSfondo)

        val centroIconaX = rect.left + padding + dimensioneIcona / 2f
        disegnaIconaTipo(canvas, centroIconaX, cy, dimensioneIcona / 2f, tipo)

        if (testoDurata != null) {
            val centroTestoX = centroIconaX + dimensioneIcona / 2f + spazioIconaTesto + larghezzaTesto / 2f
            canvas.drawText(testoDurata, centroTestoX, cy + paintTesto.textSize / 3, paintTesto)
        }
    }

    /**
     * Pittogrammi minimali disegnati a mano (non è possibile rasterizzare un'`ImageVector` di
     * Compose — come [androidx.compose.material.icons.Icons.Filled.Train] già usata in
     * `TratteScreen` — fuori dall'albero di composizione, su un `Canvas` nativo osmdroid): stesso
     * soggetto delle icone Material già in uso altrove nell'app per ciascun tipo, semplificato per
     * la dimensione ridotta.
     */
    private fun disegnaIconaTipo(canvas: Canvas, cx: Float, cy: Float, r: Float, tipo: TipoTratta) {
        when (tipo) {
            TipoTratta.TRENO -> {
                val corpo = RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.5f)
                canvas.drawRoundRect(corpo, r * 0.3f, r * 0.3f, paintIconaRiempita)
                canvas.drawCircle(cx - r * 0.4f, cy - r * 0.2f, r * 0.28f, paintSfondo)
                canvas.drawCircle(cx + r * 0.4f, cy - r * 0.2f, r * 0.28f, paintSfondo)
                canvas.drawCircle(cx - r * 0.5f, cy + r * 0.65f, r * 0.18f, paintIconaRiempita)
                canvas.drawCircle(cx + r * 0.5f, cy + r * 0.65f, r * 0.18f, paintIconaRiempita)
            }
            TipoTratta.AEREO -> {
                val path = Path().apply {
                    moveTo(cx + r, cy)
                    lineTo(cx - r * 0.6f, cy - r * 0.75f)
                    lineTo(cx - r * 0.15f, cy)
                    lineTo(cx - r * 0.6f, cy + r * 0.75f)
                    close()
                }
                canvas.drawPath(path, paintIconaRiempita)
            }
            TipoTratta.AUTO -> {
                val corpo = RectF(cx - r, cy - r * 0.15f, cx + r, cy + r * 0.55f)
                canvas.drawRoundRect(corpo, r * 0.25f, r * 0.25f, paintIconaRiempita)
                val cabina = RectF(cx - r * 0.5f, cy - r * 0.75f, cx + r * 0.5f, cy - r * 0.05f)
                canvas.drawRoundRect(cabina, r * 0.2f, r * 0.2f, paintIconaRiempita)
                canvas.drawCircle(cx - r * 0.55f, cy + r * 0.6f, r * 0.2f, paintIconaRiempita)
                canvas.drawCircle(cx + r * 0.55f, cy + r * 0.6f, r * 0.2f, paintIconaRiempita)
            }
            TipoTratta.RIUNIONE -> {
                canvas.drawCircle(cx - r * 0.42f, cy, r * 0.5f, paintIconaRiempita)
                canvas.drawCircle(cx + r * 0.42f, cy, r * 0.5f, paintIconaRiempita)
            }
            TipoTratta.A_PIEDI -> {
                canvas.drawCircle(cx, cy - r * 0.7f, r * 0.22f, paintIconaRiempita)
                canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.15f, paintIconaTratto)
                canvas.drawLine(cx, cy + r * 0.15f, cx - r * 0.45f, cy + r * 0.75f, paintIconaTratto)
                canvas.drawLine(cx, cy + r * 0.15f, cx + r * 0.5f, cy + r * 0.55f, paintIconaTratto)
                canvas.drawLine(cx, cy - r * 0.2f, cx + r * 0.45f, cy - r * 0.5f, paintIconaTratto)
            }
        }
    }
}

/**
 * Ritaglio di Cohen-Sutherland del segmento (x0,y0)-(x1,y1) contro il rettangolo [0,larghezza] x
 * [0,altezza]: ritorna gli estremi della sola porzione visibile, o null se il segmento è
 * interamente fuori. Limitato a 8 iterazioni per non poter mai girare all'infinito (bastano al
 * più 4 per un rettangolo), anche in un ipotetico caso limite numerico.
 */
private fun clipSegmentoAlViewport(x0: Float, y0: Float, x1: Float, y1: Float, larghezza: Float, altezza: Float): FloatArray? {
    val sinistra = 1; val destra = 2; val alto = 4; val basso = 8
    fun codice(x: Float, y: Float): Int {
        var c = 0
        if (x < 0) c = c or sinistra else if (x > larghezza) c = c or destra
        if (y < 0) c = c or alto else if (y > altezza) c = c or basso
        return c
    }
    var ax = x0; var ay = y0; var bx = x1; var by = y1
    var codiceA = codice(ax, ay)
    var codiceB = codice(bx, by)
    repeat(8) {
        if (codiceA or codiceB == 0) return floatArrayOf(ax, ay, bx, by)
        if (codiceA and codiceB != 0) return null
        val fuori = if (codiceA != 0) codiceA else codiceB
        var x = 0f
        var y = 0f
        when {
            fuori and alto != 0 -> { x = ax + (bx - ax) * (0 - ay) / (by - ay); y = 0f }
            fuori and basso != 0 -> { x = ax + (bx - ax) * (altezza - ay) / (by - ay); y = altezza }
            fuori and destra != 0 -> { y = ay + (by - ay) * (larghezza - ax) / (bx - ax); x = larghezza }
            fuori and sinistra != 0 -> { y = ay + (by - ay) * (0 - ax) / (bx - ax); x = 0f }
        }
        if (fuori == codiceA) { ax = x; ay = y; codiceA = codice(ax, ay) } else { bx = x; by = y; codiceB = codice(bx, by) }
    }
    return null
}

/**
 * Disegna un piccolo pin circolare con il numero della tappa, per i marker ordinati sulla mappa.
 * [coloreLuogo] è il colore ARGB scelto dall'utente per questo Luogo (stesso campo usato per lo
 * sfondo della sua card in libreria); se assente usa il rosso di default.
 */
private fun iconaMarkerNumerato(context: Context, numero: Int, coloreLuogo: Int?): Drawable {
    val diametro = (32 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(diametro, diametro, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val raggio = diametro / 2f

    val paintCerchio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coloreLuogo ?: Color.parseColor("#D32F2F") }
    canvas.drawCircle(raggio, raggio, raggio - 2f, paintCerchio)

    val paintBordo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(raggio, raggio, raggio - 2f, paintBordo)

    val paintTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = diametro * 0.5f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val y = raggio - (paintTesto.descent() + paintTesto.ascent()) / 2
    canvas.drawText(numero.toString(), raggio, y, paintTesto)

    return BitmapDrawable(context.resources, bitmap)
}
