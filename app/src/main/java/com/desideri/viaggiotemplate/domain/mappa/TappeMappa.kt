package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.calendar.EventoCreato
import com.desideri.viaggiotemplate.domain.calendar.LuogoCongelato
import com.desideri.viaggiotemplate.domain.calendar.PosizioneEventoCreato
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Un nodo (luogo) del percorso di un template sulla mappa.
 *
 * [attesaMinuti] è il `margineMinuti` (vedi [Tratta.margineMinuti]) della tratta che riparte da
 * qui: lo stesso identico valore che [com.desideri.viaggiotemplate.domain.calcolo.MotoreCalcolo]
 * usa come buffer minimo richiesto prima che quella tratta possa iniziare, calcolato rispetto alla
 * fine della tratta precedente — non un tempo di attesa "reale" ricalcolato da un'esecuzione (che
 * richiederebbe un'ancora con orario concreto, non disponibile per un template di libreria).
 *
 * È `null` quando questo nodo non è un vero snodo tra un arrivo e una partenza:
 * - è la prima tappa del template (nessuna tratta arriva qui);
 * - è l'ultima tappa (nessuna tratta riparte da qui);
 * - è un nodo creato da un "salto" geografico (arrivo e partenza consecutivi non coincidono):
 *   il margine esiste sempre sulla tratta in partenza, ma qui non descriverebbe un'attesa IN
 *   questo punto, bensì il buffer verso un luogo diverso da quello raggiunto in precedenza.
 */
data class NodoPercorso(
    val luogoId: String,
    val attesaMinuti: Int?
)

/**
 * Un arco tra due nodi consecutivi del percorso.
 *
 * [trattaId] è `null` quando l'arco rappresenta un "salto" tra due luoghi non contigui, senza una
 * tratta diretta a spiegarlo (nessun controllo nel resto dell'app impedisce a un template di avere
 * un salto: vedi [MotoreCalcolo], che propaga solo i tempi, non verifica i luoghi).
 *
 * [durataMinuti]: per AUTO/RIUNIONE/A_PIEDI è `durataMinutiReale`, un valore certo
 * ([durataIndicativa] resta `false`). Per TRENO/AEREO, [MotoreCalcolo] sceglie l'orario di uno
 * specifico giorno risolvendo un'ancora — ma la DURATA di ogni pattern configurato (differenza tra
 * arrivo e partenza di una [OpzioneOrario] o di un [OrarioFisso]) non dipende da quale ora del
 * giorno viene scelta, quindi è comunque calcolabile senza ancora: se tutti i pattern configurati
 * durano uguale, quel valore è certo; se durano diverso, [durataMinuti] è la loro media e
 * [durataIndicativa] è `true` (va mostrata come "circa", non come un dato esatto). Se la tratta non
 * ha alcun pattern configurato (né opzioni ricorrenti né orari fissi — lo stesso caso in cui
 * MotoreCalcolo usa un placeholder "da confermare"), non c'è proprio nulla su cui basarsi:
 * [durataMinuti] resta `null`.
 *
 * [tipoTratta] è il tipo della tratta di questo arco (`null` per un salto, che non ha una tratta):
 * serve a spiegare sull'arco stesso perché lì non compare una durata, quando [durataMinuti] è
 * `null` per mancanza di dati (non per un salto).
 */
data class ArcoPercorso(
    val trattaId: String?,
    val durataMinuti: Int?,
    val durataIndicativa: Boolean = false,
    val tipoTratta: TipoTratta? = null
)

/** Durata di un singolo pattern ricorrente, indipendente da quale ora del giorno verrà scelta. */
private fun durataMinuti(opzione: OpzioneOrario): Int =
    opzione.offsetOreArrivo * 60 + opzione.minutoArrivo - opzione.minutoPartenza

/** Durata di un orario fisso; se l'arrivo è prima della partenza si considera oltre mezzanotte. */
private fun durataMinuti(fisso: OrarioFisso): Int {
    val partenzaMin = fisso.partenza.hour * 60 + fisso.partenza.minute
    val arrivoMin = fisso.arrivo.hour * 60 + fisso.arrivo.minute
    return (if (arrivoMin < partenzaMin) arrivoMin + 1440 else arrivoMin) - partenzaMin
}

/**
 * Risolve durata/indicatività di una tratta per l'arco sulla mappa. Vedi il commento su
 * [ArcoPercorso.durataMinuti] per il ragionamento completo.
 */
private fun risolviDurataArco(tratta: Tratta): Pair<Int?, Boolean> {
    if (!tratta.tipo.usaOrariProgrammati) return tratta.durataMinutiReale to false
    val durate = tratta.opzioniOrario.map(::durataMinuti) + tratta.orariFissi.map(::durataMinuti)
    return when {
        durate.isEmpty() -> null to false
        durate.distinct().size == 1 -> durate.first() to false
        else -> (durate.sum() / durate.size) to true
    }
}

data class PercorsoTemplate(
    val nodi: List<NodoPercorso>,
    val archi: List<ArcoPercorso>
)

/**
 * Risolve il percorso geografico di un template: la sequenza di luoghi attraversati (partenza e
 * arrivo di ogni tratta selezionata, nell'ordine degli slot) con gli archi che li collegano.
 *
 * Le ripetizioni consecutive sono collassate in un solo nodo (il caso normale, in cui l'arrivo di
 * una tratta coincide con la partenza della successiva): vale anche per una tratta "puntiforme"
 * come una RIUNIONE, il cui luogo di arrivo coincide per costruzione con quello di partenza (vedi
 * `TrattaEditorScreen`) — non produce né un nodo proprio né un arco, resta un punto sul nodo
 * condiviso. Il margine della RIUNIONE stessa (buffer prima che possa iniziare) non è quindi
 * un'attesa mostrabile su un nodo distinto: è un limite noto di questa rappresentazione, non un bug.
 *
 * Uno slot la cui tratta selezionata non è (più) tra [tratte] viene saltato silenziosamente:
 * capita solo per un template salvato con un riferimento a una tratta poi eliminata dalla libreria.
 */
fun risolviPercorso(template: Template, tratte: List<Tratta>): PercorsoTemplate {
    val tratteById = tratte.associateBy { it.id }
    val sequenza = template.slotsOrdinati.mapNotNull { tratteById[it.trattaSelezionataId] }
    if (sequenza.isEmpty()) return PercorsoTemplate(emptyList(), emptyList())

    val nodi = mutableListOf(sequenza.first().luogoPartenzaId)
    val archi = mutableListOf<ArcoPercorso>()

    sequenza.forEach { tratta ->
        if (nodi.last() != tratta.luogoPartenzaId) {
            // Salto: la partenza di questa tratta non coincide con l'ultimo arrivo registrato.
            archi += ArcoPercorso(trattaId = null, durataMinuti = null)
            nodi += tratta.luogoPartenzaId
        }
        if (tratta.luogoArrivoId != tratta.luogoPartenzaId) {
            val (durata, indicativa) = risolviDurataArco(tratta)
            archi += ArcoPercorso(trattaId = tratta.id, durataMinuti = durata, durataIndicativa = indicativa, tipoTratta = tratta.tipo)
            nodi += tratta.luogoArrivoId
        }
        // Se arrivo == partenza (tratta puntiforme, es. RIUNIONE) non si aggiunge né nodo né arco:
        // il margine di questa tratta resta "assorbito" nel nodo condiviso, non mostrabile a parte.
    }

    val nodiConAttesa = nodi.mapIndexed { indice, luogoId ->
        val entrante = archi.getOrNull(indice - 1)
        val uscente = archi.getOrNull(indice)
        // Serve un vero arrivo (entrante) e una vera partenza (uscente) DA QUESTO nodo: se uno dei
        // due manca (prima/ultima tappa) o è un salto (trattaId null, nessuna tratta reale), non
        // c'è un'attesa "in questo punto" da mostrare.
        val attesa = if (entrante?.trattaId != null && uscente?.trattaId != null) {
            tratteById[uscente.trattaId]?.margineMinuti
        } else null
        NodoPercorso(luogoId, attesa)
    }

    return PercorsoTemplate(nodiConAttesa, archi)
}

/**
 * Risolve il percorso di una singola tratta isolata (partenza + arrivo), per la vista mappa
 * aperta dalla schermata Tratte anziché da un template. Costruisce un template sintetico di un
 * solo slot e riusa [risolviPercorso] invariato: stessa identica logica (inclusa la durata
 * certa/indicativa per TRENO/AEREO), senza duplicarla. L'attesa sui nodi resta sempre `null`
 * (corretto: fuori da un template non esiste una tratta precedente/successiva rispetto a cui
 * definire un margine).
 */
fun risolviPercorsoTratta(tratta: Tratta): PercorsoTemplate {
    val slotSintetico = TemplateSlot(
        id = tratta.id,
        ordine = 0,
        ancora = true,
        trattaCandidatiIds = listOf(tratta.id),
        trattaSelezionataId = tratta.id
    )
    val templateSintetico = Template(id = tratta.id, nome = tratta.nome, slots = listOf(slotSintetico))
    return risolviPercorso(templateSintetico, listOf(tratta))
}

/** Un evento con la sua posizione congelata già "spacchettata" (partenza/arrivo garantiti non null), pronto per entrare nella sequenza del percorso. */
private data class TappaEsecuzione(val evento: EventoCreato, val tipo: TipoTratta?, val partenza: LuogoCongelato, val arrivo: LuogoCongelato)

/**
 * Risolve il percorso geografico di un'esecuzione già scritta a calendario, dalle posizioni
 * congelate al momento della scrittura (vedi [PosizioneEventoCreato]) — mai dai Luoghi/Tratte
 * live, che potrebbero essere cambiati o eliminati da allora: stessa logica di
 * [risolviPercorso] (nodi/archi, collasso delle tappe coincidenti, "salti" quando due tappe
 * consecutive non coincidono), ma qui durata di ogni arco e attesa su ogni nodo sono quelle REALI
 * tra gli orari effettivi degli eventi (rilette dal Calendar Provider, vedi [EventoCreato]) invece
 * che una stima sui pattern configurati: l'esecuzione è già concreta, quindi il dato reale è
 * sempre disponibile ed è più accurato di qualunque stima.
 *
 * [eventiOrdinati] deve essere ordinato per orario di inizio reale (come già fa
 * [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter.eventiPerId]). Un evento la cui
 * posizione non è in [posizioni], o la cui posizione non è stata congelata (creato prima
 * dell'introduzione di questa funzionalità), viene saltato silenziosamente — esattamente come un
 * nodo intermedio senza dati geografici in [risolviMappa] — e produce un "salto" nel percorso
 * anziché un arco spiegato.
 *
 * Ritorna, insieme al percorso, l'elenco dei Luoghi (sintetici, ricostruiti dalle posizioni
 * congelate) su cui risolverlo: la stessa forma richiesta da [risolviMappa], per riusarlo
 * invariato.
 */
fun risolviPercorsoEsecuzione(eventiOrdinati: List<EventoCreato>, posizioni: List<PosizioneEventoCreato>): Pair<PercorsoTemplate, List<Luogo>> {
    val posizioniPerId = posizioni.associateBy { it.calendarEventId }
    val sequenza = eventiOrdinati.mapNotNull { evento ->
        val posizione = posizioniPerId[evento.eventoId] ?: return@mapNotNull null
        val partenza = posizione.partenza ?: return@mapNotNull null
        val arrivo = posizione.arrivo ?: return@mapNotNull null
        TappaEsecuzione(evento, posizione.tipoTratta, partenza, arrivo)
    }
    if (sequenza.isEmpty()) return PercorsoTemplate(emptyList(), emptyList()) to emptyList()

    val luoghi = LinkedHashMap<String, Luogo>()
    fun registra(luogo: LuogoCongelato) = luoghi.getOrPut(luogo.luogoId) {
        Luogo(
            id = luogo.luogoId,
            nome = luogo.nome,
            indirizzo = luogo.indirizzo,
            latitudine = luogo.latitudine,
            longitudine = luogo.longitudine,
            colore = luogo.colore,
            icona = luogo.icona
        )
    }

    val nodi = mutableListOf(sequenza.first().partenza.luogoId)
    registra(sequenza.first().partenza)
    val archi = mutableListOf<ArcoPercorso>()
    // Allineato indice per indice con `archi`: l'evento reale di quell'arco, null per un salto — serve a calcolare l'attesa reale sui nodi sotto.
    val eventiArco = mutableListOf<EventoCreato?>()

    sequenza.forEach { tappa ->
        registra(tappa.partenza)
        registra(tappa.arrivo)
        if (nodi.last() != tappa.partenza.luogoId) {
            archi += ArcoPercorso(trattaId = null, durataMinuti = null)
            eventiArco += null
            nodi += tappa.partenza.luogoId
        }
        if (tappa.arrivo.luogoId != tappa.partenza.luogoId) {
            val durataMinuti = ChronoUnit.MINUTES.between(tappa.evento.inizio, tappa.evento.fine).toInt()
            archi += ArcoPercorso(trattaId = tappa.evento.eventoId.toString(), durataMinuti = durataMinuti, durataIndicativa = false, tipoTratta = tappa.tipo)
            eventiArco += tappa.evento
            nodi += tappa.arrivo.luogoId
        }
    }

    val nodiConAttesa = nodi.mapIndexed { indice, luogoId ->
        val entrante = eventiArco.getOrNull(indice - 1)
        val uscente = eventiArco.getOrNull(indice)
        val attesa = if (entrante != null && uscente != null) {
            ChronoUnit.MINUTES.between(entrante.fine, uscente.inizio).toInt().coerceAtLeast(0)
        } else null
        NodoPercorso(luogoId, attesa)
    }

    return PercorsoTemplate(nodiConAttesa, archi) to luoghi.values.toList()
}

/**
 * Dove ci si troverebbe ADESSO lungo un'esecuzione, secondo la sola pianificazione (orari reali
 * degli eventi già scritti a calendario): MAI una posizione GPS — vedi [calcolaPosizioneAttuale].
 */
sealed class PosizioneAttualeEsecuzione {
    abstract val lat: Double
    abstract val lng: Double

    /** In transito su una tratta: coordinate interpolate tra partenza e arrivo in base alla frazione di tempo reale trascorsa. */
    data class SuSegmento(override val lat: Double, override val lng: Double) : PosizioneAttualeEsecuzione()

    /** In un'attesa tra due eventi consecutivi nello stesso luogo: nessuna linea da percorrere, si evidenzia il luogo stesso. */
    data class SuLuogo(val luogoId: String, override val lat: Double, override val lng: Double) : PosizioneAttualeEsecuzione()
}

/**
 * Esito di [calcolaPosizioneAttuale]: a differenza di un semplice `PosizioneAttualeEsecuzione?`,
 * il caso negativo porta anche [NonDisponibile.motivo] — un messaggio leggibile del PERCHÉ non
 * c'è una posizione da mostrare (fuori finestra, coordinate mancanti, evento intermedio non
 * tracciabile, ecc.). Introdotto insieme al log diagnostico in EsecuzioneMappaScreen: senza un
 * motivo esplicito, un `null` da solo non basta a un utente per capire (o a chi lo assiste per
 * dirgli) se è normale (non è in viaggio in questo momento) o un problema sui dati.
 */
sealed class RisultatoPosizioneAttuale {
    data class Trovata(val posizione: PosizioneAttualeEsecuzione) : RisultatoPosizioneAttuale()
    data class NonDisponibile(val motivo: String) : RisultatoPosizioneAttuale()
}

/**
 * Calcola dove ci si troverebbe ADESSO lungo l'esecuzione, in base ai soli orari pianificati (reali,
 * dagli eventi già scritti a calendario) — non è mai una posizione GPS, solo una stima: interpola
 * linearmente tra partenza e arrivo dell'evento in corso secondo la frazione di tempo trascorsa tra
 * il suo inizio e la sua fine.
 *
 * Si applica solo quando [adesso] cade dentro la finestra temporale di QUESTA esecuzione: prima del
 * primo evento (viaggio non ancora iniziato) o dopo l'ultimo (viaggio concluso) ritorna
 * [RisultatoPosizioneAttuale.NonDisponibile]. Il confronto è tra [ZonedDateTime] (istanti assoluti,
 * data+ora+fuso — mai solo l'ora del giorno): [EventoCreato.inizio]/`fine` vengono da
 * `Instant.ofEpochMilli(...)` in [com.desideri.viaggiotemplate.domain.calendar.CalendarWriter.eventiPerId],
 * quindi un evento con la stessa fascia oraria ma su un giorno diverso NON può mai far scattare
 * questi confronti per errore: `isBefore`/`isAfter` su due `ZonedDateTime` confrontano l'istante
 * intero, non la sola ora locale.
 *
 * Ogni evento è considerato contro l'intera [eventiOrdinati] (non filtrata per posizione nota, a
 * differenza di [risolviPercorsoEsecuzione]): se l'evento in corso ORA non ha una posizione
 * tracciabile (nessuna riga in [posizioni], o coordinate mancanti su partenza/arrivo), si ritorna
 * [RisultatoPosizioneAttuale.NonDisponibile] invece di "saltare" al prossimo evento tracciabile —
 * mostrare lì una posizione sarebbe inventata, dato che non sappiamo dove si trovi realmente
 * durante quella tratta.
 *
 * Se [adesso] cade in un'attesa tra la fine di un evento e l'inizio del successivo, non c'è una
 * linea da percorrere: si evidenzia invece il luogo in cui ci si trova, ma solo quando l'arrivo del
 * primo evento e la partenza del secondo sono confermati essere lo stesso luogo — altrimenti (un
 * evento intermedio non tracciabile "saltato" nel mezzo) non c'è abbastanza certezza su dove ci si
 * trovi durante quell'attesa, e si ritorna [RisultatoPosizioneAttuale.NonDisponibile] piuttosto che
 * indovinare.
 *
 * [eventiOrdinati] deve essere ordinato per orario di inizio reale, come per [risolviPercorsoEsecuzione].
 */
fun calcolaPosizioneAttuale(
    eventiOrdinati: List<EventoCreato>,
    posizioni: List<PosizioneEventoCreato>,
    adesso: ZonedDateTime
): RisultatoPosizioneAttuale {
    val posizioniPerId = posizioni.associateBy { it.calendarEventId }

    fun nonDisponibile(motivo: String) = RisultatoPosizioneAttuale.NonDisponibile(motivo)

    eventiOrdinati.forEachIndexed { indice, evento ->
        if (!adesso.isBefore(evento.inizio) && !adesso.isAfter(evento.fine)) {
            val posizione = posizioniPerId[evento.eventoId]
                ?: return nonDisponibile("Nessuna posizione tracciata per l'evento in corso (${evento.titolo}).")
            val partenza = posizione.partenza
                ?: return nonDisponibile("Partenza mancante per l'evento in corso (${evento.titolo}).")
            val arrivo = posizione.arrivo
                ?: return nonDisponibile("Arrivo mancante per l'evento in corso (${evento.titolo}).")
            if (partenza.luogoId == arrivo.luogoId) {
                val lat = arrivo.latitudine
                val lng = arrivo.longitudine
                return if (lat != null && lng != null) {
                    RisultatoPosizioneAttuale.Trovata(PosizioneAttualeEsecuzione.SuLuogo(arrivo.luogoId, lat, lng))
                } else {
                    nonDisponibile("Coordinate mancanti per il luogo dell'evento in corso (${arrivo.nome}).")
                }
            }
            val latPartenza = partenza.latitudine
            val lngPartenza = partenza.longitudine
            val latArrivo = arrivo.latitudine
            val lngArrivo = arrivo.longitudine
            if (latPartenza == null || lngPartenza == null || latArrivo == null || lngArrivo == null) {
                return nonDisponibile("Coordinate di partenza o arrivo mancanti per l'evento in corso (${evento.titolo}).")
            }
            val durataTotaleMs = Duration.between(evento.inizio, evento.fine).toMillis()
            val frazione = if (durataTotaleMs <= 0) 1.0 else
                (Duration.between(evento.inizio, adesso).toMillis().toDouble() / durataTotaleMs).coerceIn(0.0, 1.0)
            return RisultatoPosizioneAttuale.Trovata(
                PosizioneAttualeEsecuzione.SuSegmento(
                    lat = latPartenza + (latArrivo - latPartenza) * frazione,
                    lng = lngPartenza + (lngArrivo - lngPartenza) * frazione
                )
            )
        }

        val successivo = eventiOrdinati.getOrNull(indice + 1) ?: return@forEachIndexed
        if (adesso.isAfter(evento.fine) && adesso.isBefore(successivo.inizio)) {
            val arrivo = posizioniPerId[evento.eventoId]?.arrivo
                ?: return nonDisponibile("Nessuna posizione di arrivo per l'evento appena concluso (${evento.titolo}).")
            val partenzaSuccessiva = posizioniPerId[successivo.eventoId]?.partenza
                ?: return nonDisponibile("Nessuna posizione di partenza per il prossimo evento (${successivo.titolo}).")
            if (arrivo.luogoId != partenzaSuccessiva.luogoId) {
                return nonDisponibile("Attesa tra luoghi diversi: l'evento intermedio non è tracciabile con certezza.")
            }
            val lat = arrivo.latitudine
            val lng = arrivo.longitudine
            return if (lat != null && lng != null) {
                RisultatoPosizioneAttuale.Trovata(PosizioneAttualeEsecuzione.SuLuogo(arrivo.luogoId, lat, lng))
            } else {
                nonDisponibile("Coordinate mancanti per il luogo dell'attesa (${arrivo.nome}).")
            }
        }
    }
    return nonDisponibile("Fuori dalla finestra temporale dell'esecuzione (prima del primo evento o dopo l'ultimo).")
}
