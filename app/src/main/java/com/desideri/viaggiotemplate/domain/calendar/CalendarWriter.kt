package com.desideri.viaggiotemplate.domain.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.model.Notifica
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

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

    private fun inserisciPromemoria(eventoId: Long, minutiPrima: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventoId)
            put(CalendarContract.Reminders.MINUTES, minutiPrima)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
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
