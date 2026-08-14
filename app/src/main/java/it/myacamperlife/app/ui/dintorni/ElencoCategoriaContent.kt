package it.myacamperlife.app.ui.dintorni

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.Luoghi
import it.myacamperlife.app.dominio.PoiVicino

/**
 * L'elenco completo di una categoria attorno a un punto.
 *
 * Nasce da una domanda precisa: la scheda di una tappa diceva "Da vedere · 24"
 * e il numero non si poteva aprire. Ventiquattro attrazioni annunciate e
 * nessuna leggibile e' peggio che non contarle.
 *
 * Tre cose per riga, e ognuna risponde a una domanda diversa: **cosa** (il nome),
 * **dove** (il paese, non le coordinate — un numero di gradi non dice a nessuno
 * se e' dietro l'angolo o dietro la collina), **quanto lontano**.
 *
 * Non c'e' tetto: il conteggio che ha portato qui deve tornare.
 */
@Composable
fun ElencoCategoriaContent(
    categoria: CategoriaPoi,
    /** Il nome del punto da cui si guarda: la tappa, o dove sei. */
    intorno: String,
    elenco: List<PoiVicino>,
    luoghi: Luoghi,
    /** Apre il punto nell'app di mappe del telefono: funziona senza rete. */
    onMappa: (PoiVicino) -> Unit,
    /** Apre la scheda del posto su Google Maps: orari, foto, recensioni. */
    onMaps: (PoiVicino) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.dintorni_categoria_titolo, categoria.nome, intorno),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.dintorni_categoria_quanti, elenco.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        items(elenco, key = { it.poi.id }) { vicino ->
            RigaPoi(
                vicino = vicino,
                luoghi = luoghi,
                onMappa = { onMappa(vicino) },
                onMaps = { onMaps(vicino) },
            )
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
    }
}

/**
 * Una riga di punto d'interesse, con i due collegamenti.
 *
 * Sta qui e non nelle due schermate che la usano perche' e' **la stessa riga**:
 * l'elenco di Esplora e quello di una categoria mostrano la stessa cosa, e
 * tenerne due copie vorrebbe dire migliorarne una sola.
 *
 * Due collegamenti e non uno, perche' rispondono a due domande. Il tocco sulla
 * riga apre l'app di mappe del telefono — Organic Maps, OsmAnd — e **funziona
 * senza rete**, che in viaggio e' la condizione normale. Il pulsante "Maps" apre
 * la scheda di Google, dove ci sono orari e recensioni: ha bisogno di rete, e
 * per questo non prende il posto del primo.
 *
 * Il nome puo' mancare: OpenStreetMap ha molti punti senza. In quel caso resta
 * il nome della categoria ("Punto d'interesse"), che con il paese accanto e' una
 * riga ancora utilizzabile.
 */
@Composable
fun RigaPoi(
    vicino: PoiVicino,
    luoghi: Luoghi,
    onMappa: () -> Unit,
    onMaps: () -> Unit,
) {
    // "Bolsena" se il punto e' dentro il paese, "3 km da Bolsena" se e' nei
    // paraggi, niente se fra i toponimi salvati non c'e' nulla di vicino:
    // meglio una riga senza paese che un paese sbagliato.
    val dove = luoghi.descrizione(vicino.poi.lat, vicino.poi.lon)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMappa)
            .padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(vicino.poi.etichetta(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(dove, vicino.poi.categoria.senzaNome, vicino.poi.dettaglio)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = vicino.distanza,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            TextButton(onClick = onMaps) { Text(stringResource(R.string.dintorni_maps)) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
