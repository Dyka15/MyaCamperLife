package it.myacamperlife.app.ui.numeri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Autonomia
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Segmento

/**
 * La scheda Numeri: autonomia e consumi.
 *
 * Sono numeri che il sistema di prima non produceva — un foglio di calcolo
 * registra litri e importi, ma non li divide per i chilometri. Ogni valore
 * dichiara come e' nato: una stima si chiama stima.
 */
@Composable
fun NumeriContent(
    consumo: Consumo,
    autonomia: Autonomia?,
    kmConUnPieno: Int?,
    onImpostaKm: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AutonomiaCard(autonomia, kmConUnPieno, onImpostaKm) }
        item { ConsumoCard(consumo) }

        if (consumo.segmenti.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.numeri_tratti),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(consumo.segmenti.asReversed()) { tratto -> RigaTratto(tratto) }
        }
    }
}

@Composable
private fun AutonomiaCard(autonomia: Autonomia?, kmConUnPieno: Int?, onImpostaKm: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.numeri_autonomia),
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                kmConUnPieno == null -> Spiegazione(
                    testo = stringResource(R.string.autonomia_senza_parametro),
                    azione = stringResource(R.string.autonomia_imposta),
                    onAzione = onImpostaKm,
                )

                autonomia == null -> Spiegazione(
                    testo = stringResource(R.string.autonomia_senza_pieno),
                )

                else -> {
                    Text(
                        stringResource(R.string.autonomia_km, arrotonda(autonomia.residui)),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = { autonomia.frazione },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        stringResource(
                            R.string.autonomia_dettaglio,
                            arrotonda(autonomia.kmStimati),
                            autonomia.kmConUnPieno,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // L'avvertenza non e' cortesia: il numero e' ottimista per
                    // costruzione, e chi lo legge deve saperlo.
                    Text(
                        stringResource(
                            if (autonomia.senzaDati) R.string.autonomia_senza_punti
                            else R.string.autonomia_e_una_stima,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsumoCard(consumo: Consumo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.numeri_consumo),
                style = MaterialTheme.typography.titleMedium,
            )

            val kmPerLitro = consumo.kmPerLitro
            if (kmPerLitro == null) {
                Spiegazione(testo = stringResource(R.string.consumo_servono_due_pieni))
                return@Column
            }

            Text(
                stringResource(R.string.consumo_km_litro, decimale(kmPerLitro, 1)),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Voce(stringResource(R.string.consumo_litri_100), decimale(consumo.litriPer100!!, 1))
            consumo.euroPer100?.let {
                Voce(stringResource(R.string.consumo_euro_100), decimale(it, 2))
            }
            Voce(
                stringResource(R.string.consumo_misurato_su),
                stringResource(R.string.consumo_km_e_litri, consumo.kmTotali, decimale(consumo.litriTotali, 1)),
            )
        }
    }
}

@Composable
private fun RigaTratto(tratto: Segmento) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.tratto_km, tratto.daKm, tratto.aKm),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.consumo_km_litro, decimale(tratto.kmPerLitro, 1)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = listOfNotNull(
                stringResource(R.string.tratto_dettaglio, tratto.km, decimale(tratto.litri, 1)),
                tratto.euro?.let { stringResource(R.string.tratto_euro, decimale(it, 2)) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Voce(etichetta: String, valore: String) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            etichetta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(valore, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Spiegazione(testo: String, azione: String? = null, onAzione: (() -> Unit)? = null) {
    Text(
        testo,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (azione != null && onAzione != null) {
        TextButton(onClick = onAzione) { Text(azione) }
    }
}

/**
 * I numeri si formattano con la lingua del telefono, quindi con la virgola
 * decimale su un telefono italiano. E' l'opposto della regola dei file, dove
 * il formato e' fisso: qui si scrive per una persona, li' per un programma.
 */
private fun decimale(valore: Double, cifre: Int): String = "%.${cifre}f".format(valore)

private fun arrotonda(valore: Double): Int = Math.round(valore).toInt()
