package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.archivio.Csv
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa

/** Le azioni possibili su una tappa, decise dal suo stato. */
@Composable
fun AzioniTappaDialog(
    tappa: Tappa,
    onCheckin: () -> Unit,
    onAlterna: () -> Unit,
    onChiudi: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(tappa.nome) },
        text = {
            Text(
                when (tappa.stato) {
                    StatoTappa.DA_FARE -> stringResource(R.string.tappa_da_fare)
                    StatoTappa.FATTA -> stringResource(R.string.tappa_fatta)
                    StatoTappa.SALTATA -> stringResource(R.string.tappa_saltata)
                },
            )
        },
        confirmButton = {
            if (tappa.stato == StatoTappa.DA_FARE) {
                TextButton(onClick = { onChiudi(); onCheckin() }) {
                    Text(stringResource(R.string.azione_checkin))
                }
            }
        },
        dismissButton = {
            Row {
                // Su una tappa dove sei stato, saltare non vuol dire niente.
                if (tappa.stato != StatoTappa.FATTA) {
                    TextButton(onClick = { onChiudi(); onAlterna() }) {
                        Text(
                            if (tappa.stato == StatoTappa.SALTATA) {
                                stringResource(R.string.azione_ripristina)
                            } else {
                                stringResource(R.string.azione_salta)
                            },
                        )
                    }
                }
                TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_chiudi)) }
            }
        },
    )
}

/** Una nota di viaggio. Un campo, due tocchi. */
@Composable
fun NotaDialog(onSalva: (String) -> Unit, onChiudi: () -> Unit) {
    var testo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.nota_titolo)) },
        text = {
            OutlinedTextField(
                value = testo,
                onValueChange = { testo = it },
                label = { Text(stringResource(R.string.nota_campo)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = testo.isNotBlank(),
                onClick = { onChiudi(); onSalva(testo) },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}

/** La didascalia di una foto appena scattata: facoltativa. */
@Composable
fun DidascaliaDialog(onSalva: (String?) -> Unit, onScarta: () -> Unit) {
    var testo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onSalva(null) },
        title = { Text(stringResource(R.string.foto_titolo)) },
        text = {
            Column {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text(stringResource(R.string.foto_didascalia)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.foto_didascalia_facoltativa),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSalva(testo.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.azione_salva))
            }
        },
        dismissButton = {
            TextButton(onClick = onScarta) { Text(stringResource(R.string.azione_scarta)) }
        },
    )
}

/**
 * Aggiungi una tappa.
 *
 * Era un wizard di quattro messaggi sul bot; qui e' una form con quattro
 * campi, compilabili in qualsiasi ordine e correggibili senza ricominciare.
 * La posizione arriva dal GPS con un tocco, oppure si digita.
 */
@Composable
fun AggiungiTappaDialog(
    tappe: List<Tappa>,
    onPrendiPosizione: () -> Unit,
    coordinatePronte: Pair<Double, Double>?,
    onSalva: (nome: String, lat: Double, lon: Double, giorno: String?, primaDi: String?) -> Unit,
    onChiudi: () -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var giorno by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var primaDi by remember { mutableStateOf<Tappa?>(null) }
    var menuAperto by remember { mutableStateOf(false) }

    // Quando il GPS risponde riempie le coordinate senza toccare il resto:
    // il nome che l'utente stava scrivendo non deve sparire.
    LaunchedEffect(coordinatePronte) {
        coordinatePronte?.let {
            lat = Csv.numero(it.first, 6)
            lon = Csv.numero(it.second, 6)
        }
    }

    val latitudine = Csv.leggiNumero(lat)
    val longitudine = Csv.leggiNumero(lon)
    val valida = nome.isNotBlank() &&
        latitudine != null && latitudine in -90.0..90.0 &&
        longitudine != null && longitudine in -180.0..180.0

    val etichettaPosizione = primaDi
        ?.let { stringResource(R.string.aggiungi_prima_di, it.nome) }
        ?: stringResource(R.string.aggiungi_in_fondo)

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.aggiungi_titolo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(stringResource(R.string.aggiungi_nome)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = giorno,
                    onValueChange = { giorno = it },
                    label = { Text(stringResource(R.string.aggiungi_giorno)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text(stringResource(R.string.aggiungi_lat)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text(stringResource(R.string.aggiungi_lon)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onPrendiPosizione) {
                    Text(stringResource(R.string.aggiungi_da_gps))
                }

                Text(
                    stringResource(R.string.aggiungi_posizione),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    TextButton(onClick = { menuAperto = true }) { Text(etichettaPosizione) }
                    DropdownMenu(expanded = menuAperto, onDismissRequest = { menuAperto = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aggiungi_in_fondo)) },
                            onClick = { primaDi = null; menuAperto = false },
                        )
                        tappe.forEach { tappa ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.aggiungi_prima_di, tappa.nome)) },
                                onClick = { primaDi = tappa; menuAperto = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valida,
                onClick = {
                    onChiudi()
                    onSalva(
                        nome.trim(),
                        latitudine ?: 0.0,
                        longitudine ?: 0.0,
                        giorno.trim().takeIf { it.isNotBlank() },
                        primaDi?.id,
                    )
                },
            ) { Text(stringResource(R.string.azione_aggiungi)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}

/**
 * Un rifornimento: contachilometri, litri, importo, pieno sì/no.
 *
 * Il chilometraggio arriva precompilato con l'ultimo registrato, che di solito
 * va solo corretto nelle ultime cifre. L'importo e' facoltativo: registrare un
 * rifornimento senza ricordare la spesa e' meglio che non registrarlo.
 */
@Composable
fun RifornimentoDialog(
    ultimoKm: Int?,
    onSalva: (km: Int, litri: Double, euro: Double?, pieno: Boolean) -> Unit,
    onChiudi: () -> Unit,
) {
    var km by remember { mutableStateOf(ultimoKm?.toString().orEmpty()) }
    var litri by remember { mutableStateOf("") }
    var euro by remember { mutableStateOf("") }
    var pieno by remember { mutableStateOf(true) }

    val chilometri = Csv.leggiIntero(km)
    val quantita = Csv.leggiNumero(litri)
    val importo = Csv.leggiNumero(euro)

    val valida = chilometri != null && chilometri > 0 &&
        quantita != null && quantita > 0 &&
        (euro.isBlank() || importo != null)

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.rifornimento_titolo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text(stringResource(R.string.rifornimento_km)) },
                    // Con `ultimoKm?.let { { Text(...) } }` la lambda esce da
                    // `let` come una funzione normale e non come @Composable,
                    // e non e' assegnabile qui. Con un `if` in posizione di
                    // argomento il tipo atteso si propaga nei rami.
                    supportingText = if (ultimoKm == null) {
                        null
                    } else {
                        { Text(stringResource(R.string.rifornimento_ultimo_km, ultimoKm)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = litri,
                        onValueChange = { litri = it },
                        label = { Text(stringResource(R.string.rifornimento_litri)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = euro,
                        onValueChange = { euro = it },
                        label = { Text(stringResource(R.string.rifornimento_euro)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = pieno, onCheckedChange = { pieno = it })
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.rifornimento_pieno),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.rifornimento_pieno_spiegazione),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valida,
                onClick = {
                    onChiudi()
                    onSalva(chilometri ?: 0, quantita ?: 0.0, importo, pieno)
                },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}

/** I km con un pieno: il solo parametro del mezzo che l'app conosce. */
@Composable
fun ImpostazioniDialog(
    kmConUnPieno: Int?,
    onSalva: (Int?) -> Unit,
    onChiudi: () -> Unit,
) {
    var km by remember { mutableStateOf(kmConUnPieno?.toString().orEmpty()) }
    val valore = Csv.leggiIntero(km)
    val valida = km.isBlank() || (valore != null && valore > 0)

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.impostazioni_titolo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text(stringResource(R.string.impostazioni_km_pieno)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.impostazioni_km_pieno_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valida,
                onClick = { onChiudi(); onSalva(valore) },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}
