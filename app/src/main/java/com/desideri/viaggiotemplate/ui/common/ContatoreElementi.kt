package com.desideri.viaggiotemplate.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Numero di elementi mostrati in una lista filtrabile (Eventi, Luoghi, Tratte, Template): stessa
 * posizione (sopra la lista, sotto i controlli di filtro/ricerca) e stile in tutte le schermate
 * che lo usano, tranne Esegui che non ha una lista filtrabile di questo tipo.
 *
 * Se [totale] (senza alcun filtro) differisce da [mostrati] (con l'eventuale filtro applicato)
 * mostra anche il totale ("N di M elementi"), altrimenti solo "N elementi": la regola è
 * semplicemente confrontare le due dimensioni, senza dover sapere schermata per schermata quale
 * combinazione di stato conta come "un filtro attivo" — se il conteggio filtrato è già uguale al
 * totale, mostrare "di M" sarebbe rumore ridondante.
 *
 * [modifier] sostituisce (non somma a) il padding di default: le schermate con lista/ricerca a
 * padding orizzontale 12dp proprio (Tratte, Luoghi, Template) possono usare il default, mentre una
 * schermata con un padding esterno già uniforme (Eventi, 16dp su tutta la colonna) passa un
 * modifier senza padding orizzontale aggiuntivo per restare allineata alla riga di ricerca sopra.
 */
@Composable
fun ContatoreElementi(
    mostrati: Int,
    totale: Int,
    modifier: Modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
) {
    val parola = if (totale == 1) "elemento" else "elementi"
    val testo = if (mostrati == totale) "$mostrati $parola" else "$mostrati di $totale $parola"
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
