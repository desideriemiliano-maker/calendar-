package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.model.OpzioneOrario
import com.desideri.viaggiotemplate.domain.model.OrarioFisso
import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.Tratta
import com.desideri.viaggiotemplate.domain.model.TipoTratta

/**
 * Un nodo (luogo) del percorso di un template sulla mappa.
 *
 * [attesaMinuti] è il `margineMinuti` (vedi [Tratta.margineMinuti]) della tratta che riparte da
 * qui: lo stesso identico valore che [com.desideri.viaggiotemplate.domain.calcolo.MotoreCalcolo]
 * usa come buffer minimo richiesto prima che quella tratta possa iniziare, calcolato rispetto alla
 * fine della tratta precedente — non un tempo di attesa "reale" ricalcolato da un'esecuzione (che
 * richiederebbe un'ancora con orario concreto, non disponibile per un template di libreria).
 *
 * È `null` quando questo nodo non è un vero snodo tra un arrivo e una partenza:
 * - è la prima tappa del template (nessuna tratta arriva qui);
 * - è l'ultima tappa (nessuna tratta riparte da qui);
 * - è un nodo creato da un "salto" geografico (arrivo e partenza consecutivi non coincidono):
 *   il margine esiste sempre sulla tratta in partenza, ma qui non descriverebbe un'attesa IN
 *   questo punto, bensì il buffer verso un luogo diverso da quello raggiunto in precedenza.
 */
data class NodoPercorso(
    val luogoId: String,
    val attesaMinuti: Int?
)

/**
 * Un arco tra due nodi consecutivi del percorso.
 *
 * [trattaId] è `null` quando l'arco rappresenta un "salto" tra due luoghi non contigui, senza una
 * tratta diretta a spiegarlo (nessun controllo nel resto dell'app impedisce a un template di avere
 * un salto: vedi [MotoreCalcolo], che propaga solo i tempi, non verifica i luoghi).
 *
 * [durataMinuti]: per AUTO/RIUNIONE/A_PIEDI è `durataMinutiReale`, un valore certo
 * ([durataIndicativa] resta `false`). Per TRENO/AEREO, [MotoreCalcolo] sceglie l'orario di uno
 * specifico giorno risolvendo un'ancora — ma la DURATA di ogni pattern configurato (differenza tra
 * arrivo e partenza di una [OpzioneOrario] o di un [OrarioFisso]) non dipende da quale ora del
 * giorno viene scelta, quindi è comunque calcolabile senza ancora: se tutti i pattern configurati
 * durano uguale, quel valore è certo; se durano diverso, [durataMinuti] è la loro media e
 * [durataIndicativa] è `true` (va mostrata come "circa", non come un dato esatto). Se la tratta non
 * ha alcun pattern configurato (né opzioni ricorrenti né orari fissi — lo stesso caso in cui
 * MotoreCalcolo usa un placeholder "da confermare"), non c'è proprio nulla su cui basarsi:
 * [durataMinuti] resta `null`.
 *
 * [tipoTratta] è il tipo della tratta di questo arco (`null` per un salto, che non ha una tratta):
 * serve a spiegare sull'arco stesso perché lì non compare una durata, quando [durataMinuti] è
 * `null` per mancanza di dati (non per un salto).
 */
data class ArcoPercorso(
    val trattaId: String?,
    val durataMinuti: Int?,
    val durataIndicativa: Boolean = false,
    val tipoTratta: TipoTratta? = null
)

/** Durata di un singolo pattern ricorrente, indipendente da quale ora del giorno verrà scelta. */
private fun durataMinuti(opzione: OpzioneOrario): Int =
    opzione.offsetOreArrivo * 60 + opzione.minutoArrivo - opzione.minutoPartenza

/** Durata di un orario fisso; se l'arrivo è prima della partenza si considera oltre mezzanotte. */
private fun durataMinuti(fisso: OrarioFisso): Int {
    val partenzaMin = fisso.partenza.hour * 60 + fisso.partenza.minute
    val arrivoMin = fisso.arrivo.hour * 60 + fisso.arrivo.minute
    return (if (arrivoMin < partenzaMin) arrivoMin + 1440 else arrivoMin) - partenzaMin
}

/**
 * Risolve durata/indicatività di una tratta per l'arco sulla mappa. Vedi il commento su
 * [ArcoPercorso.durataMinuti] per il ragionamento completo.
 */
private fun risolviDurataArco(tratta: Tratta): Pair<Int?, Boolean> {
    if (!tratta.tipo.usaOrariProgrammati) return tratta.durataMinutiReale to false
    val durate = tratta.opzioniOrario.map(::durataMinuti) + tratta.orariFissi.map(::durataMinuti)
    return when {
        durate.isEmpty() -> null to false
        durate.distinct().size == 1 -> durate.first() to false
        else -> (durate.sum() / durate.size) to true
    }
}

data class PercorsoTemplate(
    val nodi: List<NodoPercorso>,
    val archi: List<ArcoPercorso>
)

/**
 * Risolve il percorso geografico di un template: la sequenza di luoghi attraversati (partenza e
 * arrivo di ogni tratta selezionata, nell'ordine degli slot) con gli archi che li collegano.
 *
 * Le ripetizioni consecutive sono collassate in un solo nodo (il caso normale, in cui l'arrivo di
 * una tratta coincide con la partenza della successiva): vale anche per una tratta "puntiforme"
 * come una RIUNIONE, il cui luogo di arrivo coincide per costruzione con quello di partenza (vedi
 * `TrattaEditorScreen`) — non produce né un nodo proprio né un arco, resta un punto sul nodo
 * condiviso. Il margine della RIUNIONE stessa (buffer prima che possa iniziare) non è quindi
 * un'attesa mostrabile su un nodo distinto: è un limite noto di questa rappresentazione, non un bug.
 *
 * Uno slot la cui tratta selezionata non è (più) tra [tratte] viene saltato silenziosamente:
 * capita solo per un template salvato con un riferimento a una tratta poi eliminata dalla libreria.
 */
fun risolviPercorso(template: Template, tratte: List<Tratta>): PercorsoTemplate {
    val tratteById = tratte.associateBy { it.id }
    val sequenza = template.slotsOrdinati.mapNotNull { tratteById[it.trattaSelezionataId] }
    if (sequenza.isEmpty()) return PercorsoTemplate(emptyList(), emptyList())

    val nodi = mutableListOf(sequenza.first().luogoPartenzaId)
    val archi = mutableListOf<ArcoPercorso>()

    sequenza.forEach { tratta ->
        if (nodi.last() != tratta.luogoPartenzaId) {
            // Salto: la partenza di questa tratta non coincide con l'ultimo arrivo registrato.
            archi += ArcoPercorso(trattaId = null, durataMinuti = null)
            nodi += tratta.luogoPartenzaId
        }
        if (tratta.luogoArrivoId != tratta.luogoPartenzaId) {
            val (durata, indicativa) = risolviDurataArco(tratta)
            archi += ArcoPercorso(trattaId = tratta.id, durataMinuti = durata, durataIndicativa = indicativa, tipoTratta = tratta.tipo)
            nodi += tratta.luogoArrivoId
        }
        // Se arrivo == partenza (tratta puntiforme, es. RIUNIONE) non si aggiunge né nodo né arco:
        // il margine di questa tratta resta "assorbito" nel nodo condiviso, non mostrabile a parte.
    }

    val nodiConAttesa = nodi.mapIndexed { indice, luogoId ->
        val entrante = archi.getOrNull(indice - 1)
        val uscente = archi.getOrNull(indice)
        // Serve un vero arrivo (entrante) e una vera partenza (uscente) DA QUESTO nodo: se uno dei
        // due manca (prima/ultima tappa) o è un salto (trattaId null, nessuna tratta reale), non
        // c'è un'attesa "in questo punto" da mostrare.
        val attesa = if (entrante?.trattaId != null && uscente?.trattaId != null) {
            tratteById[uscente.trattaId]?.margineMinuti
        } else null
        NodoPercorso(luogoId, attesa)
    }

    return PercorsoTemplate(nodiConAttesa, archi)
}
