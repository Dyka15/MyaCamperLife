package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Percorso
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * La schermata d'apertura del viaggio: dove sei, dove vai, l'itinerario, e le
 * azioni rapide.
 *
 * Il metro di paragone e' il bot: mandargli una foto non richiedeva comandi.
 * Se qui registrare qualcosa costasse sei tocchi, l'app sarebbe peggiore di
 * quello che sostituisce. Da qui le azioni in cima, sempre a portata.
 */
@Composable
fun TappeContent(
    tappe: List<Tappa>,
    corrente: Tappa?,
    prossima: Tappa?,
    versoProssima: Percorso?,
    onPosizione: () -> Unit,
    onFoto: () -> Unit,
    onNota: () -> Unit,
    onLitri: () -> Unit,
    onSpesa: () -> Unit,
    onTappa: (Tappa) -> Unit,
    /** Carica un itinerario nuovo per il seguito del viaggio. */
    onSostituisci: () -> Unit,
    /**
     * Cos'e' diventato l'ultimo itinerario caricato.
     *
     * Sta **qui** e non solo nelle impostazioni perche' e' qui che nasce la
     * domanda: si guarda questo elenco per vedere se il file ha fatto quello che
     * si voleva, e se non l'ha fatto questa riga dice cos'ha fatto invece.
     */
    ultimoImport: String? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Testata(corrente, prossima, versoProssima)
            AzioniRapide(onPosizione, onFoto, onNota, onLitri, onSpesa)
            HorizontalDivider()
        }

        items(tappe, key = { it.id }) { tappa ->
            RigaTappa(tappa, onTocco = { onTappa(tappa) })
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

        // **In fondo all'itinerario, dove la domanda nasce.** Si arriva qui
        // scorrendo le tappe che restano, ed e' guardandole che uno si accorge
        // che non sono piu' quelle che vuole fare.
        if (tappe.any { it.stato == StatoTappa.DA_FARE }) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    TextButton(onClick = onSostituisci) {
                        Text(stringResource(R.string.azione_sostituisci_itinerario))
                    }
                    Text(
                        stringResource(R.string.sostituisci_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ultimoImport?.let { esito ->
                        Text(
                            text = stringResource(R.string.tappe_ultimo_import, esito),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Testata(corrente: Tappa?, prossima: Tappa?, versoProssima: Percorso?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = corrente?.let { tappa ->
                    val ora = oraDiArrivo(tappa.checkinIl)
                    if (ora != null) {
                        stringResource(R.string.sei_a_dalle, tappa.nome, ora)
                    } else {
                        stringResource(R.string.sei_a, tappa.nome)
                    }
                } ?: stringResource(R.string.non_ancora_partito),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = prossima?.let { stringResource(R.string.prossima, it.nome) }
                    ?: stringResource(R.string.itinerario_finito),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Solo con le tratte precalcolate: la linea d'aria qui sembrerebbe
            // una distanza di guida senza esserlo, e nessuno leggerebbe la
            // nota che lo spiega.
            if (prossima != null && versoProssima != null) {
                Text(
                    text = stringResource(
                        R.string.prossima_distanza,
                        Math.round(versoProssima.km).toInt(),
                        versoProssima.durata,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AzioniRapide(
    onPosizione: () -> Unit,
    onFoto: () -> Unit,
    onNota: () -> Unit,
    onLitri: () -> Unit,
    onSpesa: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AzioneRapida(R.drawable.ic_posizione, R.string.azione_posizione, onPosizione)
        AzioneRapida(R.drawable.ic_foto, R.string.azione_foto, onFoto)
        AzioneRapida(R.drawable.ic_nota, R.string.azione_nota, onNota)
        AzioneRapida(R.drawable.ic_litri, R.string.azione_litri, onLitri)
        AzioneRapida(R.drawable.ic_spesa, R.string.azione_spesa, onSpesa)
    }
}

/**
 * Cinque azioni su una riga: il riempimento interno del pulsante e' ridotto
 * perche' su uno schermo stretto le etichette non vadano a capo.
 */
@Composable
private fun AzioneRapida(icona: Int, etichetta: Int, onTocco: () -> Unit) {
    TextButton(
        onClick = onTocco,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = painterResource(icona), contentDescription = null)
            Text(
                stringResource(etichetta),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RigaTappa(tappa: Tappa, onTocco: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
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

/** L'ora del check-in, o `null` se il campo e' assente o illeggibile. */
private fun oraDiArrivo(iso: String?): String? = iso?.let {
    runCatching { OffsetDateTime.parse(it).format(ORA) }.getOrNull()
}

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
