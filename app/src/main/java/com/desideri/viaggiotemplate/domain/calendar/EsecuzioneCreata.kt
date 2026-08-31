package com.desideri.viaggiotemplate.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Una singola esecuzione di "Aggiungi al calendario": [id] è l'UUID con cui gli eventi
 * corrispondenti sono associati (vedi EsecuzioneCreataRepository), [inizioPrimoEvento] è l'orario
 * di inizio del più mattiniero tra quegli eventi — il giorno del viaggio, non il momento in cui
 * si è premuto il pulsante.
 */
data class EsecuzioneCreata(
    val id: String,
    val inizioPrimoEvento: Instant,
    val templateNome: String? = null,
    val templateColore: Int? = null
)

/** Data (senza orario) del viaggio a cui appartiene questa esecuzione, nel fuso orario del dispositivo. */
fun EsecuzioneCreata.dataViaggio(zona: ZoneId = ZoneId.systemDefault()): LocalDate =
    inizioPrimoEvento.atZone(zona).toLocalDate()

/**
 * True se il viaggio di questa esecuzione è già passato. Confronto per sola data, senza orario:
 * un'esecuzione con un evento ancora in corso oggi (data odierna, orario già trascorso) non va
 * considerata passata (vedi fix 0a24e73). Unica fonte di verità per questo confronto: riusata sia
 * dal filtro "mostra passati" di [com.desideri.viaggiotemplate.ui.eventicreati.StatoEventiCreati]
 * sia dall'eliminazione in blocco degli eventi passati dal menu principale.
 */
fun EsecuzioneCreata.passata(oggi: LocalDate = LocalDate.now()): Boolean = dataViaggio().isBefore(oggi)
