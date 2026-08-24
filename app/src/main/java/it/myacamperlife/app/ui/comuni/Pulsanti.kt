package it.myacamperlife.app.ui.comuni

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Il pulsante delle azioni dentro una schermata.
 *
 * **Un pulsante deve sembrare un pulsante.** Fino a ieri erano tutti
 * `TextButton`: una parola colorata in mezzo ad altro testo, che sulle schermate
 * piene di righe — l'elenco delle tappe, il diario, Esplora — non si distingue da
 * un titolo. Adesso hanno un fondo: quello del contenitore primario, cioe' lo
 * stesso della testata del viaggio, che nel tema scuro e' un marrone d'asfalto
 * caldo e nel chiaro una crema. Sta un gradino sopra lo sfondo e nemmeno uno
 * sopra il contenuto: si vede che si tocca, non urla.
 *
 * **Nei dialoghi resta il testo nudo.** Li' i pulsanti stanno sempre nello stesso
 * angolo, la convenzione e' fortissima, e riempirli farebbe sembrare ogni
 * domanda un modulo da compilare.
 */
@Composable
fun PulsanteAzione(
    etichetta: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    abilitato: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = abilitato,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        // Piu' stretto del riposo di Material: in una fila che va a capo — le
        // azioni di una tappa — il riempimento normale fa entrare due pulsanti
        // per riga invece di tre.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(etichetta))
    }
}

/** La stessa cosa con un testo gia' composto, dove l'etichetta non e' fissa. */
@Composable
fun PulsanteAzione(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    abilitato: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = abilitato,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(testo)
    }
}
