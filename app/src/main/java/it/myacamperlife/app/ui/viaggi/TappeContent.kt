package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa

/**
 * L'elenco delle tappe del viaggio aperto.
 *
 * Fase 1: si guarda. Check-in, salta e aggiungi arrivano alla fase 2, e con
 * loro gli stati diventeranno modificabili da qui.
 */
@Composable
fun TappeContent(tappe: List<Tappa>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Riepilogo(tappe)
            HorizontalDivider()
        }

        items(tappe, key = { it.id }) { tappa ->
            RigaTappa(tappa)
        }

        if (tappe.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.viaggio_senza_tappe),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Riepilogo(tappe: List<Tappa>) {
    val fatte = tappe.count { it.stato == StatoTappa.FATTA }
    val saltate = tappe.count { it.stato == StatoTappa.SALTATA }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.riepilogo_tappe, tappe.size),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.riepilogo_stati, fatte, saltate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RigaTappa(tappa: Tappa) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Il segno di stato e' un carattere, non un'icona: due glifi non
        // giustificano dieci megabyte di material-icons-extended.
        Text(
            text = segno(tappa.stato),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = when (tappa.stato) {
                StatoTappa.FATTA -> MaterialTheme.colorScheme.primary
                StatoTappa.SALTATA -> MaterialTheme.colorScheme.onSurfaceVariant
                StatoTappa.DA_FARE -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.width(28.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tappa.nome,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration =
                    if (tappa.stato == StatoTappa.SALTATA) TextDecoration.LineThrough else null,
            )

            val sotto = listOfNotNull(
                tappa.giorno,
                tappa.tipo,
                stringResource(
                    R.string.coordinate,
                    "%.4f".format(tappa.lat),
                    "%.4f".format(tappa.lon),
                ),
            ).joinToString(" · ")

            Text(
                text = sotto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            tappa.descrizione?.let { descrizione ->
                Text(
                    text = descrizione,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Text(
            text = tappa.ordine.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

private fun segno(stato: StatoTappa): String = when (stato) {
    StatoTappa.FATTA -> "✓"
    StatoTappa.DA_FARE -> "○"
    StatoTappa.SALTATA -> "⤫"
}
