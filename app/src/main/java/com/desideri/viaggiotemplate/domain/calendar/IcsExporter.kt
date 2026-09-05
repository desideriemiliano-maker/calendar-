package com.desideri.viaggiotemplate.domain.calendar

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Esportazione di [EventoCreato] in formato iCalendar (RFC 5545), per condividere via Intent.ACTION_SEND
 * (email, Outlook, ecc.) eventi già scritti sul Calendar Provider — vedi EventiCreatiViewModel/Screen.
 *
 * **Orari in UTC, non TZID/VTIMEZONE.** Un `DTSTART;TZID=Europe/Zurich` richiederebbe di incorporare
 * anche il blocco `VTIMEZONE` con le regole di passaggio ora legale/solare di quello specifico fuso
 * (RFC 5545 lo impone: un TZID senza VTIMEZONE corrispondente è ambiguo per il client che importa) -
 * scriverle a mano è un lavoro delicato e soggetto a errori (le regole DST cambiano nel tempo, non
 * sono un dato statico) per un beneficio che qui non serve: UTC con suffisso "Z" è ugualmente valido
 * per RFC 5545, sempre non ambiguo per costruzione, e non richiede alcuna regola aggiuntiva. Gli
 * eventi di [EventoCreato] possono attraversare fusi diversi nella stessa esecuzione (Lugano/Milano
 * nell'esempio che ha portato a questa scelta): convertendo tutto a UTC quel caso è già gestito
 * correttamente senza bisogno di più VTIMEZONE per la stessa esportazione.
 *
 * **Un solo VEVENT per evento esportato**, mai raggruppati per forza in un solo file quando non
 * richiesto: [generaIcs] accetta comunque una lista e produce un unico VCALENDAR multi-VEVENT,
 * perché è la forma RFC 5545 standard e funziona nella stragrande maggioranza dei client (import
 * esplicito di Outlook desktop/web, Google Calendar, Apple Calendar). Il gap noto è specifico:
 * un DOPPIO CLIC su un .ics con più VEVENT in Outlook desktop classico apre solo il primo evento,
 * un comportamento storico di quel flusso (non del formato) mai risolto in tutte le versioni. Non è
 * un motivo per abbandonare il file multi-evento come esportazione principale (l'utente ha comunque
 * un "Importa calendario" esplicito in Outlook che legge tutti i VEVENT, ed è quello che user
 * dovrebbe usare per più eventi) — ma è la ragione per cui EventiCreatiScreen offre ANCHE
 * un'esportazione per singolo evento (un file con un solo VEVENT ciascuno): quella resta corretta
 * anche nel flusso doppio-clic, come rete di sicurezza quando serve un singolo evento affidabile.
 */
private val FORMATO_ISTANTE_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** Limite RFC 5545 §3.1: una riga di contenuto (incluso il CRLF finale) non deve superare i 75 ottetti; oltre, va "piegata" su una riga di continuazione che inizia con uno spazio. */
private const val LIMITE_OTTETTI_RIGA = 75

/**
 * Piega [linea] su più righe secondo RFC 5545 §3.1 se supera [LIMITE_OTTETTI_RIGA] ottetti UTF-8:
 * ogni continuazione inizia con uno spazio (che il parser scarta) e può quindi contenere un ottetto
 * in meno della prima riga. Il taglio non cade mai a metà di un carattere UTF-8 multi-byte (i
 * caratteri accentati italiani sono comuni nei titoli): si arretra il punto di taglio finché non è
 * un byte di inizio carattere (i byte di continuazione UTF-8 hanno il bit alto `10xxxxxx`).
 */
internal fun piegaLineaIcs(linea: String): String {
    val bytes = linea.toByteArray(Charsets.UTF_8)
    if (bytes.size <= LIMITE_OTTETTI_RIGA) return linea

    val risultato = StringBuilder()
    var indice = 0
    var primaRiga = true
    while (indice < bytes.size) {
        val limite = if (primaRiga) LIMITE_OTTETTI_RIGA else LIMITE_OTTETTI_RIGA - 1
        var fine = (indice + limite).coerceAtMost(bytes.size)
        while (fine > indice && fine < bytes.size && (bytes[fine].toInt() and 0xC0) == 0x80) fine--
        risultato.append(String(bytes, indice, fine - indice, Charsets.UTF_8))
        indice = fine
        if (indice < bytes.size) risultato.append("\r\n ")
        primaRiga = false
    }
    return risultato.toString()
}

/**
 * Escaping RFC 5545 §3.3.11 per un valore TEXT: backslash, punto e virgola e virgola sono
 * caratteri speciali della sintassi e vanno preceduti da backslash; un a-capo nel testo diventa la
 * sequenza letterale "\n" (due caratteri), dato che un a-capo vero spezzerebbe la riga di contenuto
 * in modo non valido. Il backslash va escaped PER PRIMO: altrimenti l'escaping degli altri
 * caratteri introdurrebbe backslash che verrebbero poi ri-escaped per errore.
 */
internal fun escapeTestoIcs(testo: String): String =
    testo
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

/**
 * Genera un VCALENDAR con un VEVENT per ciascuno di [eventi] (uno solo per un'esportazione
 * "singolo evento", vedi la doc del file per perché è comunque offerta). [promemoriaMinuti] mappa
 * [EventoCreato.eventoId] ai minuti di preavviso del promemoria (vedi
 * [CalendarWriter.promemoriaMinutiPerEventi]): un evento assente dalla mappa, o con NESSUNA
 * corrispondenza, non porta alcun VALARM - non tutti gli eventi ne hanno uno.
 *
 * [adesso] è il DTSTAMP (il momento in cui QUESTA rappresentazione .ics viene generata, RFC 5545
 * §3.8.7.2 - non l'orario dell'evento): parametrizzato per poter fissare un istante nei test invece
 * di dipendere dall'orologio di sistema.
 */
fun generaIcs(
    eventi: List<EventoCreato>,
    promemoriaMinuti: Map<Long, Int> = emptyMap(),
    adesso: Instant = Instant.now()
): String {
    val dtstamp = FORMATO_ISTANTE_UTC.format(adesso)
    val righe = mutableListOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Calendario++//Esportazione eventi//IT",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH"
    )

    eventi.forEach { evento ->
        righe += "BEGIN:VEVENT"
        // Stabile tra esportazioni ripetute dello stesso evento (utile se l'utente lo importa più
        // volte: un client corretto lo riconosce come lo stesso evento invece di duplicarlo),
        // derivato dall'ID univoco assegnato dal Calendar Provider a questo evento.
        righe += "UID:evento-${evento.eventoId}@viaggiotemplate.desideri.com"
        righe += "DTSTAMP:$dtstamp"
        righe += "DTSTART:${FORMATO_ISTANTE_UTC.format(evento.inizio.toInstant())}"
        righe += "DTEND:${FORMATO_ISTANTE_UTC.format(evento.fine.toInstant())}"
        righe += "SUMMARY:${escapeTestoIcs(evento.titolo)}"
        evento.indirizzoNavigazione?.let { righe += "LOCATION:${escapeTestoIcs(it)}" }
        if (evento.descrizione.isNotBlank()) righe += "DESCRIPTION:${escapeTestoIcs(evento.descrizione)}"
        promemoriaMinuti[evento.eventoId]?.takeIf { it > 0 }?.let { minuti ->
            righe += "BEGIN:VALARM"
            righe += "ACTION:DISPLAY"
            righe += "DESCRIPTION:Promemoria"
            righe += "TRIGGER:-PT${minuti}M"
            righe += "END:VALARM"
        }
        righe += "END:VEVENT"
    }

    righe += "END:VCALENDAR"
    // CRLF è la terminazione di riga richiesta da RFC 5545 (non il solo LF): alcuni parser rigidi
    // (storicamente anche Outlook) la considerano un requisito, non un dettaglio opzionale.
    return righe.joinToString("\r\n") { piegaLineaIcs(it) } + "\r\n"
}

/**
 * Nome file sicuro per un export (senza caratteri che potrebbero confondere un filesystem o un
 * client email): [testo] ridotto a lettere/cifre/trattini, troncato a [maxLunghezza]. Non è pensato
 * per essere leggibile al 100% (accenti e punteggiatura vengono persi) — solo per essere un nome
 * file valido ovunque; il titolo vero resta intatto dentro il file, in SUMMARY.
 */
internal fun nomeFileSicuroIcs(testo: String, maxLunghezza: Int = 40): String {
    val pulito = testo.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
    return pulito.take(maxLunghezza).trim('-').ifBlank { "evento" }
}

private const val NOME_CARTELLA_EXPORT_ICS = "ics_export"

/**
 * Genera il .ics per [eventi] e lo scrive nella sottocartella cache condivisa tramite FileProvider
 * (vedi file_paths.xml/AndroidManifest — stesso schema di RegistroAttivitaViewModel.esporta),
 * pronto per un Intent.ACTION_SEND. Null se [eventi] è vuoto o se la scrittura fallisce: mai
 * un'eccezione propagata, l'esportazione è un'azione accessoria che non deve mai far crashare la
 * schermata da cui viene richiesta.
 */
fun scriviIcsCondivisibile(
    context: Context,
    eventi: List<EventoCreato>,
    promemoriaMinuti: Map<Long, Int>,
    nomeFileBase: String
): Uri? {
    if (eventi.isEmpty()) return null
    return try {
        val directory = File(context.cacheDir, NOME_CARTELLA_EXPORT_ICS).apply { mkdirs() }
        val file = File(directory, "$nomeFileBase.ics")
        file.writeText(generaIcs(eventi, promemoriaMinuti))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        null
    }
}
