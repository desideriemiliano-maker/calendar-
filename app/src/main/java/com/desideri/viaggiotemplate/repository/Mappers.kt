package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaConOpzioni
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity
import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import java.time.LocalTime

fun TrattaConOpzioni.toDomain(): Tratta = Tratta(
    id = tratta.id,
    nome = tratta.nome,
    tipo = TipoTratta.valueOf(tratta.tipo),
    luogoPartenza = tratta.luogoPartenza,
    luogoArrivo = tratta.luogoArrivo,
    durataMinutiReale = tratta.durataMinutiReale,
    margineMinuti = tratta.margineMinuti,
    arrotondaInizio = Arrotondamento.valueOf(tratta.arrotondaInizio),
    arrotondaFine = Arrotondamento.valueOf(tratta.arrotondaFine),
    stepArrotondamentoMinuti = tratta.stepArrotondamentoMinuti,
    titoloTemplate = tratta.titoloTemplate,
    opzioniOrario = opzioni.map { it.toDomain() },
    ordine = tratta.ordine,
    colore = tratta.colore,
    orarioInizioDefault = tratta.orarioInizioDefaultMinuti?.let { LocalTime.of(it / 60, it % 60) }
)

fun OpzioneOrarioEntity.toDomain(): OpzioneOrario = OpzioneOrario(
    id = id,
    minutoPartenza = minutoPartenza,
    cadenzaOre = cadenzaOre,
    parita = parita,
    offsetOreArrivo = offsetOreArrivo,
    minutoArrivo = minutoArrivo,
    etichetta = etichetta
)

fun Tratta.toEntity(): TrattaEntity = TrattaEntity(
    id = id,
    nome = nome,
    tipo = tipo.name,
    luogoPartenza = luogoPartenza,
    luogoArrivo = luogoArrivo,
    durataMinutiReale = durataMinutiReale,
    margineMinuti = margineMinuti,
    arrotondaInizio = arrotondaInizio.name,
    arrotondaFine = arrotondaFine.name,
    stepArrotondamentoMinuti = stepArrotondamentoMinuti,
    titoloTemplate = titoloTemplate,
    ordine = ordine,
    colore = colore,
    orarioInizioDefaultMinuti = orarioInizioDefault?.let { it.hour * 60 + it.minute }
)

fun Tratta.opzioniToEntity(): List<OpzioneOrarioEntity> = opzioniOrario.map {
    OpzioneOrarioEntity(
        id = it.id,
        trattaId = id,
        minutoPartenza = it.minutoPartenza,
        cadenzaOre = it.cadenzaOre,
        parita = it.parita,
        offsetOreArrivo = it.offsetOreArrivo,
        minutoArrivo = it.minutoArrivo,
        etichetta = it.etichetta
    )
}
