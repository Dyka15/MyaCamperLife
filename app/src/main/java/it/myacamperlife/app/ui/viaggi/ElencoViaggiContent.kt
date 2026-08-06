package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.archivio.Viaggio
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ElencoViaggiContent(
    viaggi: List<Viaggio>,
    onApri: (Viaggio) -> Unit,
    onElimina: (Viaggio) -> Unit,
) {
    if (viaggi.isEmpty()) {
        NessunViaggio()
        return
    }

    var daEliminare by remember { mutableStateOf<Viaggio?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(viaggi, key = { it.slug }) { viaggio ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApri(viaggio) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(viaggio.nome, style = MaterialTheme.typography.titleMedium)
                        Text(
                            dataLeggibile(viaggio.creatoIl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { daEliminare = viaggio }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_elimina),
                            contentDescription = stringResource(R.string.azione_elimina),
                        )
                    }
                }
            }
        }
    }

    daEliminare?.let { viaggio ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text(stringResource(R.string.elimina_titolo)) },
            text = { Text(stringResource(R.string.elimina_spiegazione, viaggio.nome)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        daEliminare = null
                        onElimina(viaggio)
                    },
                ) { Text(stringResource(R.string.azione_elimina)) }
            },
            dismissButton = {
                TextButton(onClick = { daEliminare = null }) {
                    Text(stringResource(R.string.azione_annulla))
                }
            },
        )
    }
}

@Composable
private fun NessunViaggio() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.vuoto_titolo),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.vuoto_spiegazione),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * La data di creazione in forma leggibile. Se il campo e' illeggibile si
 * mostra come sta scritto invece di far cadere la schermata: un file
 * modificato a mano non deve rendere l'app inutilizzabile.
 */
private fun dataLeggibile(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(FORMATO)
}.getOrDefault(iso)

private val FORMATO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
