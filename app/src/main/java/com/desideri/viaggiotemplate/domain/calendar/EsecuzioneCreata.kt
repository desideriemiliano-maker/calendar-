package com.desideri.viaggiotemplate.domain.calendar

import java.time.Instant

/** Una singola esecuzione di "Aggiungi al calendario": [id] è l'UUID con cui i suoi eventi sono taggati sul Calendar Provider (vedi [CalendarWriter.eventiPerEsecuzione]). */
data class EsecuzioneCreata(
    val id: String,
    val dataCreazione: Instant
)
