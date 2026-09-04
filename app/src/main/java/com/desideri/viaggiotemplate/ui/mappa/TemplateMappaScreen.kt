package com.desideri.viaggiotemplate.ui.mappa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.desideri.viaggiotemplate.BuildConfig
import com.desideri.viaggiotemplate.R
import com.desideri.viaggiotemplate.domain.mappa.PercorsoTemplate
import com.desideri.viaggiotemplate.domain.mappa.PosizioneAttualeEsecuzione
import com.desideri.viaggiotemplate.domain.mappa.RisultatoPosizioneAttuale
import com.desideri.viaggiotemplate.domain.mappa.calcolaPosizioneAttuale
import com.desideri.viaggiotemplate.domain.mappa.risolviPercorso
import com.desideri.viaggiotemplate.domain.mappa.risolviPercorsoTratta
import com.desideri.viaggiotemplate.domain.mappa.risolviPercorsoEsecuzione
import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.domain.log.EsitoRegistro
import com.desideri.viaggiotemplate.domain.model.IconaLuogo
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Tratta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.time.ZonedDateTime
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
        val inizioMisurazione = System.currentTimeMillis()
        val risultato = try {
            geocoder.getFromLocationName(indirizzo, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            AttivitaLogger.integrazione(
                descrizione = "Geocoding: \"$indirizzo\"",
                esito = EsitoRegistro.ERRORE,
                durataMs = System.currentTimeMillis() - inizioMisurazione,
                dettaglioErrore = e.message
            )
            return@withContext null
        }
        AttivitaLogger.integrazione(
            descrizione = "Geocoding: \"$indirizzo\"",
            esito = if (risultato != null) EsitoRegistro.SUCCESSO else EsitoRegistro.ERRORE,
            durataMs = System.currentTimeMillis() - inizioMisurazione,
            dettaglioErrore = if (risultato == null) "nessun risultato" else null
        )
        risultato
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

/**
 * Vista mappa per un'esecuzione già scritta a calendario ("Eventi creati"): stessa vista di
 * [TemplateMappaScreen], ma il percorso arriva da [risolviPercorsoEsecuzione] — posizioni
 * congelate al momento della scrittura, mai dai Luoghi/Tratte live (vedi lì il perché) — con
 * durate e attese calcolate sugli orari REALI degli eventi invece che stimate.
 *
 * [eventiOrdinati] devono essere ordinati per orario di inizio reale, come già li restituisce
 * [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter.eventiPerId].
 */
/** Intervallo di ricalcolo di [PosizioneAttualeEsecuzione]: un aggiornamento più frequente non porterebbe beneficio percepibile (l'indicatore mostra comunque una stima, non un tracciamento in tempo reale) e consumerebbe batteria inutilmente mentre la mappa resta aperta. */
private const val INTERVALLO_AGGIORNAMENTO_POSIZIONE_MS = 30_000L

@Composable
fun EsecuzioneMappaScreen(
    titolo: String,
    eventiOrdinati: List<EventoCreato>,
    posizioni: List<PosizioneEventoCreato>,
    onChiudi: () -> Unit,
    padding: PaddingValues
) {
    val (percorso, luoghi) = remember(eventiOrdinati, posizioni) { risolviPercorsoEsecuzione(eventiOrdinati, posizioni) }

    // "adesso" si aggiorna a bassa frequenza SOLO mentre la schermata è effettivamente visibile
    // (RESUME/PAUSE del lifecycle, come già fa MappaOsm per il MapView sotto): il loop nel
    // LaunchedEffect è annidato dentro la sua stessa key, quindi si ferma automaticamente alla
    // ricomposizione quando `schermataVisibile` torna false, senza bisogno di cancellarlo a mano.
    val lifecycleOwner = LocalLifecycleOwner.current
    var schermataVisibile by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> schermataVisibile = true
                Lifecycle.Event.ON_PAUSE -> schermataVisibile = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var adesso by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(schermataVisibile) {
        while (schermataVisibile) {
            adesso = ZonedDateTime.now()
            delay(INTERVALLO_AGGIORNAMENTO_POSIZIONE_MS)
        }
    }
    // Ricalcolato E loggato insieme, nello stesso LaunchedEffect: il registro deve riportare
    // esattamente il motivo di OGNI ricalcolo (anche quando il risultato non cambia rispetto al
    // precedente, es. "ancora fuori finestra" per tutta la sessione), non solo le transizioni -
    // per questo la chiave è (eventiOrdinati, posizioni, adesso) e non il risultato stesso: un
    // LaunchedEffect(risultato) non si riavvierebbe tra due esiti uguali per struttura (stesso
    // motivo), lasciando il registro silenzioso proprio nel caso più comune da diagnosticare
    // ("perché non vedo mai l'indicatore, nemmeno dopo un po'").
    var posizioneAttuale by remember { mutableStateOf<PosizioneAttualeEsecuzione?>(null) }
    LaunchedEffect(eventiOrdinati, posizioni, adesso) {
        val risultato = calcolaPosizioneAttuale(eventiOrdinati, posizioni, adesso)
        posizioneAttuale = (risultato as? RisultatoPosizioneAttuale.Trovata)?.posizione
        val finestraInizio = eventiOrdinati.firstOrNull()?.inizio
        val finestraFine = eventiOrdinati.lastOrNull()?.fine
        val esito = when (risultato) {
            is RisultatoPosizioneAttuale.Trovata -> "trovata (${risultato.posizione})"
            is RisultatoPosizioneAttuale.NonDisponibile -> "non disponibile - ${risultato.motivo}"
        }
        // AZIONE_UTENTE invece di ERRORE: "non disponibile" è l'esito normale per la maggior parte
        // della vita di un'esecuzione (fuori dalla finestra del viaggio), non un guasto - taggarlo
        // come errore lo farebbe apparire in rosso/allarmante nel registro ad ogni ricalcolo.
        AttivitaLogger.azioneUtente(
            "Mappa esecuzione, posizione teorica: adesso=$adesso, finestra esecuzione=$finestraInizio..$finestraFine: $esito"
        )
    }

    MappaPercorsoScreen(
        titolo = titolo,
        percorso = percorso,
        luoghi = luoghi,
        onChiudi = onChiudi,
        padding = padding,
        messaggioNessunaTappa = "Nessuna posizione disponibile per questi eventi: probabilmente sono stati creati con " +
            "una versione dell'app precedente all'introduzione di questa mappa, oppure i luoghi usati non avevano " +
            "indirizzo né coordinate GPS al momento della creazione.",
        notaInformativa = "Le posizioni mostrate sono quelle salvate al momento della creazione: modifiche successive " +
            "a Luoghi o Tratte non le cambiano. Gli eventi creati prima dell'introduzione di questa mappa potrebbero " +
            "non avere alcuna posizione disponibile.",
        posizioneAttuale = posizioneAttuale
    )
}

/** Testo di [MessaggioNessunaTappa] quando il percorso viene da un Template/Tratta della libreria (luoghi live, sempre correggibili in Luoghi). */
private const val MESSAGGIO_NESSUNA_TAPPA_LIBRERIA =
    "Nessun luogo di questo template ha coordinate GPS, e nessun indirizzo è risultato " +
        "geolocalizzabile automaticamente su questo dispositivo.\n\n" +
        "Aggiungi coordinate o un indirizzo ai luoghi usati da questo template (schermata " +
        "Luoghi) per poterli vedere qui."

@Composable
private fun MappaPercorsoScreen(
    titolo: String,
    percorso: PercorsoTemplate,
    luoghi: List<Luogo>,
    onChiudi: () -> Unit,
    padding: PaddingValues,
    messaggioNessunaTappa: String = MESSAGGIO_NESSUNA_TAPPA_LIBRERIA,
    notaInformativa: String? = null,
    posizioneAttuale: PosizioneAttualeEsecuzione? = null
) {
    val context = LocalContext.current
    var risoluzione by remember { mutableStateOf<RisoluzioneMappa?>(null) }
    // Riferimento alla MapView sottostante, popolato da MappaOsm alla creazione: serve al
    // pulsante "vai alla posizione teorica" per comandare centro/zoom dall'esterno, dato che
    // MappaOsm la tiene altrimenti solo in uno stato locale a se' non raggiungibile da qui.
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(percorso, luoghi) {
        risoluzione = risolviMappa(context, percorso, luoghi)
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onChiudi) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Chiudi mappa")
            }
            Text(titolo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            // Visibile solo quando esiste davvero una posizione teorica da raggiungere: senza,
            // sarebbe un pulsante morto (nessun posto dove andare).
            if (posizioneAttuale != null) {
                IconButton(onClick = {
                    mapViewRef?.let { mapView ->
                        mapView.controller.setZoom(ZOOM_POSIZIONE_ATTUALE)
                        mapView.controller.animateTo(GeoPoint(posizioneAttuale.lat, posizioneAttuale.lng))
                    }
                }) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Vai alla posizione teorica")
                }
            }
        }

        val esito = risoluzione
        when {
            esito == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            esito.tappe.isEmpty() -> MessaggioNessunaTappa(messaggioNessunaTappa)
            else -> {
                notaInformativa?.let { BannerMappa(it) }
                if (esito.senzaDati > 0 || esito.geocodingFallito > 0) {
                    BannerMappa("Tappe non mostrate: " + descriviTappeEscluse(esito.senzaDati, esito.geocodingFallito) + ".")
                }
                MappaOsm(
                    esito = esito,
                    posizioneAttuale = posizioneAttuale,
                    onMapReady = { mapViewRef = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

/**
 * Zoom applicato dal pulsante "vai alla posizione teorica": più ravvicinato dei 15.0 usati per
 * centrare un'unica tappa in [aggiornaOverlay] - qui l'utente ha premuto un pulsante apposta per
 * vedere da vicino dove si troverebbe adesso, non solo per orientarsi sull'insieme del percorso.
 */
private const val ZOOM_POSIZIONE_ATTUALE = 16.0

@Composable
private fun MessaggioNessunaTappa(messaggio: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(messaggio, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun descriviTappeEscluse(senzaDati: Int, geocodingFallito: Int): String = buildList {
    if (senzaDati > 0) add("$senzaDati senza indirizzo né coordinate")
    if (geocodingFallito > 0) add("$geocodingFallito con indirizzo non geolocalizzabile su questo dispositivo")
}.joinToString(", ")

@Composable
private fun BannerMappa(testo: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            testo,
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
private fun MappaOsm(
    esito: RisoluzioneMappa,
    posizioneAttuale: PosizioneAttualeEsecuzione?,
    modifier: Modifier,
    onMapReady: (MapView) -> Unit = {}
) {
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
                onMapReady(this)
            }
        },
        update = { mapView -> aggiornaOverlay(mapView, esito, posizioneAttuale) },
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

/**
 * Stesso path delle icone Material già usate in `TratteScreen` (Train/Flight/DirectionsCar/
 * Groups/DirectionsWalk), trascritto in risorse VectorDrawable (vedi `res/drawable/ic_tipo_*.xml`)
 * per poterle disegnare con `setBounds()`+`draw(canvas)` sul Canvas nativo osmdroid: un'ImageVector
 * di Compose non è rasterizzabile fuori dall'albero di composizione. `ContextCompat.getDrawable`
 * basta (nessuna dipendenza da AppCompat): minSdk 26 è ben sopra l'API 21 in cui VectorDrawable è
 * diventato un tipo nativo della piattaforma.
 */
private fun iconePerTipo(context: Context): Map<TipoTratta, Drawable> = TipoTratta.values().associateWith { tipo ->
    val resId = when (tipo) {
        TipoTratta.TRENO -> R.drawable.ic_tipo_treno
        TipoTratta.AEREO -> R.drawable.ic_tipo_aereo
        TipoTratta.AUTO -> R.drawable.ic_tipo_auto
        TipoTratta.RIUNIONE -> R.drawable.ic_tipo_riunione
        TipoTratta.A_PIEDI -> R.drawable.ic_tipo_a_piedi
    }
    requireNotNull(ContextCompat.getDrawable(context, resId)) { "Icona mancante per $tipo" }
}

/**
 * Stesso principio di [iconePerTipo] ma per [IconaLuogo]: risorse VectorDrawable in
 * `res/drawable/ic_luogo_*.xml`, usate come glifo del marker numerato (vedi
 * [iconaMarkerNumerato]) al posto del numero quando il Luogo ha un'icona assegnata.
 */
private fun iconePerLuogo(context: Context): Map<IconaLuogo, Drawable> = IconaLuogo.values().associateWith { icona ->
    val resId = when (icona) {
        IconaLuogo.UFFICIO -> R.drawable.ic_luogo_ufficio
        IconaLuogo.CASA -> R.drawable.ic_luogo_casa
        IconaLuogo.STAZIONE -> R.drawable.ic_luogo_stazione
        IconaLuogo.AEROPORTO -> R.drawable.ic_luogo_aeroporto
        IconaLuogo.EDIFICIO -> R.drawable.ic_luogo_edificio
        IconaLuogo.PARCHEGGIO -> R.drawable.ic_luogo_parcheggio
        IconaLuogo.SPORT -> R.drawable.ic_luogo_sport
    }
    requireNotNull(ContextCompat.getDrawable(context, resId)) { "Icona mancante per $icona" }
}

private fun aggiornaOverlay(mapView: MapView, esito: RisoluzioneMappa, posizioneAttuale: PosizioneAttualeEsecuzione?) {
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

    val iconeLuogo = iconePerLuogo(mapView.context)
    tappe.forEachIndexed { indice, tappa ->
        mapView.overlays.add(
            Marker(mapView).apply {
                position = punti[indice]
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "${tappa.numero}. ${tappa.luogo.nome}"
                icon = iconaMarkerNumerato(mapView.context, tappa.numero, tappa.luogo.colore, tappa.luogo.icona?.let { iconeLuogo.getValue(it) })
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
        mapView.overlays.add(OverlayEtichette(etichetteSegmento, etichetteNodo, iconePerTipo(mapView.context)))
    }

    // In osmdroid l'ordine di overlays determina la sovrapposizione a schermo (il primo aggiunto
    // sta sotto, l'ultimo sta sopra): questo blocco va tenuto per ULTIMO in aggiornaOverlay,
    // dopo polilinea/pin dei Luoghi/etichette, cosi' l'indicatore resta sempre in cima a tutto.
    // aggiornaOverlay fa mapView.overlays.clear() e ricostruisce l'intera lista da zero ad ogni
    // chiamata (compresa ognuna delle chiamate periodiche ogni 30s, vedi INTERVALLO_AGGIORNAMENTO_
    // POSIZIONE_MS in EsecuzioneMappaScreen): l'ordine non puo' "scivolare" nel tempo, e' sempre
    // ricreato uguale.
    posizioneAttuale?.let { posizione ->
        val puntoAttuale = GeoPoint(posizione.lat, posizione.lng)
        val descrizione = when (posizione) {
            is PosizioneAttualeEsecuzione.SuSegmento -> "In viaggio su questa tratta, secondo l'orario pianificato."
            is PosizioneAttualeEsecuzione.SuLuogo -> "In attesa qui, secondo l'orario pianificato."
        }
        mapView.overlays.add(
            Marker(mapView).apply {
                position = puntoAttuale
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Posizione teorica (stimata dagli orari)"
                snippet = "$descrizione Non è la tua posizione GPS reale."
                icon = iconaPosizioneAttuale(mapView.context)
            }
        )
        mapView.overlays.add(OverlayPosizioneAttuale(puntoAttuale))
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
    private val etichetteNodo: List<Pair<GeoPoint, String>>,
    private val iconePerTipo: Map<TipoTratta, Drawable>
) : Overlay() {
    private val paintSfondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
    private val paintTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || mapView.zoomLevelDouble < SOGLIA_ZOOM_ETICHETTE) return
        val densita = mapView.context.resources.displayMetrics.density
        paintTesto.textSize = 12f * densita
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
                disegnaEtichettaSegmento(canvas, (visibile[0] + visibile[2]) / 2, (visibile[1] + visibile[3]) / 2, etichetta.tipo, etichetta.testoDurata, densita)
            }
        }
        val offsetNodo = 34f * densita
        etichetteNodo.forEach { (geo, testo) ->
            proiezione.toPixels(geo, p0)
            disegnaEtichettaTesto(canvas, p0.x.toFloat(), p0.y.toFloat() + offsetNodo, testo)
        }
    }

    /** Etichetta di un nodo (attesa): solo testo su fondino. */
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
     *
     * L'icona è disegnata più grande del testo (1.5x l'altezza del carattere): alla dimensione del
     * testo i dettagli fini delle icone Material (le due finestre del treno, le due ruote
     * dell'auto) si perderebbero a schermo piccolo — vedi `res/drawable/ic_tipo_*.xml`.
     */
    private fun disegnaEtichettaSegmento(canvas: Canvas, cx: Float, cy: Float, tipo: TipoTratta, testoDurata: String?, densita: Float) {
        val dimensioneIcona = paintTesto.textSize * 1.5f
        val larghezzaTesto = testoDurata?.let { paintTesto.measureText(it) } ?: 0f
        val spazioIconaTesto = if (testoDurata != null) 5f * densita else 0f
        val padding = 5f * densita
        val larghezzaContenuto = dimensioneIcona + spazioIconaTesto + larghezzaTesto
        val altezzaPill = dimensioneIcona + padding

        val rect = RectF(
            cx - larghezzaContenuto / 2 - padding,
            cy - altezzaPill / 2,
            cx + larghezzaContenuto / 2 + padding,
            cy + altezzaPill / 2
        )
        canvas.drawRoundRect(rect, padding, padding, paintSfondo)

        val iconaLeft = rect.left + padding
        iconePerTipo[tipo]?.apply {
            setBounds(
                iconaLeft.toInt(),
                (cy - dimensioneIcona / 2f).toInt(),
                (iconaLeft + dimensioneIcona).toInt(),
                (cy + dimensioneIcona / 2f).toInt()
            )
            draw(canvas)
        }

        if (testoDurata != null) {
            val centroTestoX = iconaLeft + dimensioneIcona + spazioIconaTesto + larghezzaTesto / 2f
            canvas.drawText(testoDurata, centroTestoX, cy + paintTesto.textSize / 3, paintTesto)
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
 * Disegna un piccolo pin circolare per i marker ordinati sulla mappa. [coloreLuogo] è il colore
 * ARGB scelto dall'utente per questo Luogo (stesso campo usato per lo sfondo della sua card in
 * libreria); se assente usa il rosso di default.
 *
 * [iconaLuogo] è il glifo di [IconaLuogo] già risolto in [Drawable] da [iconePerLuogo]: quando
 * presente sostituisce il numero della tappa. Il numero resta il fallback (Luogo senza icona
 * assegnata, il caso comune finché l'utente non ne sceglie una): è l'informazione più importante
 * per un percorso con più tappe — l'ordine di visita — e non va mai persa in favore di un'icona
 * puramente decorativa.
 */
private fun iconaMarkerNumerato(context: Context, numero: Int, coloreLuogo: Int?, iconaLuogo: Drawable?): Drawable {
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

    if (iconaLuogo != null) {
        val dimensioneIcona = (diametro * 0.55f).toInt()
        val offset = ((diametro - dimensioneIcona) / 2f).toInt()
        iconaLuogo.setBounds(offset, offset, offset + dimensioneIcona, offset + dimensioneIcona)
        iconaLuogo.draw(canvas)
    } else {
        val paintTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = diametro * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = raggio - (paintTesto.descent() + paintTesto.ascent()) / 2
        canvas.drawText(numero.toString(), raggio, y, paintTesto)
    }

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Colore vivo e saturo (mai usato dalle tile Mapnik - niente strade, acqua o terreno è viola) per
 * spiccare nettamente sullo sfondo mappa: il grigio neutro usato in origine si perdeva contro le
 * tile OSM, spesso anch'esse sui grigi/beige chiari, ed era la ragione principale per cui
 * l'indicatore risultava difficile da individuare anche quando effettivamente presente.
 */
private const val COLORE_POSIZIONE_ATTUALE = "#D500F9"

/** Diametro dell'indicatore di posizione teorica: più grande dei 32dp di [iconaMarkerNumerato] (i pin dei Luoghi), non solo diverso nel colore - deve risaltare anche per dimensione. */
private const val DIAMETRO_POSIZIONE_ATTUALE_DP = 46

/**
 * Marker dell'indicatore di posizione teorica (vedi [PosizioneAttualeEsecuzione]): stessa forma
 * circolare di [iconaMarkerNumerato] ma deliberatamente diversa in più modi contemporaneamente, non
 * uno solo, per non lasciare dubbi che sia un'altra tappa dell'itinerario:
 * - colore viola acceso, mai usato per i pin dei Luoghi (colore del Luogo scelto dall'utente, o il
 *   rosso di default) né per nessun'altra tile/overlay di questa mappa;
 * - più grande dei pin dei Luoghi ([DIAMETRO_POSIZIONE_ATTUALE_DP] contro i 32dp di
 *   [iconaMarkerNumerato]);
 * - bordo TRATTEGGIATO invece che pieno (richiama l'idea di "stimato", non di un dato certo);
 * - un'icona di orologio al posto del numero progressivo o dell'icona del Luogo, a richiamare che
 *   il punto viene dagli ORARI pianificati, non da un rilevamento GPS.
 * Il testo esplicito che chiarisce "non è la tua posizione GPS reale" resta comunque nel titolo/
 * snippet del marker (mostrati al tocco) e nell'etichetta sempre visibile di [OverlayPosizioneAttuale].
 */
private fun iconaPosizioneAttuale(context: Context): Drawable {
    val densita = context.resources.displayMetrics.density
    val diametro = (DIAMETRO_POSIZIONE_ATTUALE_DP * densita).toInt().coerceAtLeast(34)
    val bitmap = Bitmap.createBitmap(diametro, diametro, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val raggio = diametro / 2f

    val paintCerchio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLORE_POSIZIONE_ATTUALE) }
    canvas.drawCircle(raggio, raggio, raggio - 3f, paintCerchio)

    val paintBordo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * densita
        pathEffect = DashPathEffect(floatArrayOf(4.5f * densita, 3f * densita), 0f)
    }
    canvas.drawCircle(raggio, raggio, raggio - 3f, paintBordo)

    val orologio = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_posizione_stimata)) { "Icona posizione stimata mancante" }
    val dimensioneIcona = (diametro * 0.55f).toInt()
    val offset = ((diametro - dimensioneIcona) / 2f).toInt()
    orologio.setBounds(offset, offset, offset + dimensioneIcona, offset + dimensioneIcona)
    orologio.draw(canvas)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Etichetta di testo SEMPRE visibile (a differenza di [OverlayEtichette], non è nascosta sotto
 * [SOGLIA_ZOOM_ETICHETTE]) accanto all'indicatore di posizione teorica: essendo un solo elemento
 * per mappa, non c'è rischio di affollamento come per le etichette di durata/attesa su percorsi con
 * molte tappe, ed è importante che il chiarimento "non è una posizione reale" resti leggibile a
 * colpo d'occhio, senza dover toccare il marker per aprirne il popup.
 */
private class OverlayPosizioneAttuale(private val punto: GeoPoint) : Overlay() {
    private val paintSfondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLORE_POSIZIONE_ATTUALE) }
    private val paintTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val densita = mapView.context.resources.displayMetrics.density
        paintTesto.textSize = 11f * densita
        val p = Point()
        mapView.projection.toPixels(punto, p)

        val testo = "posizione teorica"
        val larghezzaTesto = paintTesto.measureText(testo)
        val padding = paintTesto.textSize * 0.35f
        // Sopra il bordo superiore del marker (raggio di DIAMETRO_POSIZIONE_ATTUALE_DP) più un
        // margine: con l'indicatore ingrandito, il vecchio offset fisso lo avrebbe fatto finire
        // sovrapposto al marker invece che sopra di esso.
        val cy = p.y - (DIAMETRO_POSIZIONE_ATTUALE_DP / 2f + 12f) * densita
        val rect = RectF(
            p.x - larghezzaTesto / 2 - padding,
            cy - paintTesto.textSize / 2 - padding / 2,
            p.x + larghezzaTesto / 2 + padding,
            cy + paintTesto.textSize / 2 + padding / 2
        )
        canvas.drawRoundRect(rect, padding, padding, paintSfondo)
        canvas.drawText(testo, p.x.toFloat(), cy + paintTesto.textSize / 3, paintTesto)
    }
}
