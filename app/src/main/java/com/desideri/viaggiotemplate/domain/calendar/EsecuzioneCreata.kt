package com.desideri.viaggiotemplate.domain.calendar

import java.time.Instant

/**
 * Una singola esecuzione di "Aggiungi al calendario": [id] è l'UUID con cui gli eventi
 * corrispondenti sono associati (vedi EsecuzioneCreataRepository), [inizioPrimoEvento] è l'orario
 * di inizio del più mattiniero tra quegli eventi — il giorno del viaggio, non il momento in cui
 * si è premuto il pulsante.
 */
data class EsecuzioneCreata(
    val id: String,
    val inizioPrimoEvento: Instant
)
