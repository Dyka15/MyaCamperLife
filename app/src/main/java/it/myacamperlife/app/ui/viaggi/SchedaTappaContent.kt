package it.myacamperlife.app.ui.viaggi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.CampiExtra
import it.myacamperlife.app.dominio.Dossier
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.RiassuntoCategoria
import it.myacamperlife.app.dominio.SchedaTappa
import it.myacamperlife.app.dominio.Schede
import it.myacamperlife.app.dominio.SezioneGiorno
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.TestoMeteo
import it.myacamperlife.app.dominio.TestoTappa
import it.myacamperlife.app.dominio.Tratte
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * La schermata di una tappa: cosa dice l'itinerario, che tempo fara', cosa c'e'
 * intorno.
 *
 * **Mette insieme dati che l'app aveva gia'**, sparsi in tre schermate: la
 * descrizione stava in una riga troncata dell'elenco, la previsione solo nel
 * riepilogo delle 19:00 e solo per domani, i dintorni solo intorno a dove sei.
 * Nessuno dei tre rispondeva alla domanda che si fa davvero — *com'e' questa
 * tappa* — e la risposta non richiede rete.
 *
 * **Si scorre di lato per passare alla tappa dopo.** Un itinerario si legge in
 * fila: tornare all'elenco per aprire la tappa successiva significherebbe due
 * tocchi per fare quello che un dito fa da solo. L'ordine e' quello
 * dell'itinerario, comprese le tappe fatte e quelle saltate: sono parte del
 * viaggio e nasconderle renderebbe lo scorrimento incoerente con l'elenco.
 */
@Composable
fun SchedaTappaContent(
    tappe: List<Tappa>,
    inizialeId: String,
    poi: List<Poi>,
    tratte: Tratte,
    meteo: Meteo?,
    dossier: List<Dossier>,
    programma: List<SezioneGiorno>,
    aiConfigurata: Boolean,
    inCorso: Boolean,
    onCheckin: (Tappa) -> Unit,
    onAlterna: (Tappa) -> Unit,
    onMappa: (Tappa) -> Unit,
    onChiedi: (Tappa) -> Unit,
    onDossier: (Dossier) -> Unit,
    onScarica: () -> Unit,
    onTappaCambiata: (Tappa) -> Unit,
) {
    if (tappe.isEmpty()) return

    val partenza = tappe.indexOfFirst { it.id == inizialeId }.coerceAtLeast(0)
    val stato = rememberPagerState(initialPage = partenza, pageCount = { tappe.size })

    // L'orologio si legge **una volta**, non a ogni ricomposizione: "meteo di
    // tre ore fa" che diventa "di quattro ore fa" mentre stai guardando la
    // schermata sarebbe esatto e inquietante. E' la stessa ragione per cui i
    // dialoghi congelano l'adesso all'apertura.
    val oggi = remember { LocalDate.now() }
    val adesso = remember { OffsetDateTime.now() }

    // Chi ha in mano lo scorrimento e' il pager; il chiamante deve sapere su
    // quale tappa siamo finiti, altrimenti il titolo in cima resterebbe quello
    // della tappa da cui si e' partiti.
    LaunchedEffect(stato.currentPage, tappe) {
        tappe.getOrNull(stato.currentPage)?.let(onTappaCambiata)
    }

    HorizontalPager(state = stato, modifier = Modifier.fillMaxSize()) { pagina ->
        val tappa = tappe[pagina]

        // Comporre una scheda costa qualche migliaio di distanze: si rifa'
        // quando cambiano i dati, non quando cambia una barra di attesa.
        val composta = remember(tappa, tappe, poi, tratte, meteo, dossier, programma) {
            Schede.componi(
                tappa = tappa,
                tappe = tappe,
                oggi = oggi,
                poi = poi,
                tratte = tratte,
                meteo = meteo,
                adesso = adesso,
                dossier = dossier,
                programma = programma,
            )
        }

        Scheda(
            scheda = composta,
            quante = tappe.size,
            aiConfigurata = aiConfigurata,
            inCorso = inCorso,
            onCheckin = { onCheckin(tappa) },
            onAlterna = { onAlterna(tappa) },
            onMappa = { onMappa(tappa) },
            onChiedi = { onChiedi(tappa) },
            onDossier = onDossier,
            onScarica = onScarica,
        )
    }
}

@Composable
private fun Scheda(
    scheda: SchedaTappa,
    quante: Int,
    aiConfigurata: Boolean,
    inCorso: Boolean,
    onCheckin: () -> Unit,
    onAlterna: () -> Unit,
    onMappa: () -> Unit,
    onChiedi: () -> Unit,
    onDossier: (Dossier) -> Unit,
    onScarica: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item { Testata(scheda, quante) }
        item { Azioni(scheda, onCheckin, onAlterna, onMappa) }

        // Tutto quello che il file diceva di questa tappa: la descrizione con i
        // suoi capi, e i campi che il lettore non riconosce — orari, telefono,
        // quota, link. Prima finivano nel nulla senza che nessuno lo dicesse.
        if (scheda.descrizione != null || scheda.tappa.altro.isNotEmpty()) {
            item {
                Sezione(R.string.scheda_descrizione) {
                    scheda.descrizione?.let { Corpo(it) }
                    if (scheda.tappa.altro.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(
                                top = if (scheda.descrizione == null) 0.dp else 8.dp,
                            ),
                        ) {
                            scheda.tappa.altro.forEach { campo ->
                                Text(
                                    text = CampiExtra.riga(campo),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // **Il programma della giornata, per intero.** E' la parte piu' lunga
        // della scheda e sta subito dopo la descrizione, perche' e' quella che si
        // legge davvero: orari, durate, cosa vedere e perche'. Non si tronca e non
        // si riassume — se l'itinerario dice ottocento parole sul 10 agosto a
        // Monaco, sono ottocento parole che servono arrivando a Monaco.
        scheda.programma?.takeUnless { it.vuota }?.let { giornata ->
            item {
                Sezione(R.string.scheda_programma) {
                    giornata.titolo?.let { titolo ->
                        Text(
                            text = titolo,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (giornata.testo.isNotBlank()) Corpo(giornata.testo)
                    Text(
                        text = stringResource(R.string.scheda_programma_giorno, giornata.etichetta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // Il meteo di una tappa e' quello del **suo** giorno: una previsione di
        // oggi su una tappa di giovedi' sarebbe un numero sbagliato messo dove
        // sembra giusto.
        scheda.previsione?.let { previsione ->
            item {
                Sezione(R.string.scheda_meteo) {
                    Corpo(TestoMeteo.riga(previsione))
                    // L'eta' del dato non e' cortesia: e' cio' che impedisce di
                    // credere a una previsione ferma da due giorni.
                    TestoMeteo.eta(scheda.meteoOreFa)?.let { Sottotitolo(it) }
                }
            }
        }

        item {
            Sezione(R.string.scheda_dintorni) {
                when {
                    scheda.dintorni.isNotEmpty() ->
                        scheda.dintorni.forEach { RigaDintorno(it) }

                    // "Qui non c'e' niente" e "non lo so" sono due cose
                    // diverse, e si dicono diversamente: la seconda ha un
                    // rimedio, la prima e' una risposta.
                    scheda.dintorniVuoti -> Sottotitolo(stringResource(R.string.scheda_dintorni_vuoti))

                    else -> {
                        Sottotitolo(stringResource(R.string.scheda_dintorni_senza_scorta))
                        TextButton(onClick = onScarica) {
                            Text(stringResource(R.string.esplora_scarica))
                        }
                    }
                }
            }
        }

        item {
            Chiedi(
                aiConfigurata = aiConfigurata,
                inCorso = inCorso,
                onChiedi = onChiedi,
            )
        }

        if (scheda.dossier.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.scheda_risposte),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
            items(scheda.dossier, key = { it.id }) { salvato ->
                RigaSalvata(salvato, onTocco = { onDossier(salvato) })
            }
        }
    }
}

/**
 * Nome, giorno, e da dove ci si arriva.
 *
 * Il giorno si dice due volte, "fra 3 giorni" e la data: il primo si capisce
 * senza contare, la seconda e' quella che si confronta con l'itinerario.
 */
@Composable
private fun Testata(scheda: SchedaTappa, quante: Int) {
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
                text = scheda.tappa.nome,
                style = MaterialTheme.typography.titleLarge,
                textDecoration = if (scheda.tappa.stato == StatoTappa.SALTATA) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
            )

            val quando = listOfNotNull(
                TestoTappa.quando(scheda.fraGiorni),
                scheda.giorno?.let { TestoTappa.data(it) },
                // Senza data si mostra il campo com'e' scritto: "giorno 4" non
                // e' una data, ma e' quello che dice l'itinerario, e tacerlo
                // farebbe sembrare la tappa priva di programma.
                if (scheda.giorno == null) scheda.tappa.giorno?.trim()?.takeUnless { it.isEmpty() } else null,
            ).joinToString(" · ")

            if (quando.isNotEmpty()) {
                Text(
                    text = quando,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Da dove si arriva e quanto c'e': i chilometri solo se sono quelli
            // su strada, come in testata al viaggio. La linea d'aria qui
            // sembrerebbe una distanza di guida senza esserlo.
            scheda.da?.let { da ->
                val percorso = scheda.percorso
                Text(
                    text = if (percorso != null) {
                        stringResource(
                            R.string.scheda_da_con_distanza,
                            da.nome,
                            Math.round(percorso.km).toInt(),
                            percorso.durata,
                        )
                    } else {
                        stringResource(R.string.scheda_da, da.nome)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                text = stringResource(R.string.scheda_posizione_nel_viaggio, scheda.tappa.ordine, quante),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Le stesse azioni del vecchio dialogo, piu' l'apertura nella mappa.
 *
 * In `FlowRow` e non in `Row`: «Sono arrivato», «Salta» e «Apri nella mappa»
 * stanno in fila su uno schermo largo e a carattere normale, ma non su uno
 * stretto o a carattere ingrandito — e una `Row` non lo dice, schiaccia
 * l'ultimo figlio finche' il testo va a capo una lettera per riga. Il flusso
 * manda a capo il pulsante intero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Azioni(
    scheda: SchedaTappa,
    onCheckin: () -> Unit,
    onAlterna: () -> Unit,
    onMappa: () -> Unit,
) {
    Column {
        Text(
            text = when (scheda.tappa.stato) {
                StatoTappa.DA_FARE -> stringResource(R.string.tappa_da_fare)
                StatoTappa.FATTA -> stringResource(R.string.tappa_fatta)
                StatoTappa.SALTATA -> stringResource(R.string.tappa_saltata)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            if (scheda.tappa.stato == StatoTappa.DA_FARE) {
                TextButton(onClick = onCheckin) {
                    Text(stringResource(R.string.azione_checkin))
                }
            }
            // Su una tappa dove sei stato, saltare non vuol dire niente.
            if (scheda.tappa.stato != StatoTappa.FATTA) {
                TextButton(onClick = onAlterna) {
                    Text(
                        if (scheda.tappa.stato == StatoTappa.SALTATA) {
                            stringResource(R.string.azione_ripristina)
                        } else {
                            stringResource(R.string.azione_salta)
                        },
                    )
                }
            }
            TextButton(onClick = onMappa) {
                Text(stringResource(R.string.scheda_apri_mappa))
            }
        }
        HorizontalDivider()
    }
}

/**
 * Il pulsante che chiede al modello dei dintorni di **questa** tappa.
 *
 * Un tocco e nessun campo: la domanda e' sempre la stessa, e comporla ogni
 * volta sarebbe lavoro per niente. Il campo libero resta in Esplora, dove serve
 * a chiedere qualcosa di diverso.
 */
@Composable
private fun Chiedi(aiConfigurata: Boolean, inCorso: Boolean, onChiedi: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onChiedi, enabled = aiConfigurata && !inCorso) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab_esplora),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.scheda_chiedi),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (inCorso) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Text(
            text = if (aiConfigurata) {
                stringResource(R.string.scheda_chiedi_spiegazione)
            } else {
                stringResource(R.string.esplora_ai_da_configurare)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Aree di sosta · 3 — la piu' vicina: Area Lido, 1,2 km". */
@Composable
private fun RigaDintorno(riassunto: RiassuntoCategoria) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (riassunto.quanti > 1) {
                    stringResource(
                        R.string.scheda_categoria_quante,
                        riassunto.categoria.nome,
                        riassunto.quanti,
                    )
                } else {
                    riassunto.categoria.nome
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = riassunto.piuVicino.poi.etichetta(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = riassunto.piuVicino.distanza,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun RigaSalvata(dossier: Dossier, onTocco: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTocco)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dossier.titolo(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = listOfNotNull(dossier.istante.format(QUANDO), dossier.modello)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun Sezione(titolo: Int, contenuto: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(titolo),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        contenuto()
    }
}

@Composable
private fun Corpo(testo: String) {
    Text(testo, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Sottotitolo(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val QUANDO: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.ITALIAN)
