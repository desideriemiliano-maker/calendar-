package com.desideri.viaggiotemplate.domain.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.location.formattaCoordinateGps
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.Tratta
import java.time.Instant
import java.time.LocalDate
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
 */
data class RisultatoEliminazioneEventi(
    val idsRichiesti: Int,
    val cancellatiBatch: Int,
    val fallbackTentato: Boolean,
    val cancellatiFallback: Int,
    val idsNonCancellati: List<Long>
) {
    val completato: Boolean get() = idsNonCancellati.isEmpty()
}

/** Un calendario del dispositivo su cui l'app può scrivere eventi. */
data class CalendarioDisponibile(
    val id: Long,
    val nome: String,
    val account: String,
    val accountType: String,
    val isPrimary: Boolean
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
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
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

            while (cursor.moveToNext()) {
                val visibile = cursor.getInt(idxVisible) != 0
                val livelloAccesso = cursor.getInt(idxAccesso)
                if (!visibile || livelloAccesso < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue

                out += CalendarioDisponibile(
                    id = cursor.getLong(idxId),
                    nome = cursor.getString(idxNome) ?: cursor.getString(idxAccount),
                    account = cursor.getString(idxAccount),
                    accountType = cursor.getString(idxAccountType),
                    isPrimary = idxPrimary >= 0 && cursor.getInt(idxPrimary) != 0
                )
            }
        }
        return out.sortedWith(compareByDescending<CalendarioDisponibile> { it.isPrimary }.thenBy { it.nome })
    }

    /** Inserisce un singolo evento (con promemoria, descrizione e colore) e ritorna il suo ID, o null se l'inserimento fallisce. */
    fun inserisciEvento(
        calendario: CalendarioDisponibile,
        data: LocalDate,
        eventoDaScrivere: EventoDaScrivere,
        zonaOraria: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val evento = eventoDaScrivere.evento
        val inizio = data.atTime(evento.inizioBlocco).atZone(zonaOraria).toInstant().toEpochMilli()
        val fine = data.atTime(evento.fineBlocco).atZone(zonaOraria).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendario.id)
            put(CalendarContract.Events.TITLE, evento.titolo())
            put(CalendarContract.Events.DTSTART, inizio)
            put(CalendarContract.Events.DTEND, fine)
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

        return eventoId
    }

    /** Inserisce tutti gli eventi calcolati di un template in un'unica chiamata. */
    fun inserisciEventi(
        calendario: CalendarioDisponibile,
        data: LocalDate,
        eventi: List<EventoDaScrivere>
    ): List<Long> = eventi.mapNotNull { inserisciEvento(calendario, data, it) }

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
     */
    fun eliminaEventi(eventIds: List<Long>): RisultatoEliminazioneEventi {
        if (eventIds.isEmpty()) {
            return RisultatoEliminazioneEventi(0, 0, fallbackTentato = false, cancellatiFallback = 0, idsNonCancellati = emptyList())
        }

        val selezione = "${CalendarContract.Events._ID} IN (${eventIds.joinToString(",") { "?" }})"
        val args = eventIds.map { it.toString() }.toTypedArray()
        val cancellatiBatch = context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selezione, args)

        if (cancellatiBatch >= eventIds.size) {
            return RisultatoEliminazioneEventi(eventIds.size, cancellatiBatch, fallbackTentato = false, cancellatiFallback = 0, idsNonCancellati = emptyList())
        }

        val idsRimasti = idPresenti(eventIds)
        var cancellatiFallback = 0
        val idsNonCancellati = mutableListOf<Long>()
        for (id in idsRimasti) {
            val righe = context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            if (righe > 0) cancellatiFallback++ else idsNonCancellati += id
        }

        return RisultatoEliminazioneEventi(
            idsRichiesti = eventIds.size,
            cancellatiBatch = cancellatiBatch,
            fallbackTentato = true,
            cancellatiFallback = cancellatiFallback,
            idsNonCancellati = idsNonCancellati
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
