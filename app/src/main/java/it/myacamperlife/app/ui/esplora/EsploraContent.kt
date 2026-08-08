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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.Dossier
import it.myacamperlife.app.dominio.PoiVicino

/**
 * Esplora: cosa c'e' nei dintorni, **senza rete**.
 *
 * Due strati, e l'ordine conta. In cima la domanda libera a un modello, che
 * serve quando l'elenco non basta e ha bisogno di rete; sotto, l'elenco locale,
 * che legge una scorta sul disco e risponde sempre. Il secondo strato sta
 * **sopra** al primo, non al suo posto.
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
    dossier: List<Dossier>,
    aiConfigurata: Boolean,
    inCorso: Boolean,
    onScarica: () -> Unit,
    onApri: (PoiVicino) -> Unit,
    onChiedi: (String) -> Unit,
    onDossier: (Dossier) -> Unit,
    onImpostaAi: () -> Unit,
) {
    var scelta by rememberSaveable { mutableStateOf<CategoriaPoi?>(null) }
    var domanda by rememberSaveable { mutableStateOf("") }

    val elenco = risultati(scelta)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // Il secondo strato sta in cima, il primo sotto: la domanda libera e'
        // quello che si vuole quando l'elenco non basta, e l'elenco resta a
        // portata di pollice per quando la rete non c'e'.
        item {
            Domanda(
                domanda = domanda,
                aiConfigurata = aiConfigurata,
                inCorso = inCorso,
                onDomanda = { domanda = it },
                onChiedi = { onChiedi(domanda); domanda = "" },
                onImpostaAi = onImpostaAi,
            )
            HorizontalDivider()
        }

        if (dossier.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.esplora_risposte),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
            }
            items(dossier, key = { it.id }) { salvato ->
                RigaDossier(salvato, onTocco = { onDossier(salvato) })
            }
        }

        if (!haScorta) {
            item { Vuoto(onScarica) }
            return@LazyColumn
        }

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

/**
 * La domanda libera: il secondo strato di Esplora.
 *
 * Sta sopra l'elenco locale e **non al suo posto**. Se la chiave non e'
 * configurata il campo non compare affatto: un campo che risponde sempre
 * "configura una chiave" e' peggio di nessun campo.
 */
@Composable
private fun Domanda(
    domanda: String,
    aiConfigurata: Boolean,
    inCorso: Boolean,
    onDomanda: (String) -> Unit,
    onChiedi: () -> Unit,
    onImpostaAi: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!aiConfigurata) {
            Text(
                stringResource(R.string.esplora_ai_da_configurare),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onImpostaAi) {
                Text(stringResource(R.string.esplora_ai_configura))
            }
            return@Column
        }

        OutlinedTextField(
            value = domanda,
            onValueChange = onDomanda,
            label = { Text(stringResource(R.string.esplora_chiedi)) },
            placeholder = { Text(stringResource(R.string.esplora_chiedi_esempio)) },
            minLines = 2,
            enabled = !inCorso,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = domanda.trim().length >= 3 && !inCorso,
                onClick = onChiedi,
            ) { Text(stringResource(R.string.esplora_chiedi_azione)) }

            if (inCorso) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Text(
            stringResource(R.string.esplora_chiedi_spiegazione),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una risposta salvata: si rilegge offline, ed e' il motivo per cui si salva. */
@Composable
private fun RigaDossier(dossier: Dossier, onTocco: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dossier.titolo(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(
                    dossier.istante.format(QUANDO),
                    dossier.tappa,
                    dossier.modello,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

private val QUANDO: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.ITALIAN)
