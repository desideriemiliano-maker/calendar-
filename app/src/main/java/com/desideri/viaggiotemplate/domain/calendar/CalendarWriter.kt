package com.desideri.viaggiotemplate.domain.calendar

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.location.formattaCoordinateGps
import com.desideri.viaggiotemplate.domain.log.AttivitaLogger
import com.desideri.viaggiotemplate.domain.log.EsitoRegistro
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Tratta
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Un evento scritto a calendario da una precedente esecuzione, riletto dal Calendar Provider a
 * partire dagli ID evento salvati localmente (vedi EsecuzioneCreataRepository): il Calendar
 * Provider NON permette a un'app normale di taggare eventi con dati propri via ExtendedProperties
 * ("Only sync adapters may write using .../extendedproperties"), quindi l'associazione
 * evento-esecuzione va tenuta in un database locale invece che sul provider stesso.
 */
data class EventoCreato(
    val eventoId: Long,
    val inizio: ZonedDateTime,
    val fine: ZonedDateTime,
    val titolo: String,
    val descrizione: String,
    /** Colore effettivo mostrato dal calendario per questo evento (proprio o ereditato dal calendario), usato come fallback quando l'esecuzione non ha un colore di template salvato. */
    val colore: Int?,
    /**
     * EVENT_LOCATION, valorizzato solo per le tratte AUTO con indirizzo o coordinate di arrivo
     * (vedi [Tratta.indirizzoArrivo]/[Tratta.latitudineArrivo]): usato per l'icona "Avvia
     * navigazione". Se il Luogo aveva coordinate GPS, questo testo è "Nome (GPS: lat, lng)" invece
     * del solo indirizzo (vedi [CalendarWriter.locationEvento]). Null per tutte le altre tratte.
     */
    val indirizzoNavigazione: String?
)

/**
 * Esito di [CalendarWriter.eliminaEventi]: abbastanza dettaglio da distinguere se è bastata la
 * delete batch o è servito il fallback per singolo evento, e quali id restano comunque non
 * cancellati — usato per decidere se toccare la registrazione locale e per capire, sul
 * dispositivo reale, quale dei due meccanismi il Calendar Provider onora davvero.
 *
 * [calendariSincronizzati]/[calendariSyncDisattivata] esistono perché [completato] da solo non
 * basta a dire che l'evento è davvero sparito: per un calendario di un account non locale, il
 * Calendar Provider su una delete "normale" (non sync-adapter, vedi [CalendarWriter.eliminaEventi])
 * marca la riga `deleted=1` invece di rimuoverla, e la nasconde alle query successive — comprese le
 * nostre, che quindi la contano come "cancellata" a tutti gli effetti. La rimozione vera dal server
 * arriva solo quando il sync adapter dell'account propaga quel flag, cosa che non è istantanea (e
 * non avviene affatto se la sincronizzazione è disattivata per quel calendario).
 */
data class RisultatoEliminazioneEventi(
    val idsRichiesti: Int,
    val cancellatiBatch: Int,
    val fallbackTentato: Boolean,
    val cancellatiFallback: Int,
    val idsNonCancellati: List<Long>,
    /** Nomi dei calendari non locali coinvolti, con sincronizzazione attiva (Calendars.SYNC_EVENTS = 1): la rimozione dal server non è istantanea, ma è in corso. */
    val calendariSincronizzati: List<String> = emptyList(),
    /** Nomi dei calendari non locali coinvolti, con sincronizzazione disattivata (Calendars.SYNC_EVENTS = 0): la rimozione resta solo locale finché l'utente non la riattiva. */
    val calendariSyncDisattivata: List<String> = emptyList()
) {
    val completato: Boolean get() = idsNonCancellati.isEmpty()
}

/**
 * Nota informativa "leggera" (mai un errore: nessuna azione richiesta se non per il caso
 * sync-disattivata) da mostrare dopo un'eliminazione [RisultatoEliminazioneEventi.completato] —
 * vedi la doc lì per il perché serve. Null quando non c'è nulla da segnalare (solo calendari
 * locali, o nessun calendario coinvolto): il caso comune, che non deve produrre alcun messaggio.
 */
fun RisultatoEliminazioneEventi.notaSincronizzazione(): String? =
    notaSincronizzazioneDaCalendari(calendariSincronizzati, calendariSyncDisattivata)

/** Come [RisultatoEliminazioneEventi.notaSincronizzazione], aggregata su più esiti (es. l'eliminazione in blocco di più esecuzioni, ciascuna con il proprio [RisultatoEliminazioneEventi]). */
fun List<RisultatoEliminazioneEventi>.notaSincronizzazioneAggregata(): String? =
    notaSincronizzazioneDaCalendari(flatMap { it.calendariSincronizzati }.distinct(), flatMap { it.calendariSyncDisattivata }.distinct())

/** Il caso sync-disattivata ha priorità su quello sync-attiva quando la stessa cancellazione coinvolge entrambi i tipi di calendario: è l'informazione più rilevante per l'utente (richiede una sua azione se vuole che la rimozione arrivi al server, l'altro caso no). */
private fun notaSincronizzazioneDaCalendari(sincronizzati: List<String>, syncDisattivata: List<String>): String? = when {
    syncDisattivata.isNotEmpty() ->
        "Rimosso dal dispositivo. Sincronizzazione disattivata per ${syncDisattivata.joinToString(", ")}: resterà visibile su Google Calendar finché non la riattivi."
    sincronizzati.isNotEmpty() -> "Rimosso — la rimozione dal server può richiedere qualche istante."
    else -> null
}

/** Un calendario del dispositivo su cui l'app può scrivere eventi. */
data class CalendarioDisponibile(
    val id: Long,
    val nome: String,
    val account: String,
    val accountType: String,
    val isPrimary: Boolean,
    /** Calendars.SYNC_EVENTS: se false (e l'account non è locale), gli eventi scritti qui non arrivano al server finché l'utente non la riattiva — vedi [CalendarWriter.inserisciEventi]. */
    val syncEventsAttivo: Boolean = true
)

/**
 * Pacchetto pronto per la scrittura a calendario di un evento calcolato: oltre agli orari
 * (di dominio del motore di calcolo), porta le scelte fatte in Esegui per quella sola
 * esecuzione — promemoria, descrizione libera e colore dell'evento.
 */
data class EventoDaScrivere(
    val evento: EventoCalcolato,
    val notifica: Notifica,
    val descrizione: String,
    val colore: Int?
)

/**
 * Ricostruisce inizio/fine di ogni evento come [LocalDateTime] assoluti a partire da [data] e
 * dagli orari [EventoCalcolato.inizioBlocco]/`fineBlocco`, che da soli non portano alcuna
 * informazione di giorno: sono solo un orario, e MotoreCalcolo non porta un offset di giorno
 * oltre quello (vedi il commento su EventoCalcolato) — un blocco che scavalca la mezzanotte (per
 * una tratta notturna, o perché l'arrotondamento spinge la fine a 00:00) è quindi indistinguibile,
 * guardando solo `fineBlocco`, da uno che finisce prima di iniziare.
 *
 * Si avanza il giorno corrente di un giorno ogni volta che l'orario successivo della sequenza "va
 * indietro nel tempo" rispetto a quello precedente: dato che [eventi] è sempre in ordine
 * cronologico (viene da `eventiCalcolati`, prodotto da MotoreCalcolo avanzando nel tempo), un calo
 * dell'orario di orologio può significare solo che è scoccata la mezzanotte.
 *
 * Nota: questo presume che la sequenza sia davvero monotona in senso assoluto. Un evento
 * modificato manualmente con un orario precedente a quello dell'evento prima di lui verrebbe
 * interpretato come "il giorno dopo" invece che come configurazione incoerente — la stessa
 * ambiguità intrinseca del rappresentare tutto solo con LocalTime, non risolvibile qui senza
 * portare un giorno esplicito fin dentro MotoreCalcolo.
 */
internal fun risolviIstanti(data: LocalDate, eventi: List<EventoDaScrivere>): List<Pair<LocalDateTime, LocalDateTime>> {
    var giornoCorrente = data
    var ultimoOrario = LocalTime.MIN
    fun avanza(orario: LocalTime): LocalDateTime {
        if (orario < ultimoOrario) giornoCorrente = giornoCorrente.plusDays(1)
        ultimoOrario = orario
        return giornoCorrente.atTime(orario)
    }
    return eventi.map { evD -> avanza(evD.evento.inizioBlocco) to avanza(evD.evento.fineBlocco) }
}

/**
 * Esito di [CalendarWriter.inserisciEventi]: gli id (uno per evento, null dove l'inserimento è
 * fallito, stesso ordine di input) più, quando il calendario scelto è di un account non locale con
 * la sincronizzazione disattivata (`Calendars.SYNC_EVENTS = 0`), un avviso da mostrare — a
 * differenza della cancellazione (vedi [RisultatoEliminazioneEventi]) qui non serve segnalare
 * anche il caso "sincronizzata ma in corso": un evento appena creato è già visibile localmente su
 * questo device fin da subito (l'insert non è mai "soft" come una delete), il tempo prima che
 * arrivi al server/altri device è il comportamento atteso di un qualunque calendario sincronizzato
 * e non merita un avviso ad ogni "Aggiungi al calendario". La sync disattivata resta comunque
 * rilevante: senza, l'utente potrebbe non vedere questi eventi da nessun'altra parte.
 */
data class RisultatoInserimentoEventi(
    val idInseriti: List<Long?>,
    val calendarioSyncDisattivata: Boolean
)

/**
 * Scrive gli eventi calcolati direttamente nel Google Calendar del dispositivo.
 * Richiede i permessi runtime android.permission.WRITE_CALENDAR e READ_CALENDAR.
 */
class CalendarWriter(private val context: Context) {

    /**
     * Elenca i calendari su cui l'app può effettivamente scrivere (visibili e con
     * accesso da contributor in su). Su un dispositivo con più account Google non
     * esiste un unico calendario "primario" univoco: ogni account ne ha uno proprio
     * con IS_PRIMARY=1, quindi la scelta va lasciata all'utente invece di indovinare.
     */
    fun elencaCalendariScrivibili(): List<CalendarioDisponibile> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.SYNC_EVENTS
        )
        val out = mutableListOf<CalendarioDisponibile>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            val idxId = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val idxNome = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val idxAccount = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val idxAccountType = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val idxPrimary = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val idxVisible = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
            val idxAccesso = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val idxSync = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.SYNC_EVENTS)

            while (cursor.moveToNext()) {
                val visibile = cursor.getInt(idxVisible) != 0
                val livelloAccesso = cursor.getInt(idxAccesso)
                if (!visibile || livelloAccesso < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue

                out += CalendarioDisponibile(
                    id = cursor.getLong(idxId),
                    nome = cursor.getString(idxNome) ?: cursor.getString(idxAccount),
                    account = cursor.getString(idxAccount),
                    accountType = cursor.getString(idxAccountType),
                    isPrimary = idxPrimary >= 0 && cursor.getInt(idxPrimary) != 0,
                    syncEventsAttivo = cursor.getInt(idxSync) != 0
                )
            }
        }
        return out.sortedWith(compareByDescending<CalendarioDisponibile> { it.isPrimary }.thenBy { it.nome })
    }

    /**
     * Inserisce un singolo evento (con promemoria, descrizione e colore) e ritorna il suo ID, o
     * null se l'inserimento fallisce. [inizio]/[fine] sono già risolti su un giorno di calendario
     * preciso (vedi [risolviIstanti]): questa funzione non fa più assunzioni su quale giorno usare
     * per [EventoCalcolato.inizioBlocco]/`fineBlocco`, che da soli sono solo un orario del giorno e
     * non bastano a saperlo (una tratta che scavalca la mezzanotte, o un arrotondamento che spinge
     * la fine a 00:00, farebbe altrimenti calcolare una fine precedente all'inizio).
     */
    fun inserisciEvento(
        calendario: CalendarioDisponibile,
        inizio: LocalDateTime,
        fine: LocalDateTime,
        eventoDaScrivere: EventoDaScrivere,
        zonaOraria: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val evento = eventoDaScrivere.evento

        // Difesa in profondità, non il percorso atteso: risolviIstanti garantisce già fine > inizio.
        // Se qualcosa a monte lo violasse comunque (un caso non coperto, un futuro refactor), il
        // Calendar Provider accetterebbe comunque l'insert con un DTEND <= DTSTART — l'evento
        // risulterebbe scritto (id valido, "N su N riusciti") ma il provider non ne materializza
        // alcuna istanza: invisibile in ogni vista del calendario, un fantasma indistinguibile da un
        // inserimento riuscito finché qualcuno non lo cerca a mano. Meglio fallire qui, rumorosamente.
        if (!fine.isAfter(inizio)) {
            AttivitaLogger.errore(
                "Inserimento evento calendario rifiutato: fine ($fine) non successiva all'inizio ($inizio)",
                evento.titolo()
            )
            return null
        }

        val inizioMillis = inizio.atZone(zonaOraria).toInstant().toEpochMilli()
        val fineMillis = fine.atZone(zonaOraria).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendario.id)
            put(CalendarContract.Events.TITLE, evento.titolo())
            put(CalendarContract.Events.DTSTART, inizioMillis)
            put(CalendarContract.Events.DTEND, fineMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zonaOraria.id)
            if (eventoDaScrivere.descrizione.isNotBlank()) {
                put(CalendarContract.Events.DESCRIPTION, eventoDaScrivere.descrizione)
            }
            locationEvento(evento.tratta)?.let {
                put(CalendarContract.Events.EVENT_LOCATION, it)
            }
            eventoDaScrivere.colore?.let { applicaColore(this, calendario, it) }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventoId = uri?.let { ContentUris.parseId(it) } ?: return null

        eventoDaScrivere.notifica.minuti?.let { minuti -> inserisciPromemoria(eventoId, minuti) }
        verificaScritturaEvento(eventoId, inizioMillis, fineMillis)

        return eventoId
    }

    /**
     * Rilegge DTSTART/DTEND appena scritti e li confronta con quelli attesi: un id valido dal
     * provider (vedi la doc di [inserisciEvento]) non garantisce che l'evento sia davvero
     * materializzato come previsto. Solo diagnostica — logga un'incongruenza invece di farla
     * restare invisibile, non tocca l'esito già ritornato al chiamante (l'evento un id ce l'ha
     * comunque, "fallirlo" a questo punto significherebbe inventare un errore diverso da quello
     * vero, se mai ce n'è uno).
     */
    private fun verificaScritturaEvento(eventoId: Long, inizioAtteso: Long, fineAtteso: Long) {
        val letti = context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventoId),
            arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND),
            null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idxInizio = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val idxFine = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            cursor.getLong(idxInizio) to cursor.getLong(idxFine)
        }
        if (letti == null) {
            AttivitaLogger.errore("Verifica post-inserimento: evento $eventoId non rileggibile subito dopo la scrittura")
        } else if (letti.first != inizioAtteso || letti.second != fineAtteso) {
            AttivitaLogger.errore(
                "Verifica post-inserimento: evento $eventoId scritto con DTSTART/DTEND diversi da quelli attesi",
                "atteso $inizioAtteso-$fineAtteso, letto ${letti.first}-${letti.second}"
            )
        }
    }

    /**
     * Inserisce tutti gli eventi calcolati di un template in un'unica chiamata. Gli id in
     * [RisultatoInserimentoEventi.idInseriti] sono nella STESSA lunghezza e nello STESSO ordine di
     * [eventi] (null dove l'inserimento è fallito), non solo gli ID riusciti: il chiamante deve
     * poter correlare ogni ID al suo evento di origine — es. per congelarne la posizione (vedi
     * [PosizioneEventoCreato]) — cosa impossibile con una lista compattata che perde gli indici
     * falliti.
     *
     * Richiede una sincronizzazione immediata (vedi [richiediSyncSeOpportuno]) UNA volta sola per
     * l'intera chiamata, non per singolo evento: sollecitarla ripetutamente non avrebbe alcun
     * effetto in più (il sistema le accorpa comunque) e sprecherebbe solo chiamate.
     */
    fun inserisciEventi(
        calendario: CalendarioDisponibile,
        data: LocalDate,
        eventi: List<EventoDaScrivere>
    ): RisultatoInserimentoEventi {
        val inizioMisurazione = System.currentTimeMillis()
        val istanti = risolviIstanti(data, eventi)
        val idInseriti = eventi.zip(istanti).map { (evento, istante) ->
            inserisciEvento(calendario, istante.first, istante.second, evento)
        }
        if (idInseriti.any { it != null }) {
            richiediSyncSeOpportuno(listOf(calendario.aCalendarioEvento()))
        }
        val riusciti = idInseriti.count { it != null }
        AttivitaLogger.integrazione(
            descrizione = "Calendar Provider: inserimento $riusciti/${eventi.size} eventi su \"${calendario.nome}\"",
            esito = if (riusciti == eventi.size) EsitoRegistro.SUCCESSO else EsitoRegistro.ERRORE,
            durataMs = System.currentTimeMillis() - inizioMisurazione
        )
        val syncDisattivata = calendario.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL && !calendario.syncEventsAttivo
        return RisultatoInserimentoEventi(idInseriti, syncDisattivata)
    }

    private fun CalendarioDisponibile.aCalendarioEvento() = CalendarioEvento(nome, account, accountType, syncEventsAttivo)

    /**
     * EVENT_LOCATION per [tratta]: le coordinate GPS del Luogo di arrivo, quando impostate, hanno
     * priorità sull'indirizzo testuale perché più precise per la navigazione. Per restare leggibile
     * nel calendario (non solo "41.9028, 12.4964", che l'utente non riconosce a colpo d'occhio) il
     * testo scritto è "Nome del luogo (GPS: lat, lng)": il nome resta a beneficio dell'utente, il
     * marcatore "(GPS: lat, lng)" viene poi riestratto da [avviaNavigazioneAuto] per navigare sul
     * punto esatto invece che cercare il testo per intero (vedi lì il motivo del prefisso "GPS:").
     */
    private fun locationEvento(tratta: Tratta): String? {
        val lat = tratta.latitudineArrivo
        val lng = tratta.longitudineArrivo
        if (lat != null && lng != null) {
            return "${tratta.luogoArrivo} (GPS: ${formattaCoordinateGps(lat, lng)})"
        }
        return tratta.indirizzoArrivo?.takeIf { it.isNotBlank() }
    }

    private fun inserisciPromemoria(eventoId: Long, minutiPrima: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventoId)
            put(CalendarContract.Reminders.MINUTES, minutiPrima)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    /**
     * Rilegge dal Calendar Provider i dettagli (orari, titolo, descrizione) degli eventi con
     * questi [eventIds] — tipicamente quelli salvati per una singola esecuzione in
     * EsecuzioneCreataRepository. Eventi nel frattempo eliminati dall'utente direttamente sul
     * calendario non compaiono più: non c'è modo di distinguerli da ID mai esistiti, quindi
     * semplicemente si omettono.
     */
    fun eventiPerId(eventIds: List<Long>): List<EventoCreato> {
        if (eventIds.isEmpty()) return emptyList()

        val proiezione = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selezione = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val args = eventIds.map { it.toString() }.toTypedArray()

        val out = mutableListOf<EventoCreato>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, proiezione, selezione, args, "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val idxId = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val idxTitolo = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val idxInizio = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val idxFine = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val idxDescrizione = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val idxFuso = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
            val idxColore = cursor.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)
            val idxLocation = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                val fuso = cursor.getString(idxFuso)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                out += EventoCreato(
                    eventoId = cursor.getLong(idxId),
                    inizio = Instant.ofEpochMilli(cursor.getLong(idxInizio)).atZone(fuso),
                    fine = Instant.ofEpochMilli(cursor.getLong(idxFine)).atZone(fuso),
                    titolo = cursor.getString(idxTitolo) ?: "",
                    descrizione = cursor.getString(idxDescrizione) ?: "",
                    colore = if (cursor.isNull(idxColore)) null else cursor.getInt(idxColore),
                    indirizzoNavigazione = cursor.getString(idxLocation)?.takeIf { it.isNotBlank() }
                )
            }
        }
        return out.sortedBy { it.inizio }
    }

    /**
     * Elimina dal Calendar Provider gli eventi con questi [eventIds]. Richiede il permesso
     * runtime WRITE_CALENDAR. Prova prima un'unica delete batch (selection "_id IN (...)"): se
     * cancella meno righe del previsto, riprova id per id con `ContentUris.withAppendedId` — il
     * Calendar Provider può comportarsi diversamente tra i due meccanismi a seconda di
     * device/account/calendario, e il fallback recupera i casi in cui solo uno dei due funziona
     * davvero. Eventi già eliminati dall'utente vengono semplicemente ignorati (già cancellati).
     *
     * **Perché niente `CALLER_IS_SYNCADAPTER`.** Ogni `delete()` qui sotto usa l'URI "nudo" di
     * `Events.CONTENT_URI`: per il Calendar Provider è sempre una "app delete" normale, mai una
     * sync-adapter delete. Per un calendario di un account non locale questo NON rimuove subito la
     * riga: la marca `deleted=1` (rimuovendo solo le sue occorrenze materializzate, cioè quello che
     * l'app Calendario del device mostra) e la nasconde alle query successive di un chiamante non
     * sync-adapter — comprese le nostre, che quindi non hanno modo di distinguerla da una riga
     * davvero sparita: [idPresenti] non la trova più, [cancellatiBatch] la conta comunque, e il
     * risultato appare "completato" a tutti gli effetti. La rimozione vera arriva solo quando il
     * sync adapter dell'account propaga quel flag al server. Usare `CALLER_IS_SYNCADAPTER` qui
     * bypasserebbe quel meccanismo (niente flag "dirty" da propagare): l'evento sparirebbe solo su
     * *questo* device, restando vivo per sempre sul server e su ogni altro device collegato allo
     * stesso account — peggio del problema che risolverebbe. La mitigazione possibile, quella
     * adottata sotto, è duplice: chiedere una sincronizzazione immediata (best-effort, vedi
     * [richiediSyncSeOpportuno]) e riportare nel risultato quali calendari sono coinvolti e col
     * loro stato di sincronizzazione, così chi chiama può informarne l'utente invece di lasciarlo
     * credere che sia già tutto risolto ovunque (vedi [RisultatoEliminazioneEventi.notaSincronizzazione]).
     */
    fun eliminaEventi(eventIds: List<Long>): RisultatoEliminazioneEventi {
        if (eventIds.isEmpty()) {
            return RisultatoEliminazioneEventi(0, 0, fallbackTentato = false, cancellatiFallback = 0, idsNonCancellati = emptyList())
        }
        val inizioMisurazione = System.currentTimeMillis()

        // Va risolto PRIMA della delete: una volta marcata deleted=1, una riga su un calendario
        // sincronizzato sparisce anche alle nostre query (vedi la doc della funzione), quindi non
        // sapremmo più a quale calendario apparteneva.
        val calendariCoinvolti = calendariDegliEventi(eventIds)

        val selezione = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val args = eventIds.map { it.toString() }.toTypedArray()
        val cancellatiBatch = context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selezione, args)

        val risultatoBase = if (cancellatiBatch >= eventIds.size) {
            RisultatoEliminazioneEventi(eventIds.size, cancellatiBatch, fallbackTentato = false, cancellatiFallback = 0, idsNonCancellati = emptyList())
        } else {
            val idsRimasti = idPresenti(eventIds)
            var cancellatiFallback = 0
            val idsNonCancellati = mutableListOf<Long>()
            for (id in idsRimasti) {
                val righe = context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
                if (righe > 0) cancellatiFallback++ else idsNonCancellati += id
            }
            RisultatoEliminazioneEventi(
                idsRichiesti = eventIds.size,
                cancellatiBatch = cancellatiBatch,
                fallbackTentato = true,
                cancellatiFallback = cancellatiFallback,
                idsNonCancellati = idsNonCancellati
            )
        }

        if (risultatoBase.completato) {
            richiediSyncSeOpportuno(calendariCoinvolti)
        }

        AttivitaLogger.integrazione(
            descrizione = "Calendar Provider: eliminazione ${risultatoBase.cancellatiBatch + risultatoBase.cancellatiFallback}/${eventIds.size} eventi",
            esito = if (risultatoBase.completato) EsitoRegistro.SUCCESSO else EsitoRegistro.ERRORE,
            durataMs = System.currentTimeMillis() - inizioMisurazione
        )

        return risultatoBase.copy(
            calendariSincronizzati = calendariCoinvolti
                .filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL && it.syncEventsAttivo }
                .map { it.nome }.distinct(),
            calendariSyncDisattivata = calendariCoinvolti
                .filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL && !it.syncEventsAttivo }
                .map { it.nome }.distinct()
        )
    }

    /** Tra [eventIds], quelli ancora presenti sul Calendar Provider dopo la delete batch (non cancellati nemmeno "soft"). */
    private fun idPresenti(eventIds: List<Long>): List<Long> {
        val selezione = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val args = eventIds.map { it.toString() }.toTypedArray()
        val presenti = mutableListOf<Long>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID), selezione, args, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            while (cursor.moveToNext()) presenti += cursor.getLong(idx)
        }
        return presenti
    }

    private data class CalendarioEvento(val nome: String, val account: String, val accountType: String, val syncEventsAttivo: Boolean)

    /** Calendari (deduplicati) a cui appartengono [eventIds], letti PRIMA di una eventuale delete (vedi il perché in [eliminaEventi]). */
    private fun calendariDegliEventi(eventIds: List<Long>): List<CalendarioEvento> {
        val calendarIds = mutableSetOf<Long>()
        val selezioneEventi = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val argsEventi = eventIds.map { it.toString() }.toTypedArray()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events.CALENDAR_ID), selezioneEventi, argsEventi, null
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
            while (cursor.moveToNext()) calendarIds += cursor.getLong(idx)
        }
        if (calendarIds.isEmpty()) return emptyList()

        val out = mutableListOf<CalendarioEvento>()
        val selezioneCal = "${CalendarContract.Calendars._ID} IN (${calendarIds.joinToString(",") { "?" }})"
        val argsCal = calendarIds.map { it.toString() }.toTypedArray()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.SYNC_EVENTS
            ),
            selezioneCal, argsCal, null
        )?.use { cursor ->
            val idxNome = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val idxAccount = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val idxAccountType = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val idxSync = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.SYNC_EVENTS)
            while (cursor.moveToNext()) {
                out += CalendarioEvento(
                    nome = cursor.getString(idxNome) ?: cursor.getString(idxAccount),
                    account = cursor.getString(idxAccount),
                    accountType = cursor.getString(idxAccountType),
                    syncEventsAttivo = cursor.getInt(idxSync) != 0
                )
            }
        }
        return out
    }

    /**
     * Richiede una sincronizzazione immediata (best-effort) per gli account dei calendari
     * sincronizzati coinvolti, per accorciare la finestra prima che la cancellazione "soft" (vedi
     * [eliminaEventi]) raggiunga davvero il server. Fire-and-forget: `requestSync` è asincrona e
     * non offre un modo affidabile per sapere quando (o se) viene onorata — il sistema la ignora
     * silenziosamente se l'utente ha disattivato la sincronizzazione automatica per l'account o
     * per questo specifico calendario, che è esattamente il caso già segnalato all'utente tramite
     * [RisultatoEliminazioneEventi.calendariSyncDisattivata].
     */
    /**
     * `SYNC_EXTRAS_EXPEDITED` da solo chiede al sistema di non aspettare il prossimo giro
     * programmato, ma un dispositivo può comunque posticiparla (Doze, un backoff residuo da un
     * tentativo precedente, l'impostazione "sincronizzazione automatica" disattivata): sintomo
     * osservato, l'evento compariva solo minuti dopo, e solo perché un'altra azione (una
     * cancellazione) aveva innescato un'altra sync nel frattempo. `IGNORE_BACKOFF`/
     * `IGNORE_SETTINGS` forzano la richiesta anche in quei casi — legittimo qui perché la sync è
     * conseguenza diretta di un'azione esplicita dell'utente (ha appena scritto/cancellato eventi),
     * non una sync periodica in background.
     */
    private fun richiediSyncSeOpportuno(calendari: List<CalendarioEvento>) {
        calendari
            .filter { it.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL && it.syncEventsAttivo }
            .map { Account(it.account, it.accountType) }
            .distinct()
            .forEach { account ->
                val inizioMisurazione = System.currentTimeMillis()
                try {
                    val extras = Bundle().apply {
                        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                        putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    }
                    ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
                    AttivitaLogger.integrazione(
                        // Mai il nome account: per un account Google coincide con l'email (vedi la
                        // nota sulla privacy in AttivitaLogger) — il tipo basta a diagnosticare.
                        descrizione = "Richiesta sync calendario (account ${account.type})",
                        esito = EsitoRegistro.SUCCESSO,
                        durataMs = System.currentTimeMillis() - inizioMisurazione
                    )
                } catch (e: Exception) {
                    AttivitaLogger.integrazione(
                        descrizione = "Richiesta sync calendario (account ${account.type})",
                        esito = EsitoRegistro.ERRORE,
                        durataMs = System.currentTimeMillis() - inizioMisurazione,
                        dettaglioErrore = e.message
                    )
                }
            }
    }

    /**
     * Prova ad accostare il colore ARGB richiesto (quello scelto per l'evento in Esegui, di
     * default quello della tratta) alla palette di colori evento disponibile per l'account del
     * calendario: Google Calendar valida i colori evento contro questa palette, un ARGB
     * arbitrario viene ignorato o normalizzato. Se l'account non espone una palette (es. un
     * calendario locale senza sync adapter), si imposta comunque EVENT_COLOR come best-effort.
     */
    private fun applicaColore(values: ContentValues, calendario: CalendarioDisponibile, coloreRichiesto: Int) {
        val coloreMigliore = trovaColoreEventoPiuVicino(calendario, coloreRichiesto)
        if (coloreMigliore != null) {
            values.put(CalendarContract.Events.EVENT_COLOR_KEY, coloreMigliore.first)
            values.put(CalendarContract.Events.EVENT_COLOR, coloreMigliore.second)
        } else {
            values.put(CalendarContract.Events.EVENT_COLOR, coloreRichiesto)
        }
    }

    /** Ritorna (COLOR_KEY, colore ARGB) della entry più vicina a [coloreRichiesto] nella palette TYPE_EVENT dell'account, o null se non disponibile. */
    private fun trovaColoreEventoPiuVicino(calendario: CalendarioDisponibile, coloreRichiesto: Int): Pair<String, Int>? {
        val projection = arrayOf(
            CalendarContract.Colors.COLOR_KEY,
            CalendarContract.Colors.COLOR
        )
        val selection = "${CalendarContract.Colors.ACCOUNT_NAME} = ? AND " +
            "${CalendarContract.Colors.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Colors.COLOR_TYPE} = ?"
        val selectionArgs = arrayOf(
            calendario.account,
            calendario.accountType,
            CalendarContract.Colors.TYPE_EVENT.toString()
        )
        var migliore: Pair<String, Int>? = null
        var distanzaMinima = Int.MAX_VALUE
        context.contentResolver.query(
            CalendarContract.Colors.CONTENT_URI, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idxKey = cursor.getColumnIndexOrThrow(CalendarContract.Colors.COLOR_KEY)
            val idxColore = cursor.getColumnIndexOrThrow(CalendarContract.Colors.COLOR)
            while (cursor.moveToNext()) {
                val colore = cursor.getInt(idxColore)
                val distanza = distanzaRgb(coloreRichiesto, colore)
                if (distanza < distanzaMinima) {
                    distanzaMinima = distanza
                    migliore = cursor.getString(idxKey) to colore
                }
            }
        }
        return migliore
    }

    private fun distanzaRgb(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return abs(dr) + abs(dg) + abs(db)
    }
}
