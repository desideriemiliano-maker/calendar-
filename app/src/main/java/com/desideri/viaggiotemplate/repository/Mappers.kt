package com.desideri.viaggiotemplate.repository

import com.desideri.viaggiotemplate.data.local.entities.LuogoEntity
import com.desideri.viaggiotemplate.data.local.entities.OpzioneOrarioEntity
import com.desideri.viaggiotemplate.data.local.entities.OrarioFissoEntity
import com.desideri.viaggiotemplate.data.local.entities.TrattaConOpzioni
import com.desideri.viaggiotemplate.data.local.entities.TrattaEntity
import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.IconaLuogo
import com.desideri.viaggiotemplate.domain.model.Luogo
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import com.desideri.viaggiotemplate.domain.model.Vettore
import java.time.LocalTime

fun LuogoEntity.toDomain(): Luogo = Luogo(id = id, nome = nome, indirizzo = indirizzo, latitudine = latitudine, longitudine = longitudine, colore = colore, ordine = ordine, icona = icona?.let { IconaLuogo.valueOf(it) })

fun Luogo.toEntity(): LuogoEntity = LuogoEntity(id = id, nome = nome, indirizzo = indirizzo, latitudine = latitudine, longitudine = longitudine, colore = colore, ordine = ordine, icona = icona?.name)

fun TrattaConOpzioni.toDomain(): Tratta = Tratta(
    id = tratta.id,
    nome = tratta.nome,
    tipo = TipoTratta.valueOf(tratta.tipo),
    luogoPartenzaId = tratta.luogoPartenzaId,
    luogoArrivoId = tratta.luogoArrivoId,
    luogoPartenza = luogoPartenza.nome,
    luogoArrivo = luogoArrivo.nome,
    indirizzoArrivo = if (tratta.tipo == TipoTratta.AUTO.name) luogoArrivo.indirizzo?.takeIf { it.isNotBlank() } else null,
    latitudineArrivo = if (tratta.tipo == TipoTratta.AUTO.name) luogoArrivo.latitudine else null,
    longitudineArrivo = if (tratta.tipo == TipoTratta.AUTO.name) luogoArrivo.longitudine else null,
    durataMinutiReale = tratta.durataMinutiReale,
    margineMinuti = tratta.margineMinuti,
    arrotondaInizio = Arrotondamento.valueOf(tratta.arrotondaInizio),
    arrotondaFine = Arrotondamento.valueOf(tratta.arrotondaFine),
    stepArrotondamentoMinuti = tratta.stepArrotondamentoMinuti,
    titoloTemplate = tratta.titoloTemplate,
    opzioniOrario = opzioni.map { it.toDomain() },
    orariFissi = orariFissi.map { it.toDomain() },
    vettore = tratta.vettore?.let { Vettore.valueOf(it) },
    ordine = tratta.ordine,
    colore = tratta.colore,
    orarioInizioDefault = tratta.orarioInizioDefaultMinuti?.let { LocalTime.of(it / 60, it % 60) },
    notifica = Notifica.valueOf(tratta.notifica)
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

fun OrarioFissoEntity.toDomain(): OrarioFisso = OrarioFisso(
    id = id,
    partenza = LocalTime.of(partenzaMinuti / 60, partenzaMinuti % 60),
    arrivo = LocalTime.of(arrivoMinuti / 60, arrivoMinuti % 60),
    etichetta = etichetta
)

fun Tratta.toEntity(): TrattaEntity = TrattaEntity(
    id = id,
    nome = nome,
    tipo = tipo.name,
    luogoPartenzaId = luogoPartenzaId,
    luogoArrivoId = luogoArrivoId,
    durataMinutiReale = durataMinutiReale,
    margineMinuti = margineMinuti,
    arrotondaInizio = arrotondaInizio.name,
    arrotondaFine = arrotondaFine.name,
    stepArrotondamentoMinuti = stepArrotondamentoMinuti,
    titoloTemplate = titoloTemplate,
    ordine = ordine,
    colore = colore,
    orarioInizioDefaultMinuti = orarioInizioDefault?.let { it.hour * 60 + it.minute },
    vettore = vettore?.name,
    notifica = notifica.name
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

fun Tratta.orariFissiToEntity(): List<OrarioFissoEntity> = orariFissi.map {
    OrarioFissoEntity(
        id = it.id,
        trattaId = id,
        partenzaMinuti = it.partenza.hour * 60 + it.partenza.minute,
        arrivoMinuti = it.arrivo.hour * 60 + it.arrivo.minute,
        etichetta = it.etichetta
    )
}
