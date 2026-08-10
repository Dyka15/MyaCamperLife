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
import androidx.compose.runtime.rememberCoroutineScope
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
import it.myacamperlife.app.dominio.Carburante
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Esplora
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Indirizzo
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Modello
import it.myacamperlife.app.dominio.Momento
import it.myacamperlife.app.dominio.Rifornimento
import it.myacamperlife.app.dominio.Slittamento
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.dominio.TestoBriefing
import it.myacamperlife.app.rete.Provenienza
import it.myacamperlife.app.ui.foto.Miniatura
import it.myacamperlife.app.rete.RicercaIndirizzo
import it.myacamperlife.app.dominio.Tappa
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Cosa si puo' fare a una voce di diario gia' registrata.
 *
 * **Il formato lo prevedeva dal primo giorno** — `id`, `ts`, `cancellato`,
 * "vince l'ultima" — e per nove fasi nessuna schermata glielo chiedeva: un
 * rifornimento col chilometraggio sbagliato si aggiustava solo aprendo il CSV.
 * Questo dialogo e' quella parte mancante.
 *
 * Cancellare **chiede conferma**, correggere no: una correzione si corregge
 * ancora, una cancellazione dall'app non si annulla — la riga resta nel file, ma
 * per rimetterla in piedi bisognerebbe aprirlo, e non e' un'operazione da
 * proporre come rimedio in un messaggio.
 */
@Composable
fun AzioniVoceDialog(
    voce: Voce,
    /** La foto o lo scontrino della voce, se ne ha uno. */
    allegato: File?,
    onCorreggi: () -> Unit,
    onCancella: () -> Unit,
    onChiudi: () -> Unit,
) {
    var conferma by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = {
            Text(
                stringResource(
                    if (conferma) R.string.voce_cancella_titolo else R.string.voce_titolo,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // La miniatura c'e' soprattutto per la conferma di cancellazione:
                // "cancellare questa voce?" con la foto sotto gli occhi e' una
                // domanda a cui si puo' rispondere.
                if (allegato != null) Miniatura(file = allegato, lato = 96)
                Text(
                    text = voce.testo.ifBlank { stringResource(R.string.voce_senza_testo) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = voce.istante.format(VOCE_QUANDO),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (conferma) {
                    Text(
                        stringResource(R.string.voce_cancella_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Le due asimmetrie che sorprenderebbero, dette prima e non
                    // dopo: un file di foto non si tocca, e un check-in non si
                    // annulla cancellando la sua riga di diario.
                    if (voce.genere == Genere.FOTO) {
                        Text(
                            stringResource(R.string.voce_cancella_foto),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (voce.genere == Genere.ARRIVO) {
                        Text(
                            stringResource(R.string.voce_cancella_arrivo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (conferma) {
                TextButton(onClick = { onChiudi(); onCancella() }) {
                    Text(stringResource(R.string.azione_cancella))
                }
            } else if (voce.correggibile) {
                TextButton(onClick = { onChiudi(); onCorreggi() }) {
                    Text(stringResource(R.string.azione_correggi))
                }
            }
        },
        dismissButton = {
            Row {
                if (!conferma && voce.cancellabile) {
                    TextButton(onClick = { conferma = true }) {
                        Text(stringResource(R.string.azione_cancella))
                    }
                }
                TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_chiudi)) }
            }
        },
    )
}

private val VOCE_QUANDO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMMM, HH:mm", java.util.Locale.ITALIAN)

/**
 * Sei arrivato fuori programma: sposto il resto dell'itinerario?
 *
 * **Si chiede, non si fa.** Le date successive sono ormai sbagliate e con esse il
 * riepilogo della sera e il meteo di ogni tappa, ma riscrivere l'itinerario e' una
 * decisione di chi viaggia: magari il giorno perso lo recuperi domani, magari
 * salti una tappa. L'app se ne accorge e propone.
 *
 * Il numero di tappe interessate sta nella domanda: "sposto le prossime tre" e'
 * una cosa a cui si puo' rispondere, "sposto l'itinerario" no.
 */
@Composable
fun FuoriProgrammaDialog(
    tappa: Tappa,
    slittamento: Slittamento,
    onSlitta: () -> Unit,
    onChiudi: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = {
            Text(
                stringResource(
                    if (slittamento.ritardo) R.string.fuori_ritardo else R.string.fuori_anticipo,
                    slittamento.quanti,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.fuori_arrivato, tappa.nome),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        if (slittamento.ritardo) R.string.fuori_sposta_avanti
                        else R.string.fuori_sposta_indietro,
                        slittamento.daFare,
                        slittamento.quanti,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.fuori_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChiudi(); onSlitta() }) {
                Text(stringResource(R.string.fuori_sposta))
            }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.fuori_lascia)) }
        },
    )
}

/**
 * Un campo di testo e via: corregge una nota o la didascalia di una foto.
 *
 * Sta a parte da [NotaDialog] e [DidascaliaDialog] perche' correggere non e'
 * registrare: il titolo dice "correggi", il campo arriva pieno, e non c'e' un
 * pulsante "scarta" — la foto esiste gia'.
 */
@Composable
fun TestoDialog(
    titolo: Int,
    etichetta: Int,
    iniziale: String,
    facoltativo: Boolean,
    onSalva: (String) -> Unit,
    onChiudi: () -> Unit,
) {
    var testo by remember { mutableStateOf(iniziale) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(titolo)) },
        text = {
            OutlinedTextField(
                value = testo,
                onValueChange = { testo = it },
                label = { Text(stringResource(etichetta)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = facoltativo || testo.isNotBlank(),
                onClick = { onChiudi(); onSalva(testo) },
            ) { Text(stringResource(R.string.azione_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_annulla)) }
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
fun DidascaliaDialog(file: File?, onSalva: (String?) -> Unit, onScarta: () -> Unit) {
    var testo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onSalva(null) },
        title = { Text(stringResource(R.string.foto_titolo)) },
        text = {
            Column {
                // Lo scatto si guarda prima di tenerlo: e' la stessa domanda di
                // sempre davanti a una fotocamera, "e' venuta?", e finora questa
                // schermata non ci rispondeva.
                if (file != null) {
                    Miniatura(
                        file = file,
                        lato = 120,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
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
 * Tre modi di dire dove, in ordine di comodita': **cercare un indirizzo**,
 * prendere il GPS, incollare le coordinate. Il primo e' quello che si usa
 * pianificando a casa, gli altri due in viaggio.
 *
 * **Le coordinate stanno in un campo solo.** Si incollano da una mappa, da un
 * messaggio, da un annuncio, e si incollano insieme: spezzarle a mano per
 * infilarle in due caselle era lavoro inutile. Sotto il campo compare quello
 * che l'app ha capito, che e' l'unico modo di accorgersi di un malinteso.
 */
@Composable
fun AggiungiTappaDialog(
    tappe: List<Tappa>,
    onPrendiPosizione: () -> Unit,
    coordinatePronte: Coordinate?,
    onCerca: suspend (String) -> RicercaIndirizzo?,
    onSalva: (nome: String, lat: Double, lon: Double, giorno: String?, primaDi: String?) -> Unit,
    onChiudi: () -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var giorno by remember { mutableStateOf("") }
    var coordinate by remember { mutableStateOf("") }
    var primaDi by remember { mutableStateOf<Tappa?>(null) }
    var menuAperto by remember { mutableStateOf(false) }

    var cercato by remember { mutableStateOf("") }
    var trovati by remember { mutableStateOf<RicercaIndirizzo?>(null) }
    var ricercaInCorso by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    // Quando il GPS risponde riempie le coordinate senza toccare il resto: il
    // nome che l'utente stava scrivendo non deve sparire.
    LaunchedEffect(coordinatePronte) {
        coordinatePronte?.let { coordinate = it.toString() }
    }

    val punto = Coordinate.leggi(coordinate)
    val valida = nome.isNotBlank() && punto != null

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // --- cercare un indirizzo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = cercato,
                        onValueChange = { cercato = it; trovati = null },
                        label = { Text(stringResource(R.string.aggiungi_cerca)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = cercato.trim().length >= 2 && !ricercaInCorso,
                        onClick = {
                            ambito.launch {
                                ricercaInCorso = true
                                trovati = onCerca(cercato)
                                ricercaInCorso = false
                            }
                        },
                    ) { Text(stringResource(R.string.aggiungi_cerca_azione)) }
                }

                if (ricercaInCorso) {
                    Text(
                        stringResource(R.string.aggiungi_cerca_in_corso),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                trovati?.let { ricerca ->
                    if (ricerca.risultati.isEmpty()) {
                        Text(
                            stringResource(R.string.aggiungi_cerca_niente),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (ricerca.provenienza == Provenienza.SCORTA) {
                                    R.string.aggiungi_cerca_dalla_scorta
                                } else {
                                    R.string.aggiungi_cerca_dalla_rete
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ricerca.risultati.forEach { indirizzo ->
                            RigaIndirizzo(indirizzo) {
                                coordinate = indirizzo.coordinate.toString()
                                // Il nome si riempie solo se e' vuoto: chi l'ha
                                // gia' scritto ha deciso come chiamare la tappa.
                                if (nome.isBlank()) nome = indirizzo.nome
                                trovati = null
                            }
                        }
                    }
                }

                // --- le coordinate
                OutlinedTextField(
                    value = coordinate,
                    onValueChange = { coordinate = it },
                    label = { Text(stringResource(R.string.aggiungi_coordinate)) },
                    placeholder = { Text(stringResource(R.string.aggiungi_coordinate_esempio)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = when {
                        coordinate.isBlank() -> stringResource(R.string.aggiungi_coordinate_spiegazione)
                        punto == null -> stringResource(R.string.aggiungi_coordinate_illeggibili)
                        else -> stringResource(
                            R.string.aggiungi_coordinate_lette,
                            Csv.numero(punto.lat, 5),
                            Csv.numero(punto.lon, 5),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (coordinate.isNotBlank() && punto == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = onPrendiPosizione) {
                    Text(stringResource(R.string.aggiungi_da_gps))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                        punto?.lat ?: 0.0,
                        punto?.lon ?: 0.0,
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

/** Un risultato della ricerca: nome sopra, dove sta sotto. */
@Composable
private fun RigaIndirizzo(indirizzo: Indirizzo, onTocco: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            .padding(vertical = 6.dp),
    ) {
        Text(indirizzo.nome, style = MaterialTheme.typography.bodyMedium)
        indirizzo.descrizione?.let { dove ->
            Text(
                text = dove,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/**
 * Un rifornimento: contachilometri, importo, prezzo al litro, pieno si'/no.
 *
 * **I litri non si digitano, si calcolano.** Alla colonnina si legge quanto si
 * e' speso e il prezzo sul cartello; il volume non c'e' scritto da nessuna
 * parte, e chiederlo vorrebbe dire far fare una divisione a mano a chi ha la
 * pompa in una mano e il telefono nell'altra. I litri compaiono sotto i campi
 * mentre si scrive, cosi' una cifra sbagliata si vede subito.
 *
 * Il chilometraggio arriva precompilato con l'ultimo registrato, che di solito
 * va solo corretto nelle ultime cifre. La data e' quella di oggi e si puo'
 * cambiare: uno scontrino si ritrova in tasca due giorni dopo.
 */
@Composable
fun RifornimentoDialog(
    ultimoKm: Int?,
    adesso: OffsetDateTime,
    /** I valori da correggere, o `null` per un rifornimento nuovo. */
    iniziale: Rifornimento? = null,
    onSalva: (
        km: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
    ) -> Unit,
    onChiudi: () -> Unit,
) {
    // Correggendo, il chilometraggio proposto e' **quello della riga**, non
    // l'ultimo registrato: l'ultimo potrebbe essere proprio quello sbagliato che
    // si sta venendo a correggere.
    var km by remember {
        mutableStateOf((iniziale?.km ?: ultimoKm)?.toString().orEmpty())
    }
    var euro by remember { mutableStateOf(iniziale?.euro?.let { Csv.numero(it) }.orEmpty()) }
    var prezzo by remember {
        mutableStateOf(iniziale?.prezzoLitro?.let { Csv.numero(it, 3) }.orEmpty())
    }
    var pieno by remember { mutableStateOf(iniziale?.pieno ?: true) }
    var data by remember { mutableStateOf(Momento.scriviData(iniziale?.istante ?: adesso)) }
    var ora by remember { mutableStateOf(Momento.scriviOra(iniziale?.istante ?: adesso)) }

    val chilometri = Csv.leggiIntero(km)
    val importo = Csv.leggiNumero(euro)
    val alLitro = Csv.leggiNumero(prezzo)
    val litri = Carburante.litri(importo, alLitro)
    val istante = Momento.leggi(data, ora, adesso)

    val valida = chilometri != null && chilometri > 0 &&
        litri != null &&
        alLitro != null && alLitro <= Carburante.PREZZO_MASSIMO &&
        istante != null && !Momento.oltreOggi(istante, adesso)

    AlertDialog(
        onDismissRequest = onChiudi,
        title = {
            Text(
                stringResource(
                    if (iniziale == null) R.string.rifornimento_titolo
                    else R.string.rifornimento_correggi,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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
                        value = euro,
                        onValueChange = { euro = it },
                        label = { Text(stringResource(R.string.rifornimento_euro)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = prezzo,
                        onValueChange = { prezzo = it },
                        label = { Text(stringResource(R.string.rifornimento_prezzo)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (litri == null) {
                        stringResource(R.string.rifornimento_litri_spiegazione)
                    } else {
                        stringResource(R.string.rifornimento_litri_calcolati, Csv.numero(litri, 2))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (litri == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )

                CampiQuando(data, ora, istante, adesso, { data = it }, { ora = it })

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
                    onSalva(
                        chilometri ?: 0,
                        importo ?: 0.0,
                        alLitro ?: 0.0,
                        pieno,
                        istante ?: adesso,
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
 * Data e ora di un fatto, precompilate con adesso.
 *
 * Sono due campi e non un selettore di calendario: si tocca la data, si cambia
 * un numero, si va avanti. Un `DatePicker` per correggere "6" in "5" sarebbe
 * tre tocchi in piu' per lo stesso risultato.
 *
 * Sotto compare **la data come l'app l'ha capita**, che e' l'unico modo di
 * accorgersi che "5/8" e' stato letto come si voleva.
 */
@Composable
private fun CampiQuando(
    data: String,
    ora: String,
    istante: OffsetDateTime?,
    adesso: OffsetDateTime,
    onData: (String) -> Unit,
    onOra: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = data,
            onValueChange = onData,
            label = { Text(stringResource(R.string.quando_data)) },
            singleLine = true,
            modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            value = ora,
            onValueChange = onOra,
            label = { Text(stringResource(R.string.quando_ora)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = when {
            istante == null -> stringResource(R.string.quando_illeggibile)
            Momento.oltreOggi(istante, adesso) -> stringResource(R.string.quando_nel_futuro)
            istante.toLocalDate() == adesso.toLocalDate() -> stringResource(R.string.quando_oggi)
            else -> stringResource(R.string.quando_letta, istante.format(LETTA))
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (istante == null || Momento.oltreOggi(istante, adesso)) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private val LETTA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN)

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
    scortaDisponibile: Boolean,
    cartella: String?,
    cartellaAccessibile: Boolean,
    /** Quando la cartella e' stata sincronizzata, e quando la scorta e' stata presa. */
    /** Come si chiama questa build: versione, numero, e commit. */
    versione: String,
    sincronizzatoIl: OffsetDateTime?,
    meteoIl: OffsetDateTime?,
    dintorniIl: OffsetDateTime?,
    onScegliCartella: () -> Unit,
    onEsporta: () -> Unit,
    onSincronizza: () -> Unit,
    onSpegniCartella: () -> Unit,
    onSalva: (Impostazioni) -> Unit,
    onProvaBriefing: () -> Unit,
    onAggiornaScorta: () -> Unit,
    onScaricaDintorni: () -> Unit,
    onPermessoNotifiche: () -> Unit,
    onBatteria: () -> Unit,
    onAvvioAutomatico: () -> Unit,
    onModelli: () -> Unit,
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
                    stringResource(R.string.impostazioni_cartella),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = when {
                        cartella == null -> stringResource(R.string.impostazioni_cartella_nessuna)
                        !cartellaAccessibile -> stringResource(R.string.impostazioni_cartella_persa)
                        else -> stringResource(R.string.impostazioni_cartella_scelta, cartella)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cartella != null && !cartellaAccessibile) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = { onChiudi(); onScegliCartella() }) {
                    Text(
                        stringResource(
                            if (cartella == null) R.string.impostazioni_scegli_cartella
                            else R.string.impostazioni_cambia_cartella,
                        ),
                    )
                }
                if (cartella != null) {
                    // Sincronizzare legge e poi scrive; esportare scrive e
                    // basta. Sono due pulsanti perche' sono due operazioni: la
                    // prima serve quando nella cartella c'e' qualcosa che
                    // all'app manca — dopo una reinstallazione, o venendo da un
                    // altro telefono — la seconda quando si vuole solo essere
                    // certi che fuori ci sia tutto.
                    // La data risponde alla domanda che uno si fa cambiando
                    // telefono: "ha davvero preso tutto?". Senza, si puo' solo
                    // sperare.
                    Quando(R.string.impostazioni_sincronizzato, sincronizzatoIl)
                    Text(
                        stringResource(R.string.impostazioni_sincronizza_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(
                            enabled = cartellaAccessibile,
                            onClick = { onChiudi(); onSincronizza() },
                        ) { Text(stringResource(R.string.impostazioni_sincronizza)) }
                        TextButton(
                            enabled = cartellaAccessibile,
                            onClick = { onChiudi(); onEsporta() },
                        ) { Text(stringResource(R.string.impostazioni_esporta)) }
                        TextButton(onClick = { onChiudi(); onSpegniCartella() }) {
                            Text(stringResource(R.string.impostazioni_spegni_cartella))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringResource(R.string.impostazioni_scorta),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.impostazioni_scorta_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onChiudi(); onAggiornaScorta() }, enabled = scortaDisponibile) {
                    Text(stringResource(R.string.impostazioni_aggiorna_scorta))
                }
                // I dintorni hanno un pulsante a parte, e non per simmetria: e'
                // la richiesta piu' pesante che l'app fa, e il server di
                // OpenStreetMap e' una cortesia. Rifarla ogni volta che si
                // aggiorna il meteo sarebbe strapazzarlo per niente — i punti di
                // interesse non cambiano di sera in sera.
                // L'eta' di una scorta e' meta' del suo valore: un meteo di
                // quattro giorni e dei dintorni di un viaggio fa non sono la
                // stessa cosa di quelli presi stamattina.
                Quando(R.string.impostazioni_meteo_preso, meteoIl)
                Quando(R.string.impostazioni_dintorni_presi, dintorniIl)
                TextButton(onClick = { onChiudi(); onScaricaDintorni() }, enabled = scortaDisponibile) {
                    Text(stringResource(R.string.impostazioni_scarica_dintorni))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringResource(R.string.impostazioni_modelli),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.impostazioni_modelli_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onChiudi(); onModelli() }) {
                    Text(stringResource(R.string.impostazioni_apri_modelli))
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // In fondo, e non in cima: non e' una cosa che si cerca, e' una
                // cosa che serve quando qualcosa non va e bisogna dire quale
                // build si sta usando.
                Text(
                    text = versione,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    adesso: OffsetDateTime,
    /** La spesa da correggere, o `null` per una spesa nuova. */
    iniziale: Spesa? = null,
    /** Nullo correggendo: lo scontrino si fotografa solo registrando. */
    onScontrino: (() -> Unit)?,
    onSalva: (
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String?,
        valuta: String,
        cambio: Double?,
        istante: OffsetDateTime,
    ) -> Unit,
    onChiudi: () -> Unit,
) {
    var categoria by remember { mutableStateOf(iniziale?.categoria ?: Categoria.SOSTA) }
    var modalita by remember { mutableStateOf(iniziale?.modalita ?: Modalita.CONTANTI) }
    var importo by remember { mutableStateOf(iniziale?.let { Csv.numero(it.importo) }.orEmpty()) }
    var descrizione by remember { mutableStateOf(iniziale?.descrizione.orEmpty()) }
    var valuta by remember { mutableStateOf(iniziale?.valuta ?: valutaSuggerita) }
    var cambio by remember {
        mutableStateOf((iniziale?.cambio ?: cambioSuggerito)?.let { Csv.numero(it, 4) }.orEmpty())
    }
    // Correggendo, "estera" viene dalla spesa e non dall'ultima usata nel
    // viaggio: si sta guardando questa, non le altre.
    var estera by remember {
        mutableStateOf(iniziale?.estera ?: !valutaSuggerita.equals(Spesa.EURO, true))
    }
    var data by remember { mutableStateOf(Momento.scriviData(iniziale?.istante ?: adesso)) }
    var ora by remember { mutableStateOf(Momento.scriviOra(iniziale?.istante ?: adesso)) }

    val quanto = Csv.leggiNumero(importo)
    val tasso = Csv.leggiNumero(cambio)
    val sigla = valuta.trim().uppercase().ifEmpty { Spesa.EURO }
    val esteraDavvero = estera && sigla != Spesa.EURO

    val istante = Momento.leggi(data, ora, adesso)

    val valida = quanto != null && quanto > 0 &&
        (!esteraDavvero || (tasso != null && tasso > 0)) &&
        istante != null && !Momento.oltreOggi(istante, adesso)

    val inEuro = if (esteraDavvero && quanto != null && tasso != null) quanto * tasso else null

    AlertDialog(
        onDismissRequest = onChiudi,
        title = {
            Text(
                stringResource(
                    if (iniziale == null) R.string.spesa_titolo else R.string.spesa_correggi,
                ),
            )
        },
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

                CampiQuando(data, ora, istante, adesso, { data = it }, { ora = it })

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

                // Lo scontrino si fotografa solo registrando: correggendo, un
                // pulsante che non fa niente e' peggio di nessun pulsante, e
                // quello allegato resta comunque attaccato alla spesa.
                if (onScontrino != null) {
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
                        // La miniatura risponde alla domanda che uno si fa dopo
                        // aver fotografato uno scontrino al buio in un'area di
                        // sosta: e' venuta leggibile?
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Miniatura(file = scontrino, lato = 56)
                            Text(
                                scontrino.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
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
                    // Non chiama `onChiudi`: chiudere scarta la foto dello
                    // scontrino, e qui la foto serve. Chi salva chiude da se'.
                    onSalva(
                        categoria,
                        quanto ?: 0.0,
                        modalita,
                        descrizione.trim().takeIf { it.isNotBlank() },
                        if (esteraDavvero) sigla else Spesa.EURO,
                        if (esteraDavvero) tasso else null,
                        istante ?: adesso,
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

/**
 * Una risposta salvata, come sta su file.
 *
 * Si mostra il Markdown grezzo e non reso: sarebbe una libreria in piu' per
 * fare il grassetto, e quel testo lo si legge una volta per decidere dove
 * dormire. La stessa cosa vale per il diario, che si legge dalle tabelle.
 */
@Composable
fun DossierDialog(titolo: String, testo: String?, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo, maxLines = 2) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = testo ?: stringResource(R.string.dossier_perso),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onChiudi) { Text(stringResource(R.string.azione_chiudi)) }
        },
    )
}

/**
 * I due modelli: chiavi, identificativi, e il prompt di Esplora.
 *
 * **Le chiavi si mostrano per le ultime quattro cifre.** Mostrarle intere in una
 * schermata di impostazioni e' un invito a fotografarle per sbaglio insieme al
 * resto; queste quattro bastano a sapere se c'e' quella giusta.
 *
 * **L'identificativo del modello si puo' correggere.** I nomi dei modelli vengono
 * ritirati ogni pochi mesi: se fosse compilato dentro, un ritiro renderebbe
 * l'app muta finche' non se ne pubblica una nuova.
 */
@Composable
fun ModelliDialog(
    impostazioni: Impostazioni,
    chiaviDisponibili: Boolean,
    coda: (Modello) -> String?,
    onChiave: (Modello, String?) -> Unit,
    onSalva: (Impostazioni) -> Unit,
    onChiudi: () -> Unit,
) {
    var principale by remember { mutableStateOf(impostazioni.modelloPrincipale) }
    var nomeGemini by remember { mutableStateOf(impostazioni.modelloGemini) }
    var nomeGrok by remember { mutableStateOf(impostazioni.modelloGrok) }
    var prompt by remember { mutableStateOf(impostazioni.promptEsplora) }
    var chiaveGemini by remember { mutableStateOf("") }
    var chiaveGrok by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.modelli_titolo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (!chiaviDisponibili) {
                    Text(
                        stringResource(R.string.modelli_senza_cassaforte),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    stringResource(R.string.modelli_principale),
                    style = MaterialTheme.typography.titleSmall,
                )
                SceltaPrincipale(principale) { principale = it }
                Text(
                    stringResource(R.string.modelli_principale_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                RigaModello(
                    modello = Modello.GEMINI,
                    nome = nomeGemini,
                    chiave = chiaveGemini,
                    coda = coda(Modello.GEMINI),
                    onNome = { nomeGemini = it },
                    onChiave = { chiaveGemini = it },
                    onDimentica = { onChiave(Modello.GEMINI, null); chiaveGemini = "" },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                RigaModello(
                    modello = Modello.GROK,
                    nome = nomeGrok,
                    chiave = chiaveGrok,
                    coda = coda(Modello.GROK),
                    onNome = { nomeGrok = it },
                    onChiave = { chiaveGrok = it },
                    onDimentica = { onChiave(Modello.GROK, null); chiaveGrok = "" },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringResource(R.string.modelli_prompt),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(stringResource(R.string.modelli_prompt_vuoto)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.modelli_prompt_spiegazione),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { prompt = Esplora.PROMPT_DI_RIPOSO }) {
                    Text(stringResource(R.string.modelli_prompt_copia))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onChiudi()
                    // Le chiavi vanno nella cassaforte, non nelle impostazioni:
                    // `impostazioni.json` viene rispecchiato su un cloud.
                    chiaveGemini.trim().takeUnless { it.isEmpty() }
                        ?.let { onChiave(Modello.GEMINI, it) }
                    chiaveGrok.trim().takeUnless { it.isEmpty() }
                        ?.let { onChiave(Modello.GROK, it) }
                    onSalva(
                        impostazioni.copy(
                            principale = principale.codice,
                            modelloGemini = nomeGemini.trim(),
                            modelloGrok = nomeGrok.trim(),
                            promptEsplora = prompt.trim(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceltaPrincipale(scelta: Modello, onScelta: (Modello) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Modello.entries.forEachIndexed { indice, voce ->
            SegmentedButton(
                selected = voce == scelta,
                onClick = { onScelta(voce) },
                shape = SegmentedButtonDefaults.itemShape(indice, Modello.entries.size),
            ) { Text(voce.nome) }
        }
    }
}

@Composable
private fun RigaModello(
    modello: Modello,
    nome: String,
    chiave: String,
    coda: String?,
    onNome: (String) -> Unit,
    onChiave: (String) -> Unit,
    onDimentica: () -> Unit,
) {
    Text(modello.nome, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = nome,
        onValueChange = onNome,
        label = { Text(stringResource(R.string.modelli_identificativo)) },
        placeholder = { Text(modello.modelloDiRiposo) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = chiave,
        onValueChange = onChiave,
        label = { Text(stringResource(R.string.modelli_chiave)) },
        placeholder = {
            Text(
                if (coda == null) stringResource(R.string.modelli_chiave_assente)
                else stringResource(R.string.modelli_chiave_presente, coda),
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (coda != null) {
        TextButton(onClick = onDimentica) {
            Text(stringResource(R.string.modelli_dimentica))
        }
    }
}

/**
 * "Meteo: preso 3 ore fa" — oppure "mai".
 *
 * Sempre e' meglio del silenzio: una riga che dice "mai" spiega perche' una
 * schermata e' vuota, e l'assenza di quella riga no.
 */
@Composable
private fun Quando(etichetta: Int, istante: OffsetDateTime?) {
    Text(
        text = stringResource(
            etichetta,
            istante?.format(LETTA) ?: stringResource(R.string.impostazioni_mai),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
