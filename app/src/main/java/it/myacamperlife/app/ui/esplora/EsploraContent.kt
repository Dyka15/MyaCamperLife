package it.myacamperlife.app.ui.esplora

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.PoiVicino

/**
 * Esplora: cosa c'e' nei dintorni, **senza rete**.
 *
 * E' il primo dei due strati previsti. Questo legge una scorta locale e
 * risponde sempre; la domanda libera a un modello arriva alla fase 8 e si
 * appoggia sopra a questo, non al suo posto.
 *
 * Le categorie vuote non si mostrano: toccare "Campeggi" e trovare una lista
 * bianca fa sembrare rotta l'app, quando invece li' non ci sono campeggi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EsploraContent(
    perCategoria: Map<CategoriaPoi, Int>,
    risultati: (CategoriaPoi?) -> List<PoiVicino>,
    haScorta: Boolean,
    onScarica: () -> Unit,
    onApri: (PoiVicino) -> Unit,
) {
    var scelta by rememberSaveable { mutableStateOf<CategoriaPoi?>(null) }

    if (!haScorta) {
        Vuoto(onScarica)
        return
    }

    val elenco = risultati(scelta)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = scelta == null,
                    onClick = { scelta = null },
                    label = { Text(stringResource(R.string.esplora_tutto)) },
                )
                // Solo le categorie che hanno qualcosa, col numero: si vede
                // cosa vale la pena toccare prima di toccarlo.
                perCategoria.forEach { (categoria, quanti) ->
                    FilterChip(
                        selected = scelta == categoria,
                        onClick = { scelta = categoria },
                        label = { Text("${categoria.nome} ($quanti)") },
                    )
                }
            }
            HorizontalDivider()
        }

        items(elenco, key = { it.poi.id }) { vicino ->
            RigaPoi(vicino, onTocco = { onApri(vicino) })
        }

        if (elenco.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.esplora_niente_qui),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TextButton(
                onClick = onScarica,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.esplora_riscarica)) }
        }
    }
}

@Composable
private fun Vuoto(onScarica: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            stringResource(R.string.esplora_vuoto),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.esplora_vuoto_spiegazione),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onScarica, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.esplora_scarica))
        }
    }
}

@Composable
private fun RigaPoi(vicino: PoiVicino, onTocco: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(vicino.poi.etichetta(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(vicino.poi.categoria.senzaNome, vicino.poi.dettaglio)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = vicino.distanza,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
