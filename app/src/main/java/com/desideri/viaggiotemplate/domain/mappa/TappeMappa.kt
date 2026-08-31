package com.desideri.viaggiotemplate.domain.mappa

import com.desideri.viaggiotemplate.domain.model.Template
import com.desideri.viaggiotemplate.domain.model.Tratta

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
 * [durataMinuti] è la durata reale della tratta, solo per i tipi che non usano orari programmati
 * (AUTO/RIUNIONE/A_PIEDI, vedi [com.desideri.viaggiotemplate.domain.model.TipoTratta.usaOrariProgrammati]):
 * per TRENO/AEREO la durata dipende da quale opzione oraria/orario fisso verrebbe scelto in fase di
 * calcolo, cosa che richiede un'ancora con orario concreto — non c'è un singolo valore corretto da
 * mostrare qui, quindi resta `null` invece di sceglierne uno arbitrario.
 */
data class ArcoPercorso(
    val trattaId: String?,
    val durataMinuti: Int?
)

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

    fun durataDi(tratta: Tratta): Int? = if (tratta.tipo.usaOrariProgrammati) null else tratta.durataMinutiReale

    sequenza.forEach { tratta ->
        if (nodi.last() != tratta.luogoPartenzaId) {
            // Salto: la partenza di questa tratta non coincide con l'ultimo arrivo registrato.
            archi += ArcoPercorso(trattaId = null, durataMinuti = null)
            nodi += tratta.luogoPartenzaId
        }
        if (tratta.luogoArrivoId != tratta.luogoPartenzaId) {
            archi += ArcoPercorso(trattaId = tratta.id, durataMinuti = durataDi(tratta))
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
