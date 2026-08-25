package com.desideri.viaggiotemplate.domain.calcolo

import com.desideri.viaggiotemplate.domain.model.Arrotondamento
import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Test del motore di calcolo puro Kotlin (nessun Android/emulatore richiesto).
 * Le aspettative sono calcolate a mano seguendo la stessa logica descritta nel
 * design doc, per verificare che il porting sia fedele.
 */
class MotoreCalcoloTest {

    private val motore = MotoreCalcolo()

    private fun trattaAuto(
        id: String,
        durata: Int,
        margine: Int,
        arrotondaInizio: Arrotondamento = Arrotondamento.DIFETTO,
        arrotondaFine: Arrotondamento = Arrotondamento.ECCESSO,
        step: Int = 10
    ) = Tratta(
        id = id, nome = id, tipo = TipoTratta.AUTO,
        luogoPartenza = "P-$id", luogoArrivo = "A-$id",
        durataMinutiReale = durata, margineMinuti = margine,
        arrotondaInizio = arrotondaInizio, arrotondaFine = arrotondaFine,
        stepArrotondamentoMinuti = step
    )

    private fun trattaTreno(
        id: String,
        margine: Int,
        opzioni: List<OpzioneOrario>,
        orariFissi: List<OrarioFisso> = emptyList()
    ) = Tratta(
        id = id, nome = id, tipo = TipoTratta.TRENO,
        luogoPartenza = "P-$id", luogoArrivo = "A-$id",
        durataMinutiReale = 0, margineMinuti = margine,
        opzioniOrario = opzioni, orariFissi = orariFissi
    )

    private fun slot(id: String, ordine: Int, ancora: Boolean, selezionataId: String, candidatiIds: List<String> = listOf(selezionataId)) =
        TemplateSlot(id = id, ordine = ordine, ancora = ancora, trattaCandidatiIds = candidatiIds, trattaSelezionataId = selezionataId)

    private fun ancora(indice: Int, inizio: LocalTime, fine: LocalTime) = mapOf(indice to (inizio to fine))

    @Test
    fun `slot ancora singolo ritorna gli orari forniti e li arrotonda per il blocco`() {
        val tratta = trattaAuto("ancora", durata = 15, margine = 5)
        val risolto = SlotRisolto(slot("s0", 0, ancora = true, selezionataId = tratta.id), tratta, listOf(tratta))

        val eventi = motore.calcola(listOf(risolto), ancora(0, LocalTime.of(9, 7), LocalTime.of(9, 22)))

        assertEquals(1, eventi.size)
        val ev = eventi[0]
        assertEquals(LocalTime.of(9, 7), ev.inizioReale)
        assertEquals(LocalTime.of(9, 22), ev.fineReale)
        assertEquals(LocalTime.of(9, 0), ev.inizioBlocco)   // DIFETTO, step 10 -> arrotonda giù
        assertEquals(LocalTime.of(9, 30), ev.fineBlocco)    // ECCESSO, step 10 -> arrotonda su
        assertTrue(ev.titolo().contains("09:07"))
        assertTrue(ev.titolo().contains("09:22"))
    }

    @Test
    fun `arrotondaInizio e arrotondaFine NESSUNO lasciano l'orario invariato`() {
        val tratta = trattaAuto("t", durata = 10, margine = 0, arrotondaInizio = Arrotondamento.NESSUNO, arrotondaFine = Arrotondamento.NESSUNO)
        val risolto = SlotRisolto(slot("s0", 0, ancora = true, selezionataId = tratta.id), tratta, listOf(tratta))

        val ev = motore.calcola(listOf(risolto), ancora(0, LocalTime.of(8, 7), LocalTime.of(8, 17))).single()

        assertEquals(LocalTime.of(8, 7), ev.inizioBlocco)
        assertEquals(LocalTime.of(8, 17), ev.fineBlocco)
    }

    @Test
    fun `propagazione indietro per tratta TRENO sceglie lo slot piu tardivo che rispetta il margine`() {
        // Treno che parte al minuto 43 di ogni ora e arriva al minuto 29 dell'ora dopo.
        val opzione = OpzioneOrario(id = "o1", minutoPartenza = 43, offsetOreArrivo = 1, minutoArrivo = 29)
        val treno = trattaTreno("treno", margine = 20, opzioni = listOf(opzione))
        val ancora = trattaAuto("ancora", durata = 0, margine = 20)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = false, selezionataId = treno.id), treno, listOf(treno)),
            SlotRisolto(slot("s1", 1, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora))
        )

        // deadline arrivo = inizio ancora (10:00) - margine ancora (20) = 9:40
        // slot 8:43 -> 9:29 arriva entro il deadline; lo slot successivo 9:43 -> 10:29 no.
        val eventi = motore.calcola(risolti, ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)))

        assertEquals(LocalTime.of(8, 43), eventi[0].inizioReale)
        assertEquals(LocalTime.of(9, 29), eventi[0].fineReale)
    }

    @Test
    fun `propagazione avanti per tratta TRENO sceglie il primo slot che rispetta il margine`() {
        val opzione = OpzioneOrario(id = "o1", minutoPartenza = 43, offsetOreArrivo = 1, minutoArrivo = 29)
        val ancora = trattaAuto("ancora", durata = 0, margine = 0)
        val treno = trattaTreno("treno", margine = 20, opzioni = listOf(opzione))

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora)),
            SlotRisolto(slot("s1", 1, ancora = false, selezionataId = treno.id), treno, listOf(treno))
        )

        // deadline partenza = fine ancora (9:00) + margine treno (20) = 9:20 -> primo slot utile 9:43 -> 10:29
        val eventi = motore.calcola(risolti, ancora(0, LocalTime.of(8, 30), LocalTime.of(9, 0)))

        assertEquals(LocalTime.of(9, 43), eventi[1].inizioReale)
        assertEquals(LocalTime.of(10, 29), eventi[1].fineReale)
    }

    @Test
    fun `template a tre tratte propaga correttamente indietro e avanti dall'ancora`() {
        val prima = trattaAuto("prima", durata = 15, margine = 5)
        val ancora = trattaAuto("ancora", durata = 0, margine = 20)
        val dopo = trattaAuto("dopo", durata = 30, margine = 5)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = false, selezionataId = prima.id), prima, listOf(prima)),
            SlotRisolto(slot("s1", 1, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora)),
            SlotRisolto(slot("s2", 2, ancora = false, selezionataId = dopo.id), dopo, listOf(dopo))
        )

        val eventi = motore.calcola(risolti, ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)))

        // indietro: fine = 10:00 - 20 = 9:40; inizio = 9:40 - 15 = 9:25
        assertEquals(LocalTime.of(9, 25), eventi[0].inizioReale)
        assertEquals(LocalTime.of(9, 40), eventi[0].fineReale)

        // l'ancora passa invariata
        assertEquals(LocalTime.of(10, 0), eventi[1].inizioReale)
        assertEquals(LocalTime.of(10, 20), eventi[1].fineReale)

        // avanti: inizio = 10:20 + 5 = 10:25; fine = 10:25 + 30 = 10:55
        assertEquals(LocalTime.of(10, 25), eventi[2].inizioReale)
        assertEquals(LocalTime.of(10, 55), eventi[2].fineReale)
    }

    @Test
    fun `alternative dopo l'ancora usano ciascuna il proprio margine e la propria durata`() {
        // Scenario Roma Termini -> Pomezia／Albano／Pavona: stessa tratta ancora, tre destinazioni alternative.
        val ancora = trattaAuto("milano-roma", durata = 0, margine = 0)
        val pomezia = trattaAuto("pomezia", durata = 30, margine = 5)
        val albano = trattaAuto("albano", durata = 45, margine = 10)
        val pavona = trattaAuto("pavona", durata = 20, margine = 5)

        val slotAncora = slot("s0", 0, ancora = true, selezionataId = ancora.id)
        val slotDestinazione = slot(
            "s1", 1, ancora = false,
            selezionataId = pomezia.id,
            candidatiIds = listOf(pomezia.id, albano.id, pavona.id)
        )

        val risolti = listOf(
            SlotRisolto(slotAncora, ancora, listOf(ancora)),
            SlotRisolto(slotDestinazione, pomezia, listOf(pomezia, albano, pavona))
        )

        val eventi = motore.calcola(risolti, ancora(0, LocalTime.of(8, 0), LocalTime.of(10, 0)))

        val destinazione = eventi[1]
        assertEquals(LocalTime.of(10, 5), destinazione.inizioReale)   // 10:00 + margine pomezia (5)
        assertEquals(LocalTime.of(10, 35), destinazione.fineReale)    // 10:05 + durata pomezia (30)

        assertEquals(2, destinazione.alternative.size)

        val alb = destinazione.alternative.first { it.tratta.id == "albano" }
        assertEquals(LocalTime.of(10, 10), alb.inizioReale) // 10:00 + margine albano (10)
        assertEquals(LocalTime.of(10, 55), alb.fineReale)   // 10:10 + durata albano (45)

        val pav = destinazione.alternative.first { it.tratta.id == "pavona" }
        assertEquals(LocalTime.of(10, 5), pav.inizioReale)  // 10:00 + margine pavona (5)
        assertEquals(LocalTime.of(10, 25), pav.fineReale)   // 10:05 + durata pavona (20)
    }

    @Test
    fun `alternative prima dell'ancora usano il margine della tratta successiva selezionata, non il proprio`() {
        val selezionata = trattaAuto("selezionata", durata = 15, margine = 5)
        val alternativa = trattaAuto("alternativa", durata = 25, margine = 10)
        val ancora = trattaAuto("ancora", durata = 0, margine = 20)

        val slot0 = slot("s0", 0, ancora = false, selezionataId = selezionata.id, candidatiIds = listOf(selezionata.id, alternativa.id))
        val slot1 = slot("s1", 1, ancora = true, selezionataId = ancora.id)

        val risolti = listOf(
            SlotRisolto(slot0, selezionata, listOf(selezionata, alternativa)),
            SlotRisolto(slot1, ancora, listOf(ancora))
        )

        val eventi = motore.calcola(risolti, ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)))

        // deadline comune = 10:00 - margine ANCORA (20) = 9:40, non il margine della candidata
        val alt = eventi[0].alternative.single()
        assertEquals(LocalTime.of(9, 40), alt.fineReale)
        assertEquals(LocalTime.of(9, 15), alt.inizioReale) // 9:40 - durata alternativa (25)
    }

    @Test
    fun `lancia eccezione se non esiste uno slot TRENO compatibile con il deadline`() {
        val trenoSenzaSlot = trattaTreno("treno", margine = 20, opzioni = emptyList())
        val ancora = trattaAuto("ancora", durata = 0, margine = 20)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = false, selezionataId = trenoSenzaSlot.id), trenoSenzaSlot, listOf(trenoSenzaSlot)),
            SlotRisolto(slot("s1", 1, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora))
        )

        assertThrows(IllegalStateException::class.java) {
            motore.calcola(risolti, ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)))
        }
    }

    @Test
    fun `lancia eccezione se il template e' vuoto, senza ancore, o con indice ancora fuori range`() {
        assertThrows(IllegalArgumentException::class.java) {
            motore.calcola(emptyList(), ancora(0, LocalTime.NOON, LocalTime.NOON))
        }

        val tratta = trattaAuto("t", durata = 10, margine = 0)
        val risolto = SlotRisolto(slot("s0", 0, ancora = true, selezionataId = tratta.id), tratta, listOf(tratta))

        assertThrows(IllegalArgumentException::class.java) {
            motore.calcola(listOf(risolto), emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            motore.calcola(listOf(risolto), ancora(5, LocalTime.NOON, LocalTime.NOON))
        }
    }

    @Test
    fun `con piu ancore, i tratti intermedi si calcolano in avanti dalla loro ancora, non a ritroso dalla successiva`() {
        // Template a 4 tratte: s0 (prima), s1 = ancora del template, s2 (intermedia), s3 = ancora manuale (es. orario corretto a mano dall'utente).
        val prima = trattaAuto("prima", durata = 15, margine = 5)
        val ancoraTemplate = trattaAuto("ancoraTemplate", durata = 0, margine = 20)
        val intermedia = trattaAuto("intermedia", durata = 30, margine = 5)
        val ancoraManuale = trattaAuto("ancoraManuale", durata = 20, margine = 5)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = false, selezionataId = prima.id), prima, listOf(prima)),
            SlotRisolto(slot("s1", 1, ancora = true, selezionataId = ancoraTemplate.id), ancoraTemplate, listOf(ancoraTemplate)),
            SlotRisolto(slot("s2", 2, ancora = false, selezionataId = intermedia.id), intermedia, listOf(intermedia)),
            SlotRisolto(slot("s3", 3, ancora = false, selezionataId = ancoraManuale.id), ancoraManuale, listOf(ancoraManuale))
        )

        val ancore = ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)) +
            ancora(3, LocalTime.of(13, 0), LocalTime.of(13, 20)) // orario "corretto a mano", scollegato dal calcolo lineare

        val eventi = motore.calcola(risolti, ancore)

        // s0: indietro dalla prima ancora (s1), invariato rispetto al caso mono-ancora.
        assertEquals(LocalTime.of(9, 25), eventi[0].inizioReale)
        assertEquals(LocalTime.of(9, 40), eventi[0].fineReale)

        // s1: ancora del template, invariata.
        assertEquals(LocalTime.of(10, 0), eventi[1].inizioReale)
        assertEquals(LocalTime.of(10, 20), eventi[1].fineReale)

        // s2: in avanti da s1 (10:20 + margine 5 = 10:25 -> 10:25+30=10:55), NON a ritroso da s3.
        assertEquals(LocalTime.of(10, 25), eventi[2].inizioReale)
        assertEquals(LocalTime.of(10, 55), eventi[2].fineReale)

        // s3: ancora manuale, invariata (anche se "scollegata" dal risultato lineare di s2).
        assertEquals(LocalTime.of(13, 0), eventi[3].inizioReale)
        assertEquals(LocalTime.of(13, 20), eventi[3].fineReale)
    }

    @Test
    fun `con piu ancore, le tratte dopo l'ultima ancora propagano in avanti da essa`() {
        val ancoraTemplate = trattaAuto("ancoraTemplate", durata = 0, margine = 0)
        val ancoraManuale = trattaAuto("ancoraManuale", durata = 0, margine = 0)
        val dopo = trattaAuto("dopo", durata = 10, margine = 5)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = true, selezionataId = ancoraTemplate.id), ancoraTemplate, listOf(ancoraTemplate)),
            SlotRisolto(slot("s1", 1, ancora = false, selezionataId = ancoraManuale.id), ancoraManuale, listOf(ancoraManuale)),
            SlotRisolto(slot("s2", 2, ancora = false, selezionataId = dopo.id), dopo, listOf(dopo))
        )

        val ancore = ancora(0, LocalTime.of(8, 0), LocalTime.of(8, 30)) +
            ancora(1, LocalTime.of(9, 0), LocalTime.of(9, 10))

        val eventi = motore.calcola(risolti, ancore)

        // dopo: in avanti dalla SECONDA ancora (s1: 9:10 + margine 5 = 9:15 -> +10 = 9:25), non dalla prima.
        assertEquals(LocalTime.of(9, 15), eventi[2].inizioReale)
        assertEquals(LocalTime.of(9, 25), eventi[2].fineReale)
    }

    @Test
    fun `propagazione avanti sceglie l'orario fisso se e' piu conveniente del ricorrente`() {
        // Ricorrente: parte al minuto 43 di ogni ora. Fisso: un treno speciale alle 9:05-9:50.
        val opzione = OpzioneOrario(id = "o1", minutoPartenza = 43, offsetOreArrivo = 1, minutoArrivo = 29)
        val fisso = OrarioFisso(id = "f1", partenza = LocalTime.of(9, 5), arrivo = LocalTime.of(9, 50))
        val treno = trattaTreno("treno", margine = 0, opzioni = listOf(opzione), orariFissi = listOf(fisso))
        val ancora = trattaAuto("ancora", durata = 0, margine = 0)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora)),
            SlotRisolto(slot("s1", 1, ancora = false, selezionataId = treno.id), treno, listOf(treno))
        )

        // deadline partenza = 9:00; il ricorrente offre 9:43, il fisso 9:05 -> il fisso vince perché più vicino al deadline.
        val eventi = motore.calcola(risolti, ancora(0, LocalTime.of(8, 30), LocalTime.of(9, 0)))

        assertEquals(LocalTime.of(9, 5), eventi[1].inizioReale)
        assertEquals(LocalTime.of(9, 50), eventi[1].fineReale)
    }

    @Test
    fun `propagazione indietro sceglie il candidato con arrivo piu tardivo tra fisso e ricorrenti`() {
        val opzione = OpzioneOrario(id = "o1", minutoPartenza = 43, offsetOreArrivo = 1, minutoArrivo = 29)
        val fisso = OrarioFisso(id = "f1", partenza = LocalTime.of(9, 5), arrivo = LocalTime.of(9, 50))
        val treno = trattaTreno("treno", margine = 0, opzioni = listOf(opzione), orariFissi = listOf(fisso))
        val ancora = trattaAuto("ancora", durata = 0, margine = 0)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = false, selezionataId = treno.id), treno, listOf(treno)),
            SlotRisolto(slot("s1", 1, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora))
        )

        // deadline arrivo = 10:00; il fisso arriva alle 9:50, il ricorrente (8:43->9:29) arriva prima ma il
        // ricorrente successivo (9:43->10:29) supera il deadline: tra i validi, il fisso (9:50) è il più tardivo.
        val eventi = motore.calcola(risolti, ancora(1, LocalTime.of(10, 0), LocalTime.of(10, 20)))

        assertEquals(LocalTime.of(9, 5), eventi[0].inizioReale)
        assertEquals(LocalTime.of(9, 50), eventi[0].fineReale)
    }

    @Test
    fun `tratta TRENO con solo orari fissi, senza pattern ricorrenti, funziona regolarmente`() {
        val fisso = OrarioFisso(id = "f1", partenza = LocalTime.of(14, 32), arrivo = LocalTime.of(15, 10))
        val treno = trattaTreno("treno", margine = 5, opzioni = emptyList(), orariFissi = listOf(fisso))
        val ancora = trattaAuto("ancora", durata = 0, margine = 0)

        val risolti = listOf(
            SlotRisolto(slot("s0", 0, ancora = true, selezionataId = ancora.id), ancora, listOf(ancora)),
            SlotRisolto(slot("s1", 1, ancora = false, selezionataId = treno.id), treno, listOf(treno))
        )

        val eventi = motore.calcola(risolti, ancora(0, LocalTime.of(14, 0), LocalTime.of(14, 20)))

        assertEquals(LocalTime.of(14, 32), eventi[1].inizioReale)
        assertEquals(LocalTime.of(15, 10), eventi[1].fineReale)
    }
}
