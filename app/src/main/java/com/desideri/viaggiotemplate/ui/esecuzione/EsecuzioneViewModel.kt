package com.desideri.viaggiotemplate.ui.esecuzione

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.desideri.viaggiotemplate.data.local.ImpostazioniStore
import com.desideri.viaggiotemplate.data.local.entities.TemplateEntity
import com.desideri.viaggiotemplate.data.remote.CorsaScaricata
import com.desideri.viaggiotemplate.domain.calcolo.EventoCalcolato
import com.desideri.viaggiotemplate.domain.calcolo.MotoreCalcolo
import com.desideri.viaggiotemplate.domain.calcolo.SlotRisolto
import com.desideri.viaggiotemplate.domain.calcolo.toStringHHmm
import com.desideri.viaggiotemplate.domain.calendar.CalendarWriter
import com.desideri.viaggiotemplate.domain.calendar.CalendarioDisponibile
import com.desideri.viaggiotemplate.domain.calendar.EventoDaScrivere
import com.desideri.viaggiotemplate.domain.model.Notifica
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.TemplateSlot
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.Vettore
import com.desideri.viaggiotemplate.repository.EsecuzioneCreataRepository
import com.desideri.viaggiotemplate.repository.TemplateRepository
import com.desideri.viaggiotemplate.repository.TrattaRepository
import com.desideri.viaggiotemplate.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class StatoEsecuzione(
    val templateEntities: List<TemplateEntity> = emptyList(),
    val templateSelezionato: Template? = null,
    val tratte: Map<String, Tratta> = emptyMap(),
    val data: LocalDate = LocalDate.now(),
    /**
     * Orario inserito dall'utente per le tratte-ancora del template (una o più) che NON
     * hanno un orario di inizio predefinito (es. un treno il cui orario reale del giorno
     * va inserito a mano). Chiave: templateSlotId. Le ancore con orario predefinito (tipicamente
     * le Riunioni) non compaiono qui: il loro orario si ricava sempre dalla tratta stessa.
     */
    val orariAncoreInput: Map<String, Pair<LocalTime, LocalTime>> = emptyMap(),
    val eventiCalcolati: List<EventoCalcolato> = emptyList(),
    /**
     * Orari corretti a mano dall'utente su tratte diverse dalle ancore del template
     * (templateSlotId -> orari). Ogni voce diventa un'ancora aggiuntiva nel motore di
     * calcolo, così le tratte tra due ancore si ricalcolano in modo coerente.
     */
    val ancoreManuali: Map<String, Pair<LocalTime, LocalTime>> = emptyMap(),
    /** Slot esclusi da questo calcolo (tratte eliminate dall'utente): il motore le salta, ricalcolando le tratte adiacenti come se fossero direttamente consecutive. */
    val slotEsclusi: Set<String> = emptySet(),
    /**
     * Promemoria/descrizione/colore scelti per questa sola esecuzione (templateSlotId -> valore).
     * Popolati con i default (notifica ereditata da slot/tratta, descrizione vuota, colore della
     * tratta) subito dopo ogni calcolo, poi modificabili singolarmente prima di scrivere a calendario.
     */
    val notificheSelezionate: Map<String, Notifica> = emptyMap(),
    val descrizioni: Map<String, String> = emptyMap(),
    val coloriSelezionati: Map<String, Int?> = emptyMap(),
    /**
     * Quando una Tratta TRENO ha vettore ALTRO, l'utente sceglie qui — solo per questa esecuzione,
     * per singolo templateSlotId — quale vettore reale usare (Trenitalia/Italo/SBB): se risolve a
     * un'integrazione con orari reali (Trenitalia/SBB) sblocca la ricerca; se Italo, resta il
     * meccanismo ricorrente/fisso di sempre (nessuna integrazione disponibile).
     */
    val vettoriScelti: Map<String, Vettore> = emptyMap(),
    /** Orario indicativo per la ricerca in tempo reale quando [vettoriScelti] risolve a Trenitalia/SBB per quello slot. */
    val orariIndicativi: Map<String, LocalTime> = emptyMap(),
    /** Tutta la libreria Tratte, per poter aggiungere una tratta extra a questa sola esecuzione. */
    val libreriaTratte: List<Tratta> = emptyList(),
    val calendarioConfigurato: CalendarioDisponibile? = null,
    val calendarioConfiguratoVerificato: Boolean = false,
    val messaggio: String? = null
)

class EsecuzioneViewModel(
    private val templateRepository: TemplateRepository,
    private val trattaRepository: TrattaRepository,
    private val impostazioniStore: ImpostazioniStore,
    private val esecuzioneCreataRepository: EsecuzioneCreataRepository
) : ViewModel() {

    private val motore = MotoreCalcolo()

    private val _stato = MutableStateFlow(StatoEsecuzione())
    val stato: StateFlow<StatoEsecuzione> = _stato.asStateFlow()

    init {
        viewModelScope.launch {
            templateRepository.osservaTemplateEntities().collect { lista ->
                _stato.value = _stato.value.copy(templateEntities = lista)
            }
        }
        viewModelScope.launch {
            trattaRepository.osservaTratte().collect { lista ->
                _stato.value = _stato.value.copy(libreriaTratte = lista)
            }
        }
    }

    fun selezionaTemplate(id: String) {
        viewModelScope.launch {
            val template = templateRepository.getTemplate(id) ?: return@launch
            val tratteMap = template.slots
                .flatMap { it.trattaCandidatiIds.ifEmpty { listOf(it.trattaSelezionataId) } }
                .distinct()
                .mapNotNull { trattaId -> trattaRepository.getTratta(trattaId)?.let { trattaId to it } }
                .toMap()

            // Per le ancore SENZA orario predefinito (es. un treno) serve un input utente:
            // lo si inizializza a 9:00-9:30. Le ancore CON orario predefinito (tipicamente
            // le Riunioni) non hanno bisogno di input: il loro orario si ricava sempre dalla tratta.
            val orariAncoreInput = template.slots
                .filter { it.ancora && tratteMap[it.trattaSelezionataId]?.orarioInizioDefault == null }
                .associate { it.id to (LocalTime.of(9, 0) to LocalTime.of(9, 30)) }

            _stato.value = _stato.value.copy(
                templateSelezionato = template,
                tratte = tratteMap,
                eventiCalcolati = emptyList(),
                ancoreManuali = emptyMap(),
                slotEsclusi = emptySet(),
                notificheSelezionate = emptyMap(),
                descrizioni = emptyMap(),
                coloriSelezionati = emptyMap(),
                vettoriScelti = emptyMap(),
                orariIndicativi = emptyMap(),
                orariAncoreInput = orariAncoreInput
            )
        }
    }

    fun aggiornaData(data: LocalDate) {
        _stato.value = _stato.value.copy(data = data)
    }

    /**
     * Rilegge quale calendario è configurato nelle Impostazioni (solo per mostrarlo in questa
     * schermata prima di scrivere). Da chiamare dopo aver ottenuto i permessi.
     */
    fun aggiornaCalendarioConfigurato(context: Context) {
        val id = impostazioniStore.calendarioSelezionatoId
        val calendario = id?.let { targetId ->
            CalendarWriter(context).elencaCalendariScrivibili().firstOrNull { it.id == targetId }
        }
        _stato.value = _stato.value.copy(calendarioConfigurato = calendario, calendarioConfiguratoVerificato = true)
    }

    /** Aggiorna l'orario inserito dall'utente per una specifica tratta-ancora senza orario predefinito. */
    fun aggiornaOrarioAncora(templateSlotId: String, inizio: LocalTime, fine: LocalTime) {
        _stato.value = _stato.value.copy(
            orariAncoreInput = _stato.value.orariAncoreInput + (templateSlotId to (inizio to fine))
        )
    }

    /** Sceglie, per questa sola esecuzione, il vettore reale di una Tratta TRENO con vettore ALTRO. */
    fun sceglieVettorePerTrattaAltro(templateSlotId: String, vettore: Vettore) {
        _stato.value = _stato.value.copy(
            vettoriScelti = _stato.value.vettoriScelti + (templateSlotId to vettore)
        )
    }

    /** Aggiorna l'orario indicativo usato per la ricerca in tempo reale su una Tratta ALTRO risolta a Trenitalia/SBB. */
    fun aggiornaOrarioIndicativo(templateSlotId: String, orario: LocalTime) {
        _stato.value = _stato.value.copy(
            orariIndicativi = _stato.value.orariIndicativi + (templateSlotId to orario)
        )
    }

    /** Calcolo da zero: riparte dalle sole ancore del template, scartando correzioni manuali, esclusioni e tratte extra aggiunte in precedenza. */
    fun calcola() {
        val template = _stato.value.templateSelezionato
        _stato.value = _stato.value.copy(
            templateSelezionato = template?.let { it.copy(slots = it.slots.filter { s -> !s.extra }) },
            ancoreManuali = emptyMap(),
            slotEsclusi = emptySet()
        )
        eseguiCalcolo()
    }

    /** Pulisce i risultati del calcolo, le correzioni manuali, le esclusioni e l'esito dell'ultimo inserimento a calendario. */
    fun pulisciRisultati() {
        _stato.value = _stato.value.copy(
            eventiCalcolati = emptyList(),
            ancoreManuali = emptyMap(),
            slotEsclusi = emptySet(),
            notificheSelezionate = emptyMap(),
            descrizioni = emptyMap(),
            coloriSelezionati = emptyMap(),
            messaggio = null
        )
    }

    /** Aggiorna il promemoria scelto per un evento, solo per questa esecuzione. */
    fun aggiornaNotifica(templateSlotId: String, notifica: Notifica) {
        _stato.value = _stato.value.copy(
            notificheSelezionate = _stato.value.notificheSelezionate + (templateSlotId to notifica)
        )
    }

    /** Aggiorna la descrizione testuale libera di un evento, solo per questa esecuzione. */
    fun aggiornaDescrizione(templateSlotId: String, testo: String) {
        _stato.value = _stato.value.copy(
            descrizioni = _stato.value.descrizioni + (templateSlotId to testo)
        )
    }

    /** Aggiorna il colore dell'evento calendario, solo per questa esecuzione. */
    fun aggiornaColoreEvento(templateSlotId: String, colore: Int?) {
        _stato.value = _stato.value.copy(
            coloriSelezionati = _stato.value.coloriSelezionati + (templateSlotId to colore)
        )
    }

    /**
     * Aggiunge temporaneamente una tratta della libreria a questa sola esecuzione (non tocca il
     * template salvato): inserita subito dopo `dopoSlotId` (o in testa se null), rinumerando l'ordine.
     */
    fun aggiungiTrattaExtra(trattaId: String, dopoSlotId: String?) {
        val s = _stato.value
        val template = s.templateSelezionato ?: return
        val tratta = s.libreriaTratte.firstOrNull { it.id == trattaId } ?: return

        val ordinati = template.slotsOrdinati
        val indiceInserimento = if (dopoSlotId == null) 0 else {
            val i = ordinati.indexOfFirst { it.id == dopoSlotId }
            if (i < 0) ordinati.size else i + 1
        }
        val nuovoSlot = TemplateSlot(
            id = UUID.randomUUID().toString(),
            ordine = indiceInserimento,
            ancora = false,
            trattaCandidatiIds = listOf(trattaId),
            trattaSelezionataId = trattaId,
            extra = true
        )
        val nuoviSlots = (ordinati.toMutableList().apply { add(indiceInserimento, nuovoSlot) })
            .mapIndexed { i, slot -> slot.copy(ordine = i) }

        _stato.value = s.copy(
            templateSelezionato = template.copy(slots = nuoviSlots),
            tratte = s.tratte + (trattaId to tratta)
        )
        eseguiCalcolo()
    }

    /**
     * Sostituisce la tratta selezionata per uno slot (scelta di un'alternativa) e ricalcola.
     * Se quello slot era diventato un'ancora manuale (orario corretto a mano, o scaricato da
     * SBB/Trenitalia), l'ancora manuale era per la tratta PRECEDENTE: va aggiornata con l'orario
     * della nuova tratta scelta (quello gia' mostrato nell'anteprima "Alternative"), altrimenti
     * l'orario visualizzato resterebbe quello vecchio, fisso, ignorando la nuova selezione.
     */
    fun scegliAlternativa(templateSlotId: String, nuovaTrattaId: String, nuovoInizio: LocalTime, nuovaFine: LocalTime) {
        val s = _stato.value
        val template = s.templateSelezionato ?: return
        val nuoviSlot = template.slots.map {
            if (it.id == templateSlotId) it.copy(trattaSelezionataId = nuovaTrattaId) else it
        }
        val ancoreManuali = if (templateSlotId in s.ancoreManuali) {
            s.ancoreManuali + (templateSlotId to (nuovoInizio to nuovaFine))
        } else {
            s.ancoreManuali
        }
        _stato.value = s.copy(templateSelezionato = template.copy(slots = nuoviSlot), ancoreManuali = ancoreManuali)
        eseguiCalcolo()
    }

    /**
     * Corregge a mano inizio/fine reali di una tratta calcolata. La tratta diventa un'ancora
     * aggiuntiva: si ricalcolano tutte le altre tenendo conto sia delle ancore del template
     * sia di questo nuovo punto fisso.
     */
    fun sovrascriviEvento(templateSlotId: String, nuovoInizio: LocalTime, nuovaFine: LocalTime) {
        val s = _stato.value
        _stato.value = s.copy(ancoreManuali = s.ancoreManuali + (templateSlotId to (nuovoInizio to nuovaFine)))
        eseguiCalcolo()
    }

    /**
     * Calcola, tra le corse scaricate (es. da SBB/Trenitalia), quale verrebbe scelta
     * automaticamente dal motore se [trattaId] fosse la tratta selezionata per [templateSlotId] e
     * quelle corse diventassero suoi orari fissi — stessa logica di margine rispetto alle tratte
     * adiacenti già usata per fissi/ricorrenti. [trattaId] può essere sia la tratta già
     * selezionata per quello slot (uso nel pannello di conferma di "Aggiorna con orario reale")
     * sia una tratta alternativa non ancora scelta (uso nel confronto alternative): la
     * simulazione seleziona sempre [trattaId] per quello slot, quindi nel primo caso è un
     * no-op. Non modifica lo stato: serve solo a evidenziare la scelta consigliata prima che
     * l'utente scelga.
     */
    fun calcolaSceltaConsigliata(templateSlotId: String, trattaId: String, corse: List<CorsaScaricata>): CorsaScaricata? {
        val s = _stato.value
        val template = s.templateSelezionato ?: return null
        val trattaEsistente = s.tratte[trattaId] ?: return null
        val trattaArricchita = trattaEsistente.copy(orariFissi = trattaEsistente.orariFissi + corse.map { it.toOrarioFisso() })
        val tratteSimulate = s.tratte + (trattaId to trattaArricchita)
        val templateSimulato = template.copy(
            slots = template.slots.map { if (it.id == templateSlotId) it.copy(trattaSelezionataId = trattaId) else it }
        )
        val evento = calcolaEventiConTratte(tratteSimulate, templateSimulato)?.firstOrNull { it.templateSlotId == templateSlotId } ?: return null
        return corse.firstOrNull { it.partenza == evento.inizioReale && it.arrivo == evento.fineReale }
    }

    /**
     * Applica la corsa scelta dall'utente (di default quella suggerita da
     * [calcolaSceltaConsigliata], ma può essere una qualunque tra quelle scaricate) come orario
     * reale di [trattaId]: la imposta come tratta selezionata di [templateSlotId] (se non lo era
     * già — es. una tratta scelta nel confronto alternative) e come ancora manuale con l'orario
     * esatto scelto (vedi [sovrascriviEvento]), così il ricalcolo non lo sovrascrive con un
     * placeholder. Tutte le corse scaricate restano comunque disponibili come orari fissi della
     * tratta per eventuali ricalcoli successivi (es. dopo l'eliminazione di questa tratta come ancora).
     */
    fun confermaOrarioReale(templateSlotId: String, trattaId: String, corse: List<CorsaScaricata>, corsaScelta: CorsaScaricata) {
        val s = _stato.value
        val template = s.templateSelezionato ?: return
        val trattaEsistente = s.tratte[trattaId] ?: return
        val trattaArricchita = trattaEsistente.copy(orariFissi = trattaEsistente.orariFissi + corse.map { it.toOrarioFisso() })
        val nuoviSlot = template.slots.map { if (it.id == templateSlotId) it.copy(trattaSelezionataId = trattaId) else it }
        _stato.value = s.copy(
            templateSelezionato = template.copy(slots = nuoviSlot),
            tratte = s.tratte + (trattaId to trattaArricchita)
        )
        sovrascriviEvento(templateSlotId, corsaScelta.partenza, corsaScelta.arrivo)
        _stato.value = _stato.value.copy(
            messaggio = "Orario aggiornato con la corsa reale delle ${corsaScelta.partenza.toStringHHmm()}"
        )
    }

    private fun CorsaScaricata.toOrarioFisso() =
        OrarioFisso(id = UUID.randomUUID().toString(), partenza = partenza, arrivo = arrivo, etichetta = etichetta.ifBlank { null })

    /**
     * Esclude una tratta da questo calcolo e lo rilancia subito: le tratte adiacenti a quella
     * eliminata si ricalcolano tenendo conto del margine reciproco, come se non facesse parte
     * del viaggio (non tocca il template salvato, solo l'esito di questa esecuzione).
     */
    fun eliminaEvento(templateSlotId: String) {
        val s = _stato.value
        _stato.value = s.copy(
            slotEsclusi = s.slotEsclusi + templateSlotId,
            ancoreManuali = s.ancoreManuali - templateSlotId
        )
        eseguiCalcolo()
    }

    /**
     * Calcola gli eventi usando una mappa di tratte alternativa a quella in [_stato] (es. una
     * tratta arricchita con orari scaricati, per una simulazione) e, opzionalmente, un template
     * alternativo (es. con una diversa tratta selezionata per uno slot, per simulare una scelta
     * non ancora applicata) senza modificare lo stato. Restituisce null se il calcolo non è
     * possibile (nessun template, nessuna ancora, tratte mancanti, errore del motore) — usato per
     * anteprime, es. [calcolaSceltaConsigliata].
     */
    private fun calcolaEventiConTratte(tratte: Map<String, Tratta>, templateSimulato: Template? = null): List<EventoCalcolato>? {
        val s = _stato.value
        val template = templateSimulato ?: s.templateSelezionato ?: return null
        val slotsOrdinati = template.slotsOrdinati.filterNot { it.id in s.slotEsclusi }
        if (slotsOrdinati.isEmpty()) return null
        val slotsAncora = slotsOrdinati.withIndex().filter { it.value.ancora }
        if (slotsAncora.isEmpty()) return null

        val slotsRisolti = slotsOrdinati.mapNotNull { slot ->
            val selezionata = tratte[slot.trattaSelezionataId] ?: return@mapNotNull null
            val candidate = slot.trattaCandidatiIds.mapNotNull { tratte[it] }
            SlotRisolto(slot, selezionata, candidate)
        }
        if (slotsRisolti.size != slotsOrdinati.size) return null

        val ancore = mutableMapOf<Int, Pair<LocalTime, LocalTime>>()
        for ((indice, slot) in slotsAncora) {
            val tratta = tratte[slot.trattaSelezionataId] ?: continue
            val orari = tratta.orarioInizioDefault?.let { it to it.plusMinutes(tratta.durataMinutiReale.toLong()) }
                ?: s.orariAncoreInput[slot.id]
                ?: (LocalTime.of(9, 0) to LocalTime.of(9, 30))
            ancore[indice] = orari
        }
        s.ancoreManuali.forEach { (slotId, orari) ->
            val indice = slotsOrdinati.indexOfFirst { it.id == slotId }
            if (indice >= 0) ancore[indice] = orari
        }

        return try {
            motore.calcola(slotsRisolti, ancore)
        } catch (e: Exception) {
            null
        }
    }

    private fun eseguiCalcolo() {
        val s = _stato.value
        val template = s.templateSelezionato ?: return
        val slotsOrdinati = template.slotsOrdinati.filterNot { it.id in s.slotEsclusi }
        if (slotsOrdinati.isEmpty()) {
            _stato.value = s.copy(eventiCalcolati = emptyList(), messaggio = "Tutte le tratte sono state escluse dal calcolo")
            return
        }
        val slotsAncora = slotsOrdinati.withIndex().filter { it.value.ancora }
        if (slotsAncora.isEmpty()) {
            _stato.value = s.copy(eventiCalcolati = emptyList(), messaggio = "Il template non ha nessuna tratta ancora impostata tra quelle incluse nel calcolo")
            return
        }

        val slotsRisolti = slotsOrdinati.mapNotNull { slot ->
            val selezionata = s.tratte[slot.trattaSelezionataId] ?: return@mapNotNull null
            val candidate = slot.trattaCandidatiIds.mapNotNull { s.tratte[it] }
            SlotRisolto(slot, selezionata, candidate)
        }
        if (slotsRisolti.size != slotsOrdinati.size) {
            _stato.value = s.copy(messaggio = "Alcune tratte del template non sono state trovate")
            return
        }

        // Ogni tratta-ancora del template: se ha un orario predefinito (tipicamente una Riunione)
        // è sempre fissa; altrimenti si usa l'orario inserito dall'utente per quello slot.
        val ancore = mutableMapOf<Int, Pair<LocalTime, LocalTime>>()
        for ((indice, slot) in slotsAncora) {
            val tratta = s.tratte[slot.trattaSelezionataId] ?: continue
            val orari = tratta.orarioInizioDefault?.let { it to it.plusMinutes(tratta.durataMinutiReale.toLong()) }
                ?: s.orariAncoreInput[slot.id]
                ?: (LocalTime.of(9, 0) to LocalTime.of(9, 30))
            ancore[indice] = orari
        }
        s.ancoreManuali.forEach { (slotId, orari) ->
            val indice = slotsOrdinati.indexOfFirst { it.id == slotId }
            if (indice >= 0) ancore[indice] = orari
        }

        try {
            val eventi = motore.calcola(slotsRisolti, ancore)
            val slotById = slotsOrdinati.associateBy { it.id }

            val notifiche = _stato.value.notificheSelezionate.toMutableMap()
            val descrizioni = _stato.value.descrizioni.toMutableMap()
            val colori = _stato.value.coloriSelezionati.toMutableMap()
            for (ev in eventi) {
                val id = ev.templateSlotId
                if (id !in notifiche) {
                    notifiche[id] = slotById[id]?.notificaOverride ?: ev.tratta.notifica
                }
                if (id !in descrizioni) descrizioni[id] = ""
                if (id !in colori) colori[id] = ev.tratta.colore
            }

            _stato.value = _stato.value.copy(
                eventiCalcolati = eventi,
                notificheSelezionate = notifiche,
                descrizioni = descrizioni,
                coloriSelezionati = colori,
                messaggio = null
            )
        } catch (e: Exception) {
            _stato.value = _stato.value.copy(messaggio = e.message ?: "Errore nel calcolo")
        }
    }

    fun aggiungiAlCalendario(context: Context) {
        val s = _stato.value
        if (s.eventiCalcolati.isEmpty()) return
        val calendario = s.calendarioConfigurato
        if (calendario == null) {
            _stato.value = s.copy(messaggio = "Configura un calendario di destinazione nella sezione Impostazioni")
            return
        }
        try {
            val writer = CalendarWriter(context)
            val eventiDaScrivere = s.eventiCalcolati.map { ev ->
                EventoDaScrivere(
                    evento = ev,
                    notifica = s.notificheSelezionate[ev.templateSlotId] ?: Notifica.NESSUNA,
                    descrizione = s.descrizioni[ev.templateSlotId] ?: "",
                    colore = s.coloriSelezionati[ev.templateSlotId] ?: ev.tratta.colore
                )
            }
            val idInseriti = writer.inserisciEventi(calendario, s.data, eventiDaScrivere)
            if (idInseriti.isNotEmpty()) {
                val esecuzioneId = UUID.randomUUID().toString()
                // Orario di inizio del più mattiniero degli eventi (stesso arrotondamento scritto su
                // DTSTART): più intuitivo da ritrovare in "Eventi creati" rispetto al momento in cui
                // si è premuto il pulsante, che può non coincidere col giorno del viaggio.
                val inizioPrimoEvento = s.data.atTime(s.eventiCalcolati.minOf { it.inizioBlocco })
                    .atZone(ZoneId.systemDefault()).toInstant()
                viewModelScope.launch { esecuzioneCreataRepository.registra(esecuzioneId, idInseriti, inizioPrimoEvento) }
            }
            _stato.value = if (idInseriti.size == s.eventiCalcolati.size) {
                s.copy(messaggio = "${idInseriti.size} eventi aggiunti al calendario ✓")
            } else {
                s.copy(messaggio = "Aggiunti solo ${idInseriti.size} su ${s.eventiCalcolati.size} eventi: controlla il calendario scelto")
            }
        } catch (e: Exception) {
            _stato.value = s.copy(messaggio = "Errore nella scrittura sul calendario: ${e.message}")
        }
    }
}

object EsecuzioneViewModelFactory {
    fun get(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EsecuzioneViewModel(
                AppContainer.templateRepository,
                AppContainer.trattaRepository,
                AppContainer.impostazioniStore,
                AppContainer.esecuzioneCreataRepository
            ) as T
    }
}
