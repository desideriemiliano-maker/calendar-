package com.desideri.viaggiotemplate.data.remote

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

/** Client per scaricare orari reali per il vettore indicato, o null se non c'e' (ancora) un'integrazione. */
fun clientOrariPer(vettore: Vettore): ClientOrariTreno? = when (vettore) {
    Vettore.SBB -> OrariTrasportiSvizzeriClient()
    Vettore.TRENITALIA -> OrariTrenitaliaClient()
    Vettore.ITALO -> OrariItaloClient()
    Vettore.ALTRO -> null
}
