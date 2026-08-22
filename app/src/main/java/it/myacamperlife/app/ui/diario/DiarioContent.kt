package it.myacamperlife.app.ui.diario

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.ui.foto.Miniatura
import java.io.File
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
fun DiarioContent(
    voci: List<Voce>,
    giorni: List<LocalDate>,
    prosaPossibile: Boolean,
    onProsa: (LocalDate) -> Unit,
    onCronaca: () -> Unit,
    /**
     * Apre `diario.md` con un'altra app: `null` se il file non c'e' ancora.
     *
     * Nullo e non spento: prima del primo diario non c'e' niente da aprire, e un
     * pulsante che non fa niente e' peggio di un pulsante che non c'e'.
     */
    onApriFile: (() -> Unit)? = null,
    onVoce: (Voce) -> Unit,
    /** Il file dell'allegato, per le voci che ne hanno uno. */
    allegato: (Voce) -> File?,
    onFoto: (Voce) -> Unit,
) {
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
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        giorni.forEach { giorno ->
            val delGiorno = voci.filter { it.istante.toLocalDate() == giorno }

            item(key = "testa-$giorno") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = giorno.format(GIORNO),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    // La prosa riscrive **solo la sezione di `diario.md`**: gli
                    // eventi restano nelle tabelle, ed e' per questo che si puo'
                    // offrire senza timori.
                    //
                    // Spento e non nascosto quando manca la chiave del modello:
                    // un pulsante che non c'e' non si distingue da una funzione
                    // che non esiste, e la domanda che ne nasce — "dov'e' il
                    // pulsante del diario?" — non ha nessun posto dove trovare
                    // risposta. Il perche' sta scritto in fondo alla schermata.
                    TextButton(onClick = { onProsa(giorno) }, enabled = prosaPossibile) {
                        Text(stringResource(R.string.diario_in_prosa))
                    }
                }
                HorizontalDivider()
            }

            // Senza chiave: due voci nello stesso secondo e dello stesso
            // genere darebbero una chiave duplicata, e LazyColumn cade.
            items(delGiorno) { voce ->
                RigaVoce(
                    voce = voce,
                    allegato = allegato(voce),
                    onTocco = { onVoce(voce) },
                    onFoto = { onFoto(voce) },
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                // **Questo pulsante non ha bisogno di nessun modello**: riscrive
                // `diario.md` dalle tabelle, che sono la verita'. Stava dentro
                // `if (prosaPossibile)` per il solo motivo che era nato come
                // l'annullamento della prosa — e cosi' senza una chiave API
                // l'unico modo di rifare il file del diario era invisibile.
                Row {
                    TextButton(onClick = onCronaca) {
                        Text(stringResource(R.string.diario_torna_cronaca))
                    }
                    // **Il diario e' un file, e si puo' portare via.** E' la
                    // ragione per cui l'archivio e' fatto di file di testo invece
                    // che di un database: aprirlo con l'editor che uno preferisce
                    // e' quella promessa, resa visibile.
                    onApriFile?.let { apri ->
                        TextButton(onClick = apri) {
                            Text(stringResource(R.string.diario_apri_file))
                        }
                    }
                }
                Text(
                    stringResource(R.string.diario_prosa_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!prosaPossibile) {
                    Text(
                        stringResource(R.string.diario_prosa_senza_chiavi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RigaVoce(
    voce: Voce,
    allegato: File?,
    onTocco: () -> Unit,
    onFoto: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Un tocco apre cosa si puo' farne: correggere, o cancellare. E'
            // l'unico posto da cui si torna su un evento gia' registrato.
            .clickable(onClick = onTocco)
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

        // La miniatura sta a destra e ha un tocco suo: sul resto della riga il
        // tocco apre correggi/cancella, e una foto va guardata senza passare per
        // un menu.
        if (allegato != null) {
            Miniatura(
                file = allegato,
                onTocco = onFoto,
                modifier = Modifier.padding(start = 8.dp),
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
