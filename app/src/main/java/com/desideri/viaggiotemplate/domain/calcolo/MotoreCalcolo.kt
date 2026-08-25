package com.desideri.viaggiotemplate.domain.calcolo

import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import java.time.LocalTime

/**
 * Rappresenta uno slot del template con la tratta scelta già risolta (e le tratte
 * candidate alternative, se presenti), pronto per il motore di calcolo.
 */
data class SlotRisolto(
    val slot: TemplateSlot,
    val trattaSelezionata: Tratta,
    val trattaCandidate: List<Tratta>
)

class MotoreCalcolo {

    /**
     * Calcola gli orari di tutte le tratte del template, propagando da una o più ancore.
     * Con più ancore (es. l'ancora del template più eventuali orari corretti a mano
     * dall'utente), ciascuna propaga in avanti fino alla prossima ancora (esclusa) o
     * alla fine del template; solo la primissima ancora (in ordine di indice) propaga
     * anche all'indietro, fino all'inizio del template.
     *
     * @param slotsRisolti tratte del template in ordine di viaggio, con la tratta selezionata già risolta
     * @param ancore mappa indice (in `slotsRisolti`) -> (orario partenza reale, orario arrivo reale) fissati dall'utente
     */
    fun calcola(
        slotsRisolti: List<SlotRisolto>,
        ancore: Map<Int, Pair<LocalTime, LocalTime>>
    ): List<EventoCalcolato> {
        require(slotsRisolti.isNotEmpty()) { "Il template non ha tratte" }
        require(ancore.isNotEmpty()) { "Serve almeno una tratta ancora" }
        require(ancore.keys.all { it in slotsRisolti.indices }) { "Indice ancora fuori range" }

        val inizio = arrayOfNulls<LocalTime>(slotsRisolti.size)
        val fine = arrayOfNulls<LocalTime>(slotsRisolti.size)

        ancore.forEach { (indice, orari) ->
            inizio[indice] = orari.first
            fine[indice] = orari.second
        }

        val indiciAncora = ancore.keys.sorted()
        val primaAncora = indiciAncora.first()

        // Propagazione all'indietro: dalla prima ancora fino all'inizio del template
        for (i in primaAncora - 1 downTo 0) {
            val trattaSuccessiva = slotsRisolti[i + 1].trattaSelezionata
            val deadline = inizio[i + 1]!!.minusMinutes(trattaSuccessiva.margineMinuti.toLong())
            val tratta = slotsRisolti[i].trattaSelezionata
            val (ini, fin) = calcolaIndietro(tratta, deadline)
            inizio[i] = ini
            fine[i] = fin
        }

        // Propagazione in avanti: da ciascuna ancora fino alla prossima (esclusa) o alla fine
        indiciAncora.forEachIndexed { posizione, ancoraIndex ->
            val limite = if (posizione + 1 < indiciAncora.size) indiciAncora[posizione + 1] else slotsRisolti.size
            for (i in ancoraIndex + 1 until limite) {
                val tratta = slotsRisolti[i].trattaSelezionata
                val deadline = fine[i - 1]!!.plusMinutes(tratta.margineMinuti.toLong())
                val (ini, fin) = calcolaAvanti(tratta, deadline)
                inizio[i] = ini
                fine[i] = fin
            }
        }

        return slotsRisolti.mapIndexed { i, slotRisolto ->
            val ini = inizio[i]!!
            val fin = fine[i]!!
            val (blocIni, blocFin) = applicaArrotondamento(slotRisolto.trattaSelezionata, ini, fin)

            val alternative = if (slotRisolto.trattaCandidate.size > 1) {
                slotRisolto.trattaCandidate
                    .filter { it.id != slotRisolto.trattaSelezionata.id }
                    .mapNotNull { candidata ->
                        calcolaAlternativa(
                            candidata = candidata,
                            èPrimaDellAncora = i < primaAncora,
                            èDopoDellAncora = i > primaAncora && i !in ancore.keys,
                            inizioProprio = ini,
                            fineProprio = fin,
                            inizioAdiacentePrecedente = if (i > 0) fine[i - 1] else null,
                            inizioAdiacenteSuccessivo = if (i < slotsRisolti.size - 1) inizio[i + 1] else null,
                            margineSuccessivo = if (i < slotsRisolti.size - 1) slotsRisolti[i + 1].trattaSelezionata.margineMinuti else 0
                        )
                    }
            } else emptyList()

            EventoCalcolato(
                templateSlotId = slotRisolto.slot.id,
                tratta = slotRisolto.trattaSelezionata,
                inizioReale = ini,
                fineReale = fin,
                inizioBlocco = blocIni,
                fineBlocco = blocFin,
                alternative = alternative
            )
        }
    }

    /** Calcola inizio/fine di una tratta andando all'indietro da un deadline di arrivo massimo. */
    private fun calcolaIndietro(tratta: Tratta, deadlineArrivoMassimo: LocalTime): Pair<LocalTime, LocalTime> {
        return if (tratta.tipo == TipoTratta.TRENO) {
            val opzione = trovaUltimoSlotConArrivoEntro(tratta.opzioniOrario, deadlineArrivoMassimo)
                ?: error("Nessuno slot disponibile per ${tratta.nome} entro $deadlineArrivoMassimo")
            opzione
        } else {
            val fine = deadlineArrivoMassimo
            val ini = fine.minusMinutes(tratta.durataMinutiReale.toLong())
            ini to fine
        }
    }

    /** Calcola inizio/fine di una tratta andando in avanti da un deadline di partenza minimo. */
    private fun calcolaAvanti(tratta: Tratta, deadlinePartenzaMinima: LocalTime): Pair<LocalTime, LocalTime> {
        return if (tratta.tipo == TipoTratta.TRENO) {
            val opzione = trovaPrimoSlotConPartenzaDa(tratta.opzioniOrario, deadlinePartenzaMinima)
                ?: error("Nessuno slot disponibile per ${tratta.nome} da $deadlinePartenzaMinima")
            opzione
        } else {
            val ini = deadlinePartenzaMinima
            val fine = ini.plusMinutes(tratta.durataMinutiReale.toLong())
            ini to fine
        }
    }

    private fun calcolaAlternativa(
        candidata: Tratta,
        èPrimaDellAncora: Boolean,
        èDopoDellAncora: Boolean,
        inizioProprio: LocalTime,
        fineProprio: LocalTime,
        inizioAdiacentePrecedente: LocalTime?,
        inizioAdiacenteSuccessivo: LocalTime?,
        margineSuccessivo: Int
    ): EventoCalcolato? {
        val (ini, fin) = when {
            èDopoDellAncora && inizioAdiacentePrecedente != null -> {
                val deadline = inizioAdiacentePrecedente.plusMinutes(candidata.margineMinuti.toLong())
                calcolaAvanti(candidata, deadline)
            }
            èPrimaDellAncora && inizioAdiacenteSuccessivo != null -> {
                val deadline = inizioAdiacenteSuccessivo.minusMinutes(margineSuccessivo.toLong())
                calcolaIndietro(candidata, deadline)
            }
            else -> return null
        }
        val (blocIni, blocFin) = applicaArrotondamento(candidata, ini, fin)
        return EventoCalcolato(
            templateSlotId = "",
            tratta = candidata,
            inizioReale = ini,
            fineReale = fin,
            inizioBlocco = blocIni,
            fineBlocco = blocFin
        )
    }

    private fun applicaArrotondamento(tratta: Tratta, inizio: LocalTime, fine: LocalTime): Pair<LocalTime, LocalTime> {
        val step = tratta.stepArrotondamentoMinuti.coerceAtLeast(1)
        val blocIni = when (tratta.arrotondaInizio) {
            Arrotondamento.NESSUNO -> inizio
            Arrotondamento.DIFETTO -> arrotondaGiu(inizio, step)
            Arrotondamento.ECCESSO -> arrotondaSu(inizio, step)
        }
        val blocFin = when (tratta.arrotondaFine) {
            Arrotondamento.NESSUNO -> fine
            Arrotondamento.DIFETTO -> arrotondaGiu(fine, step)
            Arrotondamento.ECCESSO -> arrotondaSu(fine, step)
        }
        return blocIni to blocFin
    }

    private fun arrotondaGiu(t: LocalTime, step: Int): LocalTime {
        val totMin = t.hour * 60 + t.minute
        val giu = (totMin / step) * step
        return LocalTime.of((giu / 60) % 24, giu % 60)
    }

    private fun arrotondaSu(t: LocalTime, step: Int): LocalTime {
        val totMin = t.hour * 60 + t.minute
        val su = ((totMin + step - 1) / step) * step
        return LocalTime.of((su / 60) % 24, su % 60)
    }

    /**
     * Tra tutti gli slot generabili dai pattern, trova quello con partenza >= deadline,
     * scegliendo il più vicino (il primo utile).
     */
    private fun trovaPrimoSlotConPartenzaDa(
        opzioni: List<OpzioneOrario>,
        deadline: LocalTime
    ): Pair<LocalTime, LocalTime>? {
        val candidati = generaCandidati(opzioni, deadline.hour - 2, 36)
        return candidati.filter { it.first.toMinutiAssoluti() >= deadline.toMinutiAssoluti() }
            .minByOrNull { it.first.toMinutiAssoluti() }
    }

    /**
     * Tra tutti gli slot generabili dai pattern, trova quello con arrivo <= deadline,
     * scegliendo il più tardivo (l'ultimo utile, per massimizzare il margine reale).
     */
    private fun trovaUltimoSlotConArrivoEntro(
        opzioni: List<OpzioneOrario>,
        deadline: LocalTime
    ): Pair<LocalTime, LocalTime>? {
        val candidati = generaCandidati(opzioni, deadline.hour - 40, 44)
        return candidati.filter { it.second.toMinutiAssoluti() <= deadline.toMinutiAssoluti() }
            .maxByOrNull { it.second.toMinutiAssoluti() }
    }

    /**
     * Genera tutte le combinazioni partenza/arrivo per una finestra di ore, rispettando
     * la cadenza/parità di ciascuna opzione. Le ore possono uscire da [0,23]: si lavora
     * in "minuti assoluti" e si normalizza solo alla fine con modulo 1440.
     */
    private fun generaCandidati(
        opzioni: List<OpzioneOrario>,
        oraIniziale: Int,
        numeroOre: Int
    ): List<Pair<LocalTime, LocalTime>> {
        val out = mutableListOf<Pair<LocalTime, LocalTime>>()
        for (h in oraIniziale until oraIniziale + numeroOre) {
            for (opzione in opzioni) {
                val oraNormalizzata = ((h % 24) + 24) % 24
                if (!opzione.èAttivoNellOra(oraNormalizzata)) continue
                val partenzaMin = h * 60 + opzione.minutoPartenza
                val arrivoMin = h * 60 + opzione.offsetOreArrivo * 60 + opzione.minutoArrivo
                out += minutiToLocalTime(partenzaMin) to minutiToLocalTime(arrivoMin)
            }
        }
        return out
    }

    private fun minutiToLocalTime(minutiAssoluti: Int): LocalTime {
        val norm = ((minutiAssoluti % 1440) + 1440) % 1440
        return LocalTime.of(norm / 60, norm % 60)
    }

    private fun LocalTime.toMinutiAssoluti(): Int = hour * 60 + minute
}
