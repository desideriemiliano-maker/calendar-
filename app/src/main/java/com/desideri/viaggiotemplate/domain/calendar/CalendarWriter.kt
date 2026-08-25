package com.desideri.viaggiotemplate.domain.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import java.time.LocalDate
import java.time.ZoneId

/** Un calendario del dispositivo su cui l'app può scrivere eventi. */
data class CalendarioDisponibile(
    val id: Long,
    val nome: String,
    val account: String,
    val isPrimary: Boolean
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
                    isPrimary = idxPrimary >= 0 && cursor.getInt(idxPrimary) != 0
                )
            }
        }
        return out.sortedWith(compareByDescending<CalendarioDisponibile> { it.isPrimary }.thenBy { it.nome })
    }

    /** Inserisce un singolo evento e ritorna il suo ID, o null se l'inserimento fallisce. */
    fun inserisciEvento(
        calendarioId: Long,
        data: LocalDate,
        evento: EventoCalcolato,
        zonaOraria: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val inizio = data.atTime(evento.inizioBlocco).atZone(zonaOraria).toInstant().toEpochMilli()
        val fine = data.atTime(evento.fineBlocco).atZone(zonaOraria).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarioId)
            put(CalendarContract.Events.TITLE, evento.titolo())
            put(CalendarContract.Events.DTSTART, inizio)
            put(CalendarContract.Events.DTEND, fine)
            put(CalendarContract.Events.EVENT_TIMEZONE, zonaOraria.id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.let { ContentUris.parseId(it) }
    }

    /** Inserisce tutti gli eventi calcolati di un template in un'unica chiamata. */
    fun inserisciEventi(
        calendarioId: Long,
        data: LocalDate,
        eventi: List<EventoCalcolato>
    ): List<Long> = eventi.mapNotNull { inserisciEvento(calendarioId, data, it) }
}
