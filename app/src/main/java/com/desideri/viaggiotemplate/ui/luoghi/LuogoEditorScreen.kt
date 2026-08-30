package com.desideri.viaggiotemplate.ui.luoghi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desideri.viaggiotemplate.domain.model.Luogo

@Composable
fun LuogoEditorScreen(
    luogoEsistente: Luogo?,
    nuovoId: () -> String,
    onSalva: (Luogo) -> Unit,
    onAnnulla: () -> Unit,
    padding: PaddingValues
) {
    var nome by remember { mutableStateOf(luogoEsistente?.nome ?: "") }
    var indirizzo by remember { mutableStateOf(luogoEsistente?.indirizzo ?: "") }

    LazyColumn(
        modifier = Modifier.padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = nome, onValueChange = { nome = it },
                label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = indirizzo, onValueChange = { indirizzo = it },
                label = { Text("Indirizzo (opzionale, per la navigazione da tratte AUTO)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = nome.isNotBlank(),
                    onClick = {
                        onSalva(
                            Luogo(
                                id = luogoEsistente?.id ?: nuovoId(),
                                nome = nome.trim(),
                                indirizzo = indirizzo.trim().ifBlank { null }
                            )
                        )
                    }
                ) { Text("Salva") }
                TextButton(onClick = onAnnulla) { Text("Annulla") }
            }
        }
    }
}
