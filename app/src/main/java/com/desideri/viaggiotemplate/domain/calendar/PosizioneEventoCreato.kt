package com.desideri.viaggiotemplate.domain.calendar

import com.desideri.viaggiotemplate.domain.model.IconaLuogo
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.TipoTratta

/**
 * Snapshot immutabile di un [Luogo], congelato nel momento in cui l'evento viene scritto a
 * calendario (vedi [PosizioneEventoCreato]): MAI ri-risolto contro la tabella `luogo` in un
 * momento successivo. Le Tratte sono entità vive — nome, colore, icona e persino l'indirizzo di un
 * Luogo possono cambiare o essere eliminati dopo la creazione — mentre questo record deve restare
 * fedele al viaggio così come pianificato quando l'utente ha premuto "Aggiungi al calendario".
 * [luogoId] è conservato solo per riconoscere due tappe consecutive coincidenti all'interno della
 * stessa esecuzione (l'arrivo di una tratta e la partenza della successiva, vedi
 * [com.desideri.viaggiotemplate.domain.mappa.risolviPercorsoEsecuzione]), mai per una nuova
 * risoluzione contro il database.
 */
data class LuogoCongelato(
    val luogoId: String,
    val nome: String,
    val indirizzo: String?,
    val latitudine: Double?,
    val longitudine: Double?,
    val colore: Int?,
    val icona: IconaLuogo?
)

/** Congela questo Luogo così com'è ora, per scriverlo su [PosizioneEventoCreato] al momento della creazione di un evento. */
fun Luogo.congela(): LuogoCongelato = LuogoCongelato(
    luogoId = id,
    nome = nome,
    indirizzo = indirizzo,
    latitudine = latitudine,
    longitudine = longitudine,
    colore = colore,
    icona = icona
)

/**
 * Posizione congelata di un singolo evento scritto a calendario (una Tratta di un'esecuzione):
 * partenza e arrivo, più il tipo di tratta per l'icona sull'arco tra i due sulla mappa.
 * [partenza]/[arrivo] sono `null` per gli eventi creati prima dell'introduzione di questa
 * funzionalità (colonne aggiunte con la migrazione 16→17 dello schema database, tutte nullable):
 * per quelli non c'è nulla da mostrare sulla mappa.
 */
data class PosizioneEventoCreato(
    val calendarEventId: Long,
    val tipoTratta: TipoTratta?,
    val partenza: LuogoCongelato?,
    val arrivo: LuogoCongelato?
)
