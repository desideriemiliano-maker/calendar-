package com.desideri.viaggiotemplate.domain.model

/**
 * Una posizione ordinata nel template. Di norma ha un solo candidato (`trattaCandidatiIds`
 * con un solo elemento), ma può averne più d'uno quando ci sono destinazioni alternative
 * (es. Roma Termini -> Pomezia / Albano Laziale / Pavona): in quel caso `trattaSelezionataId`
 * indica quale candidato è attualmente scelto, modificabile in fase di esecuzione.
 */
data class TemplateSlot(
    val id: String,
    val ordine: Int,
    val ancora: Boolean,
    val trattaCandidatiIds: List<String>,
    val trattaSelezionataId: String
)

data class Template(
    val id: String,
    val nome: String,
    val slots: List<TemplateSlot>,
    val ordine: Int = 0,
    /** Colore ARGB della card in libreria; null = colore grigio di default del tema. */
    val colore: Int? = null
) {
    val slotAncora: TemplateSlot?
        get() = slots.firstOrNull { it.ancora }

    val slotsOrdinati: List<TemplateSlot>
        get() = slots.sortedBy { it.ordine }
}
