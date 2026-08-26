package com.desideri.viaggiotemplate.data.remote

import com.desideri.viaggiotemplate.data.local.CredenzialiItalo
import com.desideri.viaggiotemplate.domain.model.Vettore
import java.time.LocalDate
import java.time.LocalTime

/** Contratto comune ai client che scaricano orari reali di corse ferroviarie da una fonte esterna. */
interface ClientOrariTreno {
    /**
     * @param oraRiferimento le corse restituite partono da qui in poi (in ordine cronologico).
     * @throws java.io.IOException se la richiesta di rete fallisce.
     */
    fun cercaCorse(
        daStazione: String,
        aStazione: String,
        data: LocalDate,
        oraRiferimento: LocalTime = LocalTime.MIDNIGHT,
        limite: Int = 16
    ): List<CorsaScaricata>
}

/**
 * Client per scaricare orari reali per il vettore indicato, o null se non c'e' (ancora)
 * un'integrazione. [credenzialiItalo] e' usato solo per [Vettore.ITALO] (vedi [OrariItaloClient]).
 *
 * [Vettore.ITALO] restituisce sempre null: sia il login guest sia un account Italo reale vengono
 * bloccati dal gateway anti-bot di Italo (Layer7/Akamai, HTTP 403) — confermato anche da altri
 * sviluppatori che usano la stessa API non ufficiale (issue aperta e irrisolta su
 * github.com/SimoDax/Italo-API). Non essendo un problema di credenziali ma di fingerprinting del
 * client, non c'e' modo di aggirarlo da qui: [OrariItaloClient] resta pronto per essere
 * ri-agganciato se in futuro Akamai smettesse di bloccare l'endpoint.
 */
fun clientOrariPer(vettore: Vettore, credenzialiItalo: CredenzialiItalo? = null): ClientOrariTreno? = when (vettore) {
    Vettore.SBB -> OrariTrasportiSvizzeriClient()
    Vettore.TRENITALIA -> OrariTrenitaliaClient()
    Vettore.ITALO -> null
    Vettore.ALTRO -> null
}
