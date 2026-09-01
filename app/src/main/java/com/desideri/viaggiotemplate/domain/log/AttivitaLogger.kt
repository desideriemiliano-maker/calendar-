package com.desideri.viaggiotemplate.domain.log

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Categoria di una [VoceRegistro], per il filtro nella schermata di consultazione: azione
 * dell'utente, chiamata a un'integrazione esterna (con [VoceRegistro.esito]/[VoceRegistro.durataMs]),
 * o errore imprevisto non legato a una specifica integrazione.
 */
enum class CategoriaRegistro(val etichetta: String) {
    AZIONE_UTENTE("Azione utente"),
    INTEGRAZIONE("Integrazione"),
    ERRORE("Errore")
}

enum class EsitoRegistro { SUCCESSO, ERRORE }

data class VoceRegistro(
    val timestampMs: Long,
    val categoria: CategoriaRegistro,
    val descrizione: String,
    val esito: EsitoRegistro? = null,
    val durataMs: Long? = null,
    val dettaglioErrore: String? = null
)

private const val GIORNI_CONSERVAZIONE = 7
private const val NOME_DIRECTORY = "registro_attivita"
private const val PREFISSO_FILE = "attivita-"
private const val SUFFISSO_FILE = ".jsonl"
private val FORMATO_DATA_FILE = DateTimeFormatter.ISO_LOCAL_DATE

internal fun nomeFilePer(data: LocalDate): String = "$PREFISSO_FILE${data.format(FORMATO_DATA_FILE)}$SUFFISSO_FILE"

internal fun dataDaNomeFile(nomeFile: String): LocalDate? {
    if (!nomeFile.startsWith(PREFISSO_FILE) || !nomeFile.endsWith(SUFFISSO_FILE)) return null
    val parteData = nomeFile.removePrefix(PREFISSO_FILE).removeSuffix(SUFFISSO_FILE)
    return try {
        LocalDate.parse(parteData, FORMATO_DATA_FILE)
    } catch (e: Exception) {
        null
    }
}

/** File (di [nomiEsistenti], nomi soli, non percorsi) più vecchi di [giorniConservazione] giorni rispetto a [oggi]: quelli di cui la rotazione a 7 giorni deve liberarsi. */
internal fun fileDaEliminare(nomiEsistenti: List<String>, oggi: LocalDate, giorniConservazione: Int = GIORNI_CONSERVAZIONE): List<String> {
    val sogliaMinima = oggi.minusDays(giorniConservazione.toLong() - 1)
    return nomiEsistenti.filter { nome -> dataDaNomeFile(nome)?.let { it < sogliaMinima } ?: false }
}

internal fun VoceRegistro.toJson(): JSONObject = JSONObject().apply {
    put("ts", timestampMs)
    put("cat", categoria.name)
    put("descr", descrizione)
    esito?.let { put("esito", it.name) }
    durataMs?.let { put("durataMs", it) }
    dettaglioErrore?.let { put("errore", it) }
}

/** Null su una riga corrotta/illeggibile invece di far fallire l'intera lettura del registro (vedi [AttivitaLogger.leggiRecenti]). */
internal fun JSONObject.toVoceRegistro(): VoceRegistro? = try {
    VoceRegistro(
        timestampMs = getLong("ts"),
        categoria = CategoriaRegistro.valueOf(getString("cat")),
        descrizione = getString("descr"),
        esito = if (has("esito")) EsitoRegistro.valueOf(getString("esito")) else null,
        durataMs = if (has("durataMs")) getLong("durataMs") else null,
        dettaglioErrore = if (has("errore")) getString("errore") else null
    )
} catch (e: Exception) {
    null
}

/**
 * Registro locale delle attività significative dell'app (azioni utente ed esiti delle
 * integrazioni esterne), consultabile dal menu a tre puntini. `object` singleton (come
 * [com.desideri.viaggiotemplate.ui.AppContainer]) invece di un'istanza iniettata: va richiamabile
 * da ovunque nel codice — client di rete, `CalendarWriter`, migrazioni Room — senza dover far
 * transitare un riferimento attraverso ogni costruttore.
 *
 * Persistenza: un file JSON Lines per giorno di calendario (non una tabella nel database Room
 * principale). Due motivi:
 * - Il backup su Google Drive ([com.desideri.viaggiotemplate.domain.backup.DatabaseBackupManager])
 *   copia il file .db intero a livello di byte: una tabella in più nello stesso database finirebbe
 *   automaticamente nel backup, cosa non desiderata per un log diagnostico. Un file a parte non
 *   viene mai letto da quel meccanismo, che punta solo al nome del file .db.
 * - Va escluso anche dal backup automatico di Android stesso (`android:allowBackup="true"` nel
 *   manifest, nessuna regola di esclusione dichiarata): la directory ritornata da
 *   [Context.getNoBackupFilesDir] è l'unica esclusa per costruzione dal sistema, senza bisogno di
 *   XML di configurazione aggiuntivo.
 *
 * La rotazione a un file per giorno rende banale anche liberarsi dei dati più vecchi di
 * [GIORNI_CONSERVAZIONE] giorni: basta cancellare i file la cui data (nel nome) è troppo vecchia,
 * senza dover analizzare il contenuto riga per riga.
 *
 * Scritture sempre asincrone e mai propagate: [scope] ha parallelismo 1 (le scritture restano in
 * ordine senza bisogno di un mutex esplicito) e ogni eccezione di I/O viene ignorata sul posto —
 * un guasto nel logging non deve mai far fallire né rallentare l'operazione che lo ha generato.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object AttivitaLogger {
    private lateinit var directory: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var ultimaRotazione: LocalDate? = null

    fun init(context: Context) {
        if (::directory.isInitialized) return
        directory = File(context.applicationContext.noBackupFilesDir, NOME_DIRECTORY)
    }

    /** Come [init], ma senza passare da un `Context` Android (indisponibile nei test JVM puri) e senza il guard "una volta sola": ogni test parte da una directory temporanea pulita. */
    internal fun initPerTest(directory: File) {
        this.directory = directory
        this.ultimaRotazione = null
    }

    /**
     * Aspetta che ogni scrittura già accodata su [scope] sia completata. [scope] serializza
     * (parallelismo 1, FIFO): un task accodato qui dopo N scritture parte solo a N completate, quindi
     * attenderlo equivale ad attendere tutte le precedenti. Solo per i test: la produzione non deve
     * mai attendere le proprie scritture (vedi la doc della classe).
     */
    internal suspend fun attendiScrittureInSospesoPerTest() {
        val completamento = CompletableDeferred<Unit>()
        scope.launch { completamento.complete(Unit) }
        completamento.await()
    }

    fun azioneUtente(descrizione: String) {
        scrivi(VoceRegistro(System.currentTimeMillis(), CategoriaRegistro.AZIONE_UTENTE, descrizione))
    }

    /** Errore imprevisto non legato a una specifica integrazione (per quelli, vedi [integrazione]/[misura]/[misuraSync]). */
    fun errore(descrizione: String, dettaglio: String? = null) {
        scrivi(VoceRegistro(System.currentTimeMillis(), CategoriaRegistro.ERRORE, descrizione, dettaglioErrore = dettaglio))
    }

    fun integrazione(descrizione: String, esito: EsitoRegistro, durataMs: Long, dettaglioErrore: String? = null) {
        scrivi(VoceRegistro(System.currentTimeMillis(), CategoriaRegistro.INTEGRAZIONE, descrizione, esito, durataMs, dettaglioErrore))
    }

    /** Per i call site sincroni (non-suspend, es. `CalendarWriter`): misura la durata, logga l'esito e ripropaga l'eccezione inalterata. */
    fun <T> misuraSync(descrizione: String, block: () -> T): T {
        val inizio = System.currentTimeMillis()
        try {
            val risultato = block()
            integrazione(descrizione, EsitoRegistro.SUCCESSO, System.currentTimeMillis() - inizio)
            return risultato
        } catch (e: Exception) {
            integrazione(descrizione, EsitoRegistro.ERRORE, System.currentTimeMillis() - inizio, e.message)
            throw e
        }
    }

    /** Come [misuraSync], per i call site suspend. Una cancellazione (navigazione via prima che la chiamata finisca) non è un errore da loggare. */
    suspend fun <T> misura(descrizione: String, block: suspend () -> T): T {
        val inizio = System.currentTimeMillis()
        try {
            val risultato = block()
            integrazione(descrizione, EsitoRegistro.SUCCESSO, System.currentTimeMillis() - inizio)
            return risultato
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            integrazione(descrizione, EsitoRegistro.ERRORE, System.currentTimeMillis() - inizio, e.message)
            throw e
        }
    }

    private fun scrivi(voce: VoceRegistro) {
        if (!::directory.isInitialized) return
        scope.launch {
            try {
                ruotaSeNecessario()
                directory.mkdirs()
                File(directory, nomeFilePer(LocalDate.now())).appendText(voce.toJson().toString() + "\n")
            } catch (e: Exception) {
                // Mai propagare: vedi la doc della classe.
            }
        }
    }

    /** Chiamata da dentro [scope] (parallelismo 1): nessuna corsa possibile su [ultimaRotazione]. Una volta al giorno, non a ogni scrittura. */
    private fun ruotaSeNecessario() {
        val oggi = LocalDate.now()
        if (ultimaRotazione == oggi) return
        ultimaRotazione = oggi
        val nomiEsistenti = directory.list()?.toList() ?: return
        fileDaEliminare(nomiEsistenti, oggi).forEach { File(directory, it).delete() }
    }

    /** Tutte le voci ancora conservate, più recenti prima. Chiamata dalla schermata di consultazione, non da un percorso critico. */
    suspend fun leggiRecenti(): List<VoceRegistro> = withContext(Dispatchers.IO) {
        if (!::directory.isInitialized) return@withContext emptyList()
        val file = directory.listFiles() ?: return@withContext emptyList()
        file.filter { dataDaNomeFile(it.name) != null }
            .flatMap { f -> f.readLines() }
            .mapNotNull { linea -> linea.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it).toVoceRegistro() }.getOrNull() } }
            .sortedByDescending { it.timestampMs }
    }

    suspend fun svuota() = withContext(Dispatchers.IO) {
        if (!::directory.isInitialized) return@withContext
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }
}
