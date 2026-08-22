package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Fermata
import it.myacamperlife.app.dominio.Filo
import it.myacamperlife.app.dominio.GiornataFilo
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Percorso
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.TestoMeteo
import it.myacamperlife.app.dominio.Tratte
import java.time.LocalDate
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TappeContent(
    tappe: List<Tappa>,
    corrente: Tappa?,
    prossima: Tappa?,
    versoProssima: Percorso?,
    /** Le distanze su strada gia' calcolate: senza, i tratti non si mostrano. */
    tratte: Tratte? = null,
    /** La scorta di previsioni: da' il tempo di ogni giornata. */
    meteo: Meteo? = null,
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
    // **Testata e azioni non scorrono.** Prima stavano dentro l'elenco, e
    // scorrendo le tappe se ne andavano: le cinque azioni rapide sono l'intera
    // ragione per cui questa schermata batte il bot — "mandare una foto non
    // richiede comandi" — e un pulsante che si deve andare a cercare in cima
    // costa i due tocchi che l'app si e' impegnata a non chiedere. Scorre
    // l'itinerario, che e' l'unica parte che puo' essere lunga.
    Column(modifier = Modifier.fillMaxSize()) {
        Testata(corrente, prossima, versoProssima)
        AzioniRapide(onPosizione, onFoto, onNota, onLitri, onSpesa)
        HorizontalDivider()

        // L'orologio si legge una volta sola, come nella scheda di tappa: "oggi"
        // che cambia mentre guardi lo schermo sarebbe esatto e inquietante.
        val oggi = remember { LocalDate.now() }
        val adesso = remember { OffsetDateTime.now() }
        val giornate = remember(tappe, tratte, meteo, oggi) {
            Filo.componi(tappe, oggi, tratte, meteo, adesso)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            giornate.forEach { giornata ->
                // L'intestazione resta in cima mentre si scorre: su un
                // itinerario di ventiquattro tappe, senza, si perde il conto di
                // che giorno si sta guardando.
                stickyHeader(key = "giorno-${giornata.etichetta}-${giornata.fermate.first().tappa.id}") {
                    IntestazioneGiornata(giornata)
                }
                itemsIndexed(
                    giornata.fermate,
                    key = { _, fermata -> fermata.tappa.id },
                ) { indice, fermata ->
                    RigaFermata(
                        fermata = fermata,
                        prima = indice > 0,
                        ultima = indice == giornata.fermate.lastIndex,
                        onTocco = { onTappa(fermata.tappa) },
                    )
                }
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

/**
 * L'intestazione di una giornata: quando, e che tempo fa.
 *
 * Ha uno sfondo pieno e non trasparente perche' resta appiccicata in cima
 * mentre le tappe le scorrono sotto: senza, si leggerebbero due testi
 * sovrapposti.
 */
@Composable
private fun IntestazioneGiornata(giornata: GiornataFilo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = giornata.etichetta,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        // Il tempo di quel giorno, in due parole. Manca finche' la scorta non
        // copre quella data, e allora la riga semplicemente non lo dice.
        giornata.previsione?.let { previsione ->
            TestoMeteo.breve(previsione)?.let { breve ->
                Text(
                    text = breve,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Una tappa nel filo.
 *
 * **Il filo e' un segno, non una decorazione**: la linea verticale dice che le
 * tappe sono in fila e che fra l'una e l'altra si guida, e il pallino dice a che
 * punto sei. Pieno se ci sei stato, vuoto se e' da fare, spento se l'hai
 * saltata — e il barrato resta solo sul nome, cosi' ogni cosa e' detta una volta.
 *
 * **Il numero d'ordine resta.** Su un itinerario di ventiquattro tappe e' come
 * ci si tiene il segno parlandone: "la sedici" e' un nome piu' corto di
 * "Landshut".
 */
@Composable
private fun RigaFermata(
    fermata: Fermata,
    prima: Boolean,
    ultima: Boolean,
    onTocco: () -> Unit,
) {
    val tappa = fermata.tappa
    val colorePallino = when {
        fermata.corrente -> MaterialTheme.colorScheme.primary
        tappa.stato == StatoTappa.FATTA -> MaterialTheme.colorScheme.secondary
        tappa.stato == StatoTappa.SALTATA -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val pieno = fermata.corrente || tappa.stato == StatoTappa.FATTA

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            // **`IntrinsicSize.Min` non e' un vezzo**: il filo a sinistra deve
            // essere alto quanto il testo a destra, e dentro una lista che
            // scorre l'altezza disponibile e' infinita — `fillMaxHeight` non
            // avrebbe niente da riempire e il filo sparirebbe. Cosi' la riga
            // misura prima il suo contenuto, e il filo si adegua.
            .height(IntrinsicSize.Min)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Binario(
            colore = colorePallino,
            pieno = pieno,
            sopra = prima,
            sotto = !ultima,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            // Quanto si guida per arrivare qui. Sta sopra il nome perche' e'
            // quello che succede prima: si parte, si guida, si arriva.
            fermata.arrivoDa?.let { percorso ->
                Text(
                    text = stringResource(
                        R.string.filo_tratto,
                        Math.round(percorso.km).toInt(),
                        percorso.durata,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = tappa.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tappa.stato == StatoTappa.SALTATA) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration =
                        if (tappa.stato == StatoTappa.SALTATA) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tappa.ordine.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Il tipo e l'ora d'arrivo. Le coordinate non ci sono piu': non le
            // legge nessuno, e stanno nella scheda della tappa insieme al
            // pulsante che apre la mappa, che e' cosa se ne fa davvero.
            val sotto = listOfNotNull(
                tappa.tipo?.replace('_', ' '),
                oraDiArrivo(tappa.checkinIl)?.let { stringResource(R.string.filo_arrivato, it) },
                stringResource(R.string.tappa_saltata_breve).takeIf {
                    tappa.stato == StatoTappa.SALTATA
                },
            ).joinToString(" · ")
            if (sotto.isNotEmpty()) {
                Text(
                    text = sotto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            tappa.descrizione?.let { descrizione ->
                Text(
                    text = descrizione,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Il filo e il suo pallino, nella colonna di sinistra. */
@Composable
private fun Binario(colore: Color, pieno: Boolean, sopra: Boolean, sotto: Boolean) {
    Column(
        modifier = Modifier
            .width(40.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spazio(sopra, altezza = 14.dp)
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (pieno) colore else MaterialTheme.colorScheme.surface)
                .border(1.5.dp, colore, CircleShape),
        )
        if (sotto) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Il pezzo di filo sopra il pallino: c'e' solo se sopra c'e' una tappa. */
@Composable
private fun Spazio(disegnato: Boolean, altezza: Dp) {
    if (disegnato) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(altezza)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Spacer(modifier = Modifier.height(altezza))
    }
}

/** L'ora del check-in, o `null` se il campo e' assente o illeggibile. */
private fun oraDiArrivo(iso: String?): String? = iso?.let {
    runCatching { OffsetDateTime.parse(it).format(ORA) }.getOrNull()
}

private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
