package com.desideri.viaggiotemplate.data.remote

import com.desideri.viaggiotemplate.domain.model.Vettore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Punto di passaggio unico per tutte le chiamate a [ClientOrariTreno.cercaCorse] (SBB/Trenitalia/
 * Italo, fonti non ufficiali e non pensate per un uso intenso): il dialog "Confronta alternative"
 * (vedi `EsecuzioneScreen.DialogConfrontoAlternative`) monta una riga per ogni tratta candidata di
 * uno slot, e ognuna interroga la stessa fonte in autonomia — senza coordinamento, aprire quel
 * dialog con N alternative sparava N richieste HTTP concorrenti nello stesso istante. Misurato su
 * transport.opendata.ch (l'endpoint di [OrariTrasportiSvizzeriClient]) un tempo di risposta base
 * di 9-14s anche per una singola richiesta isolata, con un chiaro peggioramento su richieste
 * ravvicinate: la burst di richieste concorrenti bastava a spingere quel tempo oltre il timeout
 * configurato — il sintomo "timeout su SBB" segnalato dall'utente.
 *
 * Tre misure, tutte pensate per essere buon vicino di un'API pubblica gratuita senza penalizzare
 * troppo l'utente che vuole vedere il confronto a colpo d'occhio (niente tap manuale per riga):
 * - [semaforo]: al piu' 2 richieste di rete in volo insieme, invece di una per riga tutte insieme.
 * - [staggerMinimo]: almeno 350ms tra l'avvio di due richieste consecutive, anche quando il
 *   semaforo ne libererebbe subito una seconda — spalma nel tempo quello che altrimenti resterebbe
 *   comunque un mini-burst di 2 connessioni aperte nello stesso istante.
 * - [cache]/[inCorso]: righe che chiedono esattamente la stessa cosa (stesso vettore, stazioni,
 *   data, ora di riferimento, limite) condividono un solo risultato invece di duplicare la
 *   chiamata — capita spesso perche' piu' tratte candidate dello stesso slot collegano di norma le
 *   stesse due stazioni.
 *
 * La cache assume che il risultato non dipenda da chi chiama: vero per SBB (API pubblica, nessuna
 * sessione) e per Trenitalia (nessuna credenziale per-utente in gioco). Non vale in generale per
 * Italo (che userebbe credenziali per-account), ma [clientOrariPer] la disabilita comunque sempre
 * (vedi li' il perche'), quindi il caso non e' oggi raggiungibile.
 */
object RicercaOrariCoordinator {

    private data class ChiaveRicerca(
        val vettore: Vettore,
        val daStazione: String,
        val aStazione: String,
        val data: LocalDate,
        val oraRiferimento: LocalTime,
        val limite: Int
    )

    private const val STAGGER_MINIMO_MS = 350L
    private const val PERMESSI_CONCORRENTI = 2

    private val semaforo = Semaphore(PERMESSI_CONCORRENTI)
    private val mutexStagger = Mutex()
    private var ultimoAvvioMs = 0L

    private val cache = ConcurrentHashMap<ChiaveRicerca, List<CorsaScaricata>>()
    private val inCorso = ConcurrentHashMap<ChiaveRicerca, CompletableDeferred<List<CorsaScaricata>>>()

    /**
     * Come [ClientOrariTreno.cercaCorse], ma passando da qui invece che chiamando [client]
     * direttamente: applica cache, deduplica richieste identiche in volo e limita la concorrenza
     * (vedi la doc della classe). Il chiamante non deve fare altro — nessun cambiamento nella UI:
     * resta sospeso fino al proprio turno esattamente come prima restava sospeso durante la sola
     * chiamata di rete, quindi lo spinner di caricamento di ogni riga copre anche l'attesa in coda.
     */
    suspend fun cercaCorse(
        client: ClientOrariTreno,
        vettore: Vettore,
        daStazione: String,
        aStazione: String,
        data: LocalDate,
        oraRiferimento: LocalTime,
        limite: Int
    ): List<CorsaScaricata> {
        val chiave = ChiaveRicerca(vettore, daStazione, aStazione, data, oraRiferimento, limite)

        cache[chiave]?.let { return it }

        val deferredProprio = CompletableDeferred<List<CorsaScaricata>>()
        val deferredEsistente = inCorso.putIfAbsent(chiave, deferredProprio)
        if (deferredEsistente != null) {
            // Un'altra riga sta gia' chiedendo esattamente la stessa cosa: aggancia il suo
            // risultato (o il suo errore) invece di aprire una seconda richiesta identica.
            return deferredEsistente.await()
        }

        return try {
            val risultato = semaforo.withPermit {
                staggerMinimo()
                withContext(Dispatchers.IO) {
                    client.cercaCorse(daStazione, aStazione, data, oraRiferimento, limite)
                }
            }
            cache[chiave] = risultato
            deferredProprio.complete(risultato)
            risultato
        } catch (e: Throwable) {
            deferredProprio.completeExceptionally(e)
            throw e
        } finally {
            inCorso.remove(chiave)
        }
    }

    /** Almeno [STAGGER_MINIMO_MS] tra l'avvio di due richieste di rete consecutive, indipendentemente da quanti permessi il semaforo lascerebbe liberi subito. */
    private suspend fun staggerMinimo() {
        mutexStagger.withLock {
            val adesso = System.currentTimeMillis()
            val attesa = (ultimoAvvioMs + STAGGER_MINIMO_MS) - adesso
            if (attesa > 0) delay(attesa)
            ultimoAvvioMs = System.currentTimeMillis()
        }
    }
}
