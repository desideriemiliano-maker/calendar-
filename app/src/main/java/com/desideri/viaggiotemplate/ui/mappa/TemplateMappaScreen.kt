package com.desideri.viaggiotemplate.ui.mappa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.desideri.viaggiotemplate.domain.mappa.sequenzaIdLuoghi
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.Tratta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

/** Una tappa del template già geolocalizzata (coordinate dirette o geocodificate), pronta per il marker. */
private data class TappaGeolocalizzata(
    val numero: Int,
    val luogo: Luogo,
    val lat: Double,
    val lng: Double
)

/**
 * Esito della risoluzione delle tappe di un template: quali sono posizionabili su mappa e, per
 * onestà verso l'utente, quante e perché non lo sono (nessuna coordinata/indirizzo, oppure
 * indirizzo presente ma non geocodificabile su questo dispositivo).
 */
private data class RisoluzioneMappa(
    val tappe: List<TappaGeolocalizzata>,
    val senzaDati: Int,
    val geocodingFallito: Int
)

/**
 * Risolve la sequenza di luoghi del template in coordinate mostrabili su mappa. Per i luoghi con
 * coordinate GPS già salvate le usa direttamente; per quelli con solo un indirizzo tenta un
 * geocoding "best effort" con [Geocoder] (integrato in Android, gratuito, ma non garantito su
 * tutti i dispositivi — alcuni non hanno il servizio di geocoding di sistema disponibile). Se
 * anche questo fallisce, o se il luogo non ha né coordinate né indirizzo, la tappa viene esclusa e
 * conteggiata (mai un errore silenzioso: l'utente vede sempre quante tappe mancano e perché).
 */
private suspend fun risolviMappa(context: Context, template: Template, tratte: List<Tratta>, luoghi: List<Luogo>): RisoluzioneMappa {
    val luoghiPerId = luoghi.associateBy { it.id }
    val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

    val tappe = mutableListOf<TappaGeolocalizzata>()
    var senzaDati = 0
    var geocodingFallito = 0

    sequenzaIdLuoghi(template, tratte).forEach { id ->
        val luogo = luoghiPerId[id] ?: return@forEach
        val lat = luogo.latitudine
        val lng = luogo.longitudine
        when {
            lat != null && lng != null -> tappe += TappaGeolocalizzata(tappe.size + 1, luogo, lat, lng)
            !luogo.indirizzo.isNullOrBlank() -> {
                val geocodificato = geocodificaBestEffort(geocoder, luogo.indirizzo)
                if (geocodificato != null) {
                    tappe += TappaGeolocalizzata(tappe.size + 1, luogo, geocodificato.first, geocodificato.second)
                } else {
                    geocodingFallito++
                }
            }
            else -> senzaDati++
        }
    }
    return RisoluzioneMappa(tappe, senzaDati, geocodingFallito)
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

/**
 * Vista mappa (OpenStreetMap via osmdroid) dei luoghi di un template: marker numerati nell'ordine
 * di viaggio, collegati da linee rette. Nessun itinerario reale: le tratte di questo template
 * possono essere treno/aereo/auto/a piedi, e Maps/OSM non permettono di rappresentare un percorso
 * con modalità di trasporto miste in un solo tracciato — qui si mostra solo la sequenza geografica
 * delle tappe, non le indicazioni per raggiungerle.
 */
@Composable
fun TemplateMappaScreen(
    template: Template,
    tratte: List<Tratta>,
    luoghi: List<Luogo>,
    onChiudi: () -> Unit,
    padding: PaddingValues
) {
    val context = LocalContext.current
    var risoluzione by remember { mutableStateOf<RisoluzioneMappa?>(null) }

    LaunchedEffect(template.id, tratte, luoghi) {
        risoluzione = risolviMappa(context, template, tratte, luoghi)
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onChiudi) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Chiudi mappa")
            }
            Text(
                template.nome.ifBlank { "Mappa template" },
                style = MaterialTheme.typography.titleMedium
            )
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
                MappaOsm(tappe = esito.tappe, modifier = Modifier.fillMaxWidth().weight(1f))
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
 * Il MapView osmdroid, come qualunque View Android embeddata via AndroidView, va agganciata al
 * ciclo di vita (onResume/onPause) per non continuare a scaricare tile in background e non
 * perdere risorse quando la schermata è nascosta; onDetach() al rilascio libera la cache in RAM
 * dei tile di questa istanza.
 */
@Composable
private fun MappaOsm(tappe: List<TappaGeolocalizzata>, modifier: Modifier) {
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
        update = { mapView -> aggiornaOverlay(mapView, tappe) },
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

private fun aggiornaOverlay(mapView: MapView, tappe: List<TappaGeolocalizzata>) {
    mapView.overlays.clear()
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
                icon = iconaMarkerNumerato(mapView.context, tappa.numero)
            }
        )
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

/** Disegna un piccolo pin circolare con il numero della tappa, per i marker ordinati sulla mappa. */
private fun iconaMarkerNumerato(context: Context, numero: Int): Drawable {
    val diametro = (32 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(diametro, diametro, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val raggio = diametro / 2f

    val paintCerchio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") }
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
