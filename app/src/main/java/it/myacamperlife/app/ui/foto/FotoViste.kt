package it.myacamperlife.app.ui.foto

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La miniatura di una foto dell'archivio.
 *
 * **La decodifica sta fuori dalla composizione**, su `Dispatchers.IO`: aprire un
 * JPEG e ruotarlo costa decine di millisecondi, e farlo mentre si compone
 * significa una lista che scatta a ogni scorrimento.
 *
 * Un file che non c'e' piu' — cancellato dal gestore file, o mai copiato al
 * cambio di telefono — mostra un riquadro con scritto cosa e' successo. Un
 * quadrato vuoto lascerebbe pensare che l'app sia rotta.
 */
@Composable
fun Miniatura(
    file: File?,
    modifier: Modifier = Modifier,
    lato: Int = 64,
    onTocco: (() -> Unit)? = null,
) {
    val riquadro = modifier
        .size(lato.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (file == null) {
        Box(modifier = riquadro)
        return
    }

    val bitmap by caricata(file, Immagini.MINIATURA)

    Box(
        modifier = riquadro.then(
            if (onTocco != null && bitmap != null) Modifier.clickable(onClick = onTocco) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val immagine = bitmap
        when {
            immagine != null -> Image(
                bitmap = immagine.asImageBitmap(),
                contentDescription = stringResource(R.string.foto_descrizione),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(lato.dp),
            )

            !file.isFile -> Text(
                text = stringResource(R.string.foto_persa_breve),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )

            else -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/**
 * La foto a schermo (quasi) pieno, con la sua didascalia.
 *
 * **Non zooma e non fa scorrere.** Guardare la foto per riconoscerla e' quello
 * che serve nel diario; ingrandire uno scontrino per leggere una cifra e'
 * un'altra cosa, e la fa meglio la galleria del telefono — da qui ci si arriva
 * con "Apri con", che passa il file senza copiarlo.
 */
@Composable
fun FotoDialog(
    file: File?,
    didascalia: String?,
    onApriFuori: () -> Unit,
    onChiudi: () -> Unit,
) {
    val bitmap by caricata(file, Immagini.GRANDE)

    AlertDialog(
        onDismissRequest = onChiudi,
        title = null,
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val immagine = bitmap
                    when {
                        immagine != null -> Image(
                            bitmap = immagine.asImageBitmap(),
                            contentDescription = stringResource(R.string.foto_descrizione),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        file == null || !file.isFile -> Text(
                            text = stringResource(R.string.foto_persa),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )

                        else -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                }

                didascalia?.takeUnless { it.isBlank() }?.let { testo ->
                    Text(
                        text = testo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                file?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_chiudi)) }
        },
        dismissButton = {
            // Solo se il file c'e': un pulsante che aprirebbe il nulla e' peggio
            // di nessun pulsante.
            if (file?.isFile == true) {
                TextButton(onClick = onApriFuori) {
                    Text(stringResource(R.string.foto_apri_fuori))
                }
            }
        },
    )
}

/**
 * La bitmap di un file, decodificata fuori dalla composizione.
 *
 * La chiave comprende `lastModified`: correggendo una foto — cosa che oggi non si
 * fa, ma il giorno che si facesse — la vista si aggiorna invece di mostrare
 * quella di prima.
 */
@Composable
private fun caricata(file: File?, lato: Int) = produceState<Bitmap?>(
    initialValue = null,
    file?.absolutePath,
    file?.lastModified(),
    lato,
) {
    value = file?.let { withContext(Dispatchers.IO) { Immagini.carica(it, lato) } }
}
