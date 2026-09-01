package com.desideri.viaggiotemplate.domain.calcolo

import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
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

/**
 * Esito del calcolo di inizio/fine per una singola tratta. `daConfermare` è true quando la tratta
 * è TRENO/AEREO ma non ha ancora nessun orario configurato (né pattern ricorrenti né orari
 * fissi): non essendoci alcun dato su cui basarsi, si usa il deadline stesso come placeholder
 * (partenza = arrivo), da sostituire con un orario reale (es. download SBB/Trenitalia) prima di
 * scrivere l'evento a calendario.
 */
private data class RisultatoOrario(
    val inizio: LocalTime,
    val fine: LocalTime,
    val daConfermare: Boolean = false
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
        val daConfermare = BooleanArray(slotsRisolti.size)

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
            val risultato = calcolaIndietro(tratta, deadline)
            inizio[i] = risultato.inizio
            fine[i] = risultato.fine
            daConfermare[i] = risultato.daConfermare
        }

        // Propagazione in avanti: da ciascuna ancora fino alla prossima (esclusa) o alla fine
        indiciAncora.forEachIndexed { posizione, ancoraIndex ->
            val limite = if (posizione + 1 < indiciAncora.size) indiciAncora[posizione + 1] else slotsRisolti.size
            for (i in ancoraIndex + 1 until limite) {
                val tratta = slotsRisolti[i].trattaSelezionata
                val deadline = fine[i - 1]!!.plusMinutes(tratta.margineMinuti.toLong())
                val risultato = calcolaAvanti(tratta, deadline)
                inizio[i] = risultato.inizio
                fine[i] = risultato.fine
                daConfermare[i] = risultato.daConfermare
            }
        }

        // Arrotondamento di ogni blocco calcolato indipendentemente dal vicino (vedi
        // applicaArrotondamento), poi un passaggio in ordine cronologico che vincola i blocchi a
        // non sovrapporsi mai — vedi il commento su vincolaBlocchiNonSovrapposti.
        val blocIniPerSlot = arrayOfNulls<LocalTime>(slotsRisolti.size)
        val blocFinPerSlot = arrayOfNulls<LocalTime>(slotsRisolti.size)
        val stepPerSlot = IntArray(slotsRisolti.size)
        for (i in slotsRisolti.indices) {
            val tratta = slotsRisolti[i].trattaSelezionata
            val (blocIni, blocFin) = applicaArrotondamento(tratta, inizio[i]!!, fine[i]!!)
            blocIniPerSlot[i] = blocIni
            blocFinPerSlot[i] = blocFin
            stepPerSlot[i] = tratta.stepArrotondamentoMinuti.coerceAtLeast(1)
        }
        vincolaBlocchiNonSovrapposti(blocIniPerSlot, blocFinPerSlot, inizio, fine, stepPerSlot)

        return slotsRisolti.mapIndexed { i, slotRisolto ->
            val ini = inizio[i]!!
            val fin = fine[i]!!
            val blocIni = blocIniPerSlot[i]!!
            val blocFin = blocFinPerSlot[i]!!

            val alternative = if (slotRisolto.trattaCandidate.size > 1) {
                slotRisolto.trattaCandidate
                    .filter { it.id != slotRisolto.trattaSelezionata.id }
                    .mapNotNull { candidata ->
                        // runCatching: un candidato TRENO/AEREO senza alcuno slot compatibile (es.
                        // nessun orario configurato ancora, in attesa di un download orari reali)
                        // lancia un'eccezione — non deve far fallire il calcolo dell'intero evento,
                        // altrimenti un solo candidato "rotto" toglierebbe TUTTE le alternative
                        // (incluse quelle valide) e i risultati resterebbero bloccati sul vecchio
                        // stato, impedendo di tornare indietro sulla scelta.
                        runCatching {
                            calcolaAlternativa(
                                candidata = candidata,
                                // Nota: non si esclude i qui neanche se e' diventato un'ancora
                                // manuale (es. dopo "Modifica manualmente" o un download di orario
                                // reale): quel che conta per la posizione delle alternative e' solo
                                // se i precede o segue la PRIMA ancora del template, non se il suo
                                // stesso valore e' stato corretto a mano.
                                èPrimaDellAncora = i < primaAncora,
                                èDopoDellAncora = i > primaAncora,
                                inizioProprio = ini,
                                fineProprio = fin,
                                inizioAdiacentePrecedente = if (i > 0) fine[i - 1] else null,
                                inizioAdiacenteSuccessivo = if (i < slotsRisolti.size - 1) inizio[i + 1] else null,
                                margineSuccessivo = if (i < slotsRisolti.size - 1) slotsRisolti[i + 1].trattaSelezionata.margineMinuti else 0
                            )
                        }.getOrNull()
                    }
            } else emptyList()

            EventoCalcolato(
                templateSlotId = slotRisolto.slot.id,
                tratta = slotRisolto.trattaSelezionata,
                inizioReale = ini,
                fineReale = fin,
                inizioBlocco = blocIni,
                fineBlocco = blocFin,
                alternative = alternative,
                orarioDaConfermare = daConfermare[i]
            )
        }
    }

    /** Calcola inizio/fine di una tratta andando all'indietro da un deadline di arrivo massimo. */
    private fun calcolaIndietro(tratta: Tratta, deadlineArrivoMassimo: LocalTime): RisultatoOrario {
        if (tratta.tipo.usaOrariProgrammati) {
            if (tratta.opzioniOrario.isEmpty() && tratta.orariFissi.isEmpty()) return placeholderSenzaOrario(tratta, deadlineArrivoMassimo)
            val opzione = trovaUltimoSlotConArrivoEntro(tratta, deadlineArrivoMassimo)
                ?: error("Nessuno slot disponibile per ${tratta.nome} entro $deadlineArrivoMassimo")
            return RisultatoOrario(opzione.first, opzione.second)
        }
        val fine = deadlineArrivoMassimo
        val ini = fine.minusMinutes(tratta.durataMinutiReale.toLong())
        return RisultatoOrario(ini, fine)
    }

    /** Calcola inizio/fine di una tratta andando in avanti da un deadline di partenza minimo. */
    private fun calcolaAvanti(tratta: Tratta, deadlinePartenzaMinima: LocalTime): RisultatoOrario {
        if (tratta.tipo.usaOrariProgrammati) {
            if (tratta.opzioniOrario.isEmpty() && tratta.orariFissi.isEmpty()) return placeholderSenzaOrario(tratta, deadlinePartenzaMinima)
            val opzione = trovaPrimoSlotConPartenzaDa(tratta, deadlinePartenzaMinima)
                ?: error("Nessuno slot disponibile per ${tratta.nome} da $deadlinePartenzaMinima")
            return RisultatoOrario(opzione.first, opzione.second)
        }
        val ini = deadlinePartenzaMinima
        val fine = ini.plusMinutes(tratta.durataMinutiReale.toLong())
        return RisultatoOrario(ini, fine)
    }

    /**
     * Tratta TRENO/AEREO senza alcun orario configurato (né pattern ricorrenti né orari fissi):
     * non c'è alcun dato su cui basare un calcolo reale, quindi si lascia partenza = arrivo =
     * deadline come placeholder, segnalato come "da confermare" (vedi [RisultatoOrario]) invece
     * di far fallire l'intero calcolo del template.
     */
    private fun placeholderSenzaOrario(tratta: Tratta, deadline: LocalTime): RisultatoOrario =
        RisultatoOrario(deadline, deadline, daConfermare = true)

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
        val risultato = when {
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
        val (blocIni, blocFin) = applicaArrotondamento(candidata, risultato.inizio, risultato.fine)
        return EventoCalcolato(
            templateSlotId = "",
            tratta = candidata,
            inizioReale = risultato.inizio,
            fineReale = risultato.fine,
            inizioBlocco = blocIni,
            fineBlocco = blocFin,
            orarioDaConfermare = risultato.daConfermare
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

    /**
     * Vincola in-place [blocIni]/[blocFin] (allineati per indice a `slotsRisolti`, quindi già in
     * ordine cronologico) a non sovrapporsi mai col blocco precedente, SENZA MAI rompere
     * l'invariante di contenimento che ogni blocco deve rispettare verso il proprio orario reale
     * ([inizioReale]/[fineReale], anch'essi allineati per indice): `blocIni <= inizioReale` e
     * `blocFin >= fineReale`, sempre — un blocco che inizia dopo che il viaggio reale è già partito
     * non lo rappresenterebbe più in calendario, anche se non si sovrappone più col vicino. Una
     * prima versione di questa funzione violava questa invariante spostando l'inizio del blocco in
     * conflitto fino alla fine di quello precedente, anche oltre la propria partenza reale.
     *
     * Quando l'arrotondamento indipendente di un blocco lo fa iniziare prima che il precedente sia
     * finito — il bug segnalato, quando lo scarto reale tra due tratte è più piccolo
     * dell'espansione combinata dei due arrotondamenti (es. arrivo reale 17:31 arrotondato per
     * eccesso a 17:40, partenza reale successiva 17:36 arrotondata per difetto a 17:30) — il nuovo
     * confine condiviso tra i due blocchi va scelto DENTRO la finestra reale libera tra i due
     * eventi, `[fineReale(i-1), inizioReale(i)]` (nell'esempio, `[17:31, 17:36]`): è l'unica zona in
     * cui spostarlo non intacca il contenimento di nessuno dei due blocchi. Dentro quella finestra
     * si preferisce un valore arrotondato per difetto a metà dello step più piccolo tra i due
     * blocchi (con lo step di default, 10, la metà è 5: nell'esempio produce 17:35, non un punto
     * arbitrario), per restare leggibile come gli altri arrotondamenti; se nessun valore così
     * arrotondato cade nella finestra (finestra più stretta della granularità) si usa direttamente
     * il suo estremo inferiore, la fine reale del blocco precedente.
     *
     * Caso limite: se la finestra è vuota o negativa (`inizioReale(i) < fineReale(i-1)` — nei dati,
     * la tratta successiva parte prima che la precedente sia arrivata) non esiste alcun confine che
     * soddisfi il contenimento di entrambi i blocchi: si privilegia il contenimento — mai un blocco
     * che non copre il proprio orario reale — e si accetta la sovrapposizione residua, che a quel
     * punto riflette un'incoerenza nei dati reali stessi, non un difetto dell'arrotondamento che
     * questa funzione possa correggere.
     */
    private fun vincolaBlocchiNonSovrapposti(
        blocIni: Array<LocalTime?>,
        blocFin: Array<LocalTime?>,
        inizioReale: Array<LocalTime?>,
        fineReale: Array<LocalTime?>,
        stepPerSlot: IntArray
    ) {
        for (i in 1 until blocIni.size) {
            if (blocIni[i]!! >= blocFin[i - 1]!!) continue // nessun conflitto, niente da correggere

            val finestraInizio = fineReale[i - 1]!!
            val finestraFine = inizioReale[i]!!
            if (finestraFine < finestraInizio) continue // finestra vuota/negativa: dati incoerenti, vedi doc sopra

            val granularita = maxOf(1, minOf(stepPerSlot[i - 1], stepPerSlot[i]) / 2)
            val candidato = arrotondaGiu(finestraFine, granularita)
            val confine = if (candidato >= finestraInizio) candidato else finestraInizio

            blocFin[i - 1] = confine
            blocIni[i] = confine
        }
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
     * Tra tutti gli slot generabili dai pattern ricorrenti E dagli orari fissi della tratta,
     * trova quello con partenza >= deadline, scegliendo il più vicino (il primo utile). Unendo
     * i due pool di candidati, il motore sceglie automaticamente il migliore tra fisso e
     * ricorrente, con la stessa identica logica.
     */
    private fun trovaPrimoSlotConPartenzaDa(
        tratta: Tratta,
        deadline: LocalTime
    ): Pair<LocalTime, LocalTime>? {
        val candidati = generaCandidati(tratta.opzioniOrario, deadline.hour - 2, 36) +
            generaCandidatiFissi(tratta.orariFissi, deadline.hour - 2, 36)
        // Primo criterio: partenza più presto possibile dopo il deadline (meno attesa dopo la
        // tratta precedente). A parità di partenza (es. lo stesso treno reale è anche un'opzione
        // ricorrente, o due pattern coincidono), l'arrivo più presto è comunque preferibile.
        return candidati.filter { it.first.toMinutiAssoluti() >= deadline.toMinutiAssoluti() }
            .minWithOrNull(compareBy({ it.first.toMinutiAssoluti() }, { it.second.toMinutiAssoluti() }))
    }

    /**
     * Tra tutti gli slot generabili dai pattern ricorrenti E dagli orari fissi della tratta,
     * trova quello con arrivo <= deadline, scegliendo il più tardivo (l'ultimo utile, per
     * massimizzare il margine reale). Vedi [trovaPrimoSlotConPartenzaDa] per la logica di unione.
     */
    private fun trovaUltimoSlotConArrivoEntro(
        tratta: Tratta,
        deadline: LocalTime
    ): Pair<LocalTime, LocalTime>? {
        val candidati = generaCandidati(tratta.opzioniOrario, deadline.hour - 40, 44) +
            generaCandidatiFissi(tratta.orariFissi, deadline.hour - 40, 44)
        // Primo criterio: arrivo più tardivo possibile entro il deadline (massimizza il margine
        // reale). A parità di arrivo (es. un orario fisso scaricato arriva alla stessa ora di
        // un'opzione ricorrente, ma parte più tardi), la partenza più tardiva è preferibile:
        // meno attesa per chi viaggia, a parità di risultato.
        return candidati.filter { it.second.toMinutiAssoluti() <= deadline.toMinutiAssoluti() }
            .maxWithOrNull(compareBy({ it.second.toMinutiAssoluti() }, { it.first.toMinutiAssoluti() }))
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

    /**
     * Genera le occorrenze di ciascun orario fisso per ogni "giorno" coperto dalla finestra
     * [oraIniziale, oraIniziale+numeroOre): un orario fisso, a differenza di un pattern
     * ricorrente, si presenta una sola volta al giorno, non a ogni ora. Se l'arrivo è prima
     * della partenza, si considera oltre mezzanotte.
     */
    private fun generaCandidatiFissi(
        orariFissi: List<OrarioFisso>,
        oraIniziale: Int,
        numeroOre: Int
    ): List<Pair<LocalTime, LocalTime>> {
        if (orariFissi.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<LocalTime, LocalTime>>()
        val giornoIniziale = Math.floorDiv(oraIniziale, 24)
        val giornoFinale = Math.floorDiv(oraIniziale + numeroOre - 1, 24)
        for (giorno in giornoIniziale..giornoFinale) {
            for (fisso in orariFissi) {
                val partenzaMin = giorno * 1440 + fisso.partenza.hour * 60 + fisso.partenza.minute
                var arrivoMin = giorno * 1440 + fisso.arrivo.hour * 60 + fisso.arrivo.minute
                if (arrivoMin < partenzaMin) arrivoMin += 1440
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
