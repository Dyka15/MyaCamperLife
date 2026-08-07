package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.archivio.Csv
import it.myacamperlife.app.archivio.Impostazioni
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.TestoBriefing
import it.myacamperlife.app.dominio.Tappa
import java.io.File

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

/**
 * Le impostazioni: il mezzo, il riepilogo della sera, e i pulsanti per quando
 * il riepilogo non arriva.
 *
 * L'ultima sezione esiste per HyperOS. Un'app che non viene aperta per qualche
 * giorno viene congelata, e con lei sparisce la sveglia: non c'e' modo di
 * impedirlo dal codice, si puo' solo portare l'utente dove si disattiva. Se il
 * telefono non e' uno Xiaomi il pulsante dell'avvio automatico non compare.
 */
@Composable
fun ImpostazioniDialog(
    impostazioni: Impostazioni,
    notificheConcesse: Boolean,
    batteriaSenzaLimiti: Boolean,
    avvioAutomaticoDisponibile: Boolean,
    onSalva: (Impostazioni) -> Unit,
    onProvaBriefing: () -> Unit,
    onPermessoNotifiche: () -> Unit,
    onBatteria: () -> Unit,
    onAvvioAutomatico: () -> Unit,
    onChiudi: () -> Unit,
) {
    var km by remember { mutableStateOf(impostazioni.kmConUnPieno?.toString().orEmpty()) }
    var briefing by remember { mutableStateOf(impostazioni.briefingAttivo) }
    var ora by remember { mutableStateOf(impostazioni.ora.toString()) }

    val valore = Csv.leggiIntero(km)
    val oraScelta = Csv.leggiIntero(ora)
    val valida = (km.isBlank() || (valore != null && valore > 0)) &&
        oraScelta != null && oraScelta in 0..23

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.impostazioni_titolo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = briefing, onCheckedChange = { briefing = it })
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.impostazioni_briefing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.impostazioni_briefing_spiegazione),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (briefing) {
                    OutlinedTextField(
                        value = ora,
                        onValueChange = { ora = it },
                        label = { Text(stringResource(R.string.impostazioni_ora)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onProvaBriefing) {
                        Text(stringResource(R.string.impostazioni_prova_briefing))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringResource(R.string.impostazioni_se_non_arriva),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.impostazioni_se_non_arriva_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RigaSistema(
                    etichetta = stringResource(R.string.impostazioni_notifiche),
                    fatto = notificheConcesse,
                    onTocco = onPermessoNotifiche,
                )
                RigaSistema(
                    etichetta = stringResource(R.string.impostazioni_batteria),
                    fatto = batteriaSenzaLimiti,
                    onTocco = onBatteria,
                )
                if (avvioAutomaticoDisponibile) {
                    RigaSistema(
                        etichetta = stringResource(R.string.impostazioni_avvio_automatico),
                        // Il sistema non dice se e' concesso: si puo' solo
                        // portarci l'utente e fidarsi.
                        fatto = null,
                        onTocco = onAvvioAutomatico,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valida,
                onClick = {
                    onChiudi()
                    onSalva(
                        impostazioni.copy(
                            kmConUnPieno = valore,
                            briefingAttivo = briefing,
                            oraBriefing = oraScelta ?: impostazioni.ora,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}

/**
 * Una riga della sezione di sistema: cosa manca, e un tocco per andarci.
 *
 * @param fatto `null` quando il sistema non lo dichiara — l'avvio automatico di
 *   Xiaomi non e' interrogabile, e mostrare una spunta inventata sarebbe
 *   peggio di non mostrarne nessuna.
 */
@Composable
private fun RigaSistema(etichetta: String, fatto: Boolean?, onTocco: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (fatto) {
                true -> "\u2713"
                false -> "\u25CB"
                null -> "\u2192"
            },
            fontFamily = FontFamily.Monospace,
            color = if (fatto == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(28.dp),
        )
        Text(etichetta, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Il riepilogo che arriverebbe stasera, mostrato adesso.
 *
 * E' l'unico modo sensato di provare una funzione che scatta una volta al
 * giorno: il testo e' esattamente quello della notifica, composto dalle stesse
 * funzioni.
 */
@Composable
fun BriefingDialog(briefing: Briefing?, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = {
            Text(
                if (briefing == null || briefing.vuoto) {
                    stringResource(R.string.briefing_niente)
                } else {
                    TestoBriefing.titolo(briefing)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (briefing == null || briefing.vuoto) {
                    Text(
                        stringResource(R.string.briefing_niente_spiegazione),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val corpo = TestoBriefing.corpo(briefing)
                    if (corpo.isNotBlank()) {
                        Text(corpo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.briefing_anteprima),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_chiudi)) }
        },
    )
}

/**
 * Una spesa: categoria, importo, come hai pagato.
 *
 * Le tre cose obbligatorie stanno in cima e si compilano con tre tocchi e una
 * cifra. Tutto il resto — descrizione, valuta estera, scontrino — sta sotto e
 * si puo' ignorare: e' il genere di attrito che fa smettere di registrare.
 *
 * Lo scontrino si fotografa e resta allegato alla spesa, con un nome che porta
 * data, ora e luogo come le foto del diario.
 */
@Composable
fun SpesaDialog(
    valutaSuggerita: String,
    cambioSuggerito: Double?,
    scontrino: File?,
    onScontrino: () -> Unit,
    onSalva: (
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String?,
        valuta: String,
        cambio: Double?,
    ) -> Unit,
    onChiudi: () -> Unit,
) {
    var categoria by remember { mutableStateOf(Categoria.SOSTA) }
    var modalita by remember { mutableStateOf(Modalita.CONTANTI) }
    var importo by remember { mutableStateOf("") }
    var descrizione by remember { mutableStateOf("") }
    var valuta by remember { mutableStateOf(valutaSuggerita) }
    var cambio by remember {
        mutableStateOf(cambioSuggerito?.let { Csv.numero(it, 4) }.orEmpty())
    }
    var estera by remember { mutableStateOf(!valutaSuggerita.equals(Spesa.EURO, true)) }

    val quanto = Csv.leggiNumero(importo)
    val tasso = Csv.leggiNumero(cambio)
    val sigla = valuta.trim().uppercase().ifEmpty { Spesa.EURO }
    val esteraDavvero = estera && sigla != Spesa.EURO

    val valida = quanto != null && quanto > 0 &&
        (!esteraDavvero || (tasso != null && tasso > 0))

    val inEuro = if (esteraDavvero && quanto != null && tasso != null) quanto * tasso else null

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.spesa_titolo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                SceltaCategoria(categoria) { categoria = it }

                OutlinedTextField(
                    value = importo,
                    onValueChange = { importo = it },
                    label = { Text(stringResource(R.string.spesa_importo, sigla)) },
                    // Stessa forma usata per il chilometraggio: con un `if` in
                    // posizione di argomento il tipo @Composable si propaga nei
                    // rami, cosa che dentro un `let` non succede.
                    supportingText = if (inEuro == null) {
                        null
                    } else {
                        { Text(stringResource(R.string.spesa_in_euro, Csv.numero(inEuro))) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                SceltaModalita(modalita) { modalita = it }

                OutlinedTextField(
                    value = descrizione,
                    onValueChange = { descrizione = it },
                    label = { Text(stringResource(R.string.spesa_descrizione)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = estera,
                        onCheckedChange = { acceso ->
                            estera = acceso
                            if (!acceso) valuta = Spesa.EURO
                            else if (valuta.equals(Spesa.EURO, true)) valuta = ""
                        },
                    )
                    Text(
                        stringResource(R.string.spesa_valuta_estera),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                if (estera) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = valuta,
                            onValueChange = { valuta = it.take(3) },
                            label = { Text(stringResource(R.string.spesa_valuta)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cambio,
                            onValueChange = { cambio = it },
                            label = { Text(stringResource(R.string.spesa_cambio, sigla)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.spesa_cambio_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                TextButton(onClick = onScontrino) {
                    Text(
                        stringResource(
                            if (scontrino == null) R.string.spesa_scontrino_scatta
                            else R.string.spesa_scontrino_rifai,
                        ),
                    )
                }
                if (scontrino != null) {
                    Text(
                        scontrino.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valida,
                onClick = {
                    // Non chiama `onChiudi`: chiudere scarta la foto dello
                    // scontrino, e qui la foto serve. Chi salva chiude da se'.
                    onSalva(
                        categoria,
                        quanto ?: 0.0,
                        modalita,
                        descrizione.trim().takeIf { it.isNotBlank() },
                        if (esteraDavvero) sigla else Spesa.EURO,
                        if (esteraDavvero) tasso else null,
                    )
                },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
        },
    )
}

/**
 * Le categorie come pastiglie invece che come menu a tendina: sono otto, e
 * vederle tutte insieme costa un tocco invece di due.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceltaCategoria(scelta: Categoria, onScelta: (Categoria) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Categoria.entries.forEach { voce ->
            FilterChip(
                selected = voce == scelta,
                onClick = { onScelta(voce) },
                label = { Text(stringResource(etichettaCategoria(voce))) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceltaModalita(scelta: Modalita, onScelta: (Modalita) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Modalita.entries.forEachIndexed { indice, voce ->
            SegmentedButton(
                selected = voce == scelta,
                onClick = { onScelta(voce) },
                shape = SegmentedButtonDefaults.itemShape(indice, Modalita.entries.size),
            ) {
                Text(stringResource(etichettaModalita(voce)))
            }
        }
    }
}

fun etichettaCategoria(categoria: Categoria): Int = when (categoria) {
    Categoria.SOSTA -> R.string.categoria_sosta
    Categoria.PEDAGGI -> R.string.categoria_pedaggi
    Categoria.SPESA -> R.string.categoria_spesa
    Categoria.RISTORANTE -> R.string.categoria_ristorante
    Categoria.VISITE -> R.string.categoria_visite
    Categoria.TRASPORTI -> R.string.categoria_trasporti
    Categoria.MEZZO -> R.string.categoria_mezzo
    Categoria.ALTRO -> R.string.categoria_altro
}

fun etichettaModalita(modalita: Modalita): Int = when (modalita) {
    Modalita.CONTANTI -> R.string.modalita_contanti
    Modalita.POS -> R.string.modalita_pos
    Modalita.CARTA -> R.string.modalita_carta
}
