package it.myacamperlife.app.ui.diario

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Voce
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Il diario: le giornate, dalla piu' recente, con gli eventi di ciascuna.
 *
 * Mostra le voci lette dalle tabelle e non il testo di `diario.md`: le
 * tabelle sono la verita', il file e' una vista. Se il file venisse
 * cancellato, questa schermata continuerebbe a funzionare.
 */
@Composable
fun DiarioContent(voci: List<Voce>, giorni: List<LocalDate>) {
    if (voci.isEmpty()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.diario_vuoto),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.diario_vuoto_spiegazione),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        giorni.forEach { giorno ->
            val delGiorno = voci.filter { it.istante.toLocalDate() == giorno }

            item(key = "testa-$giorno") {
                Text(
                    text = giorno.format(GIORNO),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                )
                HorizontalDivider()
            }

            // Senza chiave: due voci nello stesso secondo e dello stesso
            // genere darebbero una chiave duplicata, e LazyColumn cade.
            items(delGiorno) { voce ->
                RigaVoce(voce)
            }
        }
    }
}

@Composable
private fun RigaVoce(voce: Voce) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = voce.istante.format(ORA),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = etichetta(voce),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = testo(voce),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun etichetta(voce: Voce): String = stringResource(
    when (voce.genere) {
        Genere.ARRIVO -> R.string.genere_arrivo
        Genere.POSIZIONE -> R.string.genere_posizione
        Genere.NOTA -> R.string.genere_nota
        Genere.FOTO -> R.string.genere_foto
        Genere.RIFORNIMENTO -> R.string.genere_rifornimento
        Genere.SPESA -> R.string.genere_spesa
    },
)

@Composable
private fun testo(voce: Voce): String = when {
    voce.testo.isNotBlank() -> voce.testo
    voce.allegato != null -> voce.allegato
    else -> stringResource(R.string.voce_senza_testo)
}

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val GIORNO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
