package it.myacamperlife.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.myacamperlife.app.R
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.avvisi.Avvisi
import it.myacamperlife.app.avvisi.Sistema
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.PoiVicino
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.ui.diario.DiarioContent
import it.myacamperlife.app.ui.esplora.EsploraContent
import it.myacamperlife.app.ui.numeri.NumeriContent
import it.myacamperlife.app.ui.viaggi.AggiungiTappaDialog
import it.myacamperlife.app.ui.viaggi.AzioniTappaDialog
import it.myacamperlife.app.ui.viaggi.BriefingDialog
import it.myacamperlife.app.ui.viaggi.DidascaliaDialog
import it.myacamperlife.app.ui.viaggi.ElencoViaggiContent
import it.myacamperlife.app.ui.viaggi.ImpostazioniDialog
import it.myacamperlife.app.ui.viaggi.NotaDialog
import it.myacamperlife.app.ui.viaggi.RifornimentoDialog
import it.myacamperlife.app.ui.viaggi.SpesaDialog
import it.myacamperlife.app.ui.viaggi.TappeContent
import it.myacamperlife.app.ui.viaggi.ViaggiViewModel
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.launch

private enum class Scheda(val etichetta: Int, val icona: Int) {
    VIAGGIO(R.string.scheda_viaggio, R.drawable.ic_tab_viaggio),
    DIARIO(R.string.scheda_diario, R.drawable.ic_tab_diario),
    NUMERI(R.string.scheda_numeri, R.drawable.ic_tab_numeri),
    ESPLORA(R.string.scheda_esplora, R.drawable.ic_tab_esplora),
}

/**
 * Il contenitore dell'app.
 *
 * Barra superiore, schede e pulsanti stanno qui, non nelle singole schermate:
 * le viste sono cosi' semplici contenuti, e le spaziature di sistema restano
 * coerenti passando dall'una all'altra.
 *
 * Le schede compaiono solo dentro un viaggio: fuori non ci sarebbe niente da
 * separare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyaApp(vista: ViaggiViewModel) {
    val stato by vista.stato.collectAsStateWithLifecycle()
    val avvisi = remember { SnackbarHostState() }
    val contesto = LocalContext.current
    val ambito = rememberCoroutineScope()

    var scheda by rememberSaveable { mutableStateOf(Scheda.VIAGGIO) }
    var tappaScelta by remember { mutableStateOf<Tappa?>(null) }
    var notaAperta by remember { mutableStateOf(false) }
    var aggiungiAperto by remember { mutableStateOf(false) }
    var coordinateGps by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var fotoScattata by remember { mutableStateOf<File?>(null) }
    var fotoInAttesa by remember { mutableStateOf<File?>(null) }
    var rifornimentoAperto by remember { mutableStateOf(false) }
    var impostazioniAperte by remember { mutableStateOf(false) }
    var spesaAperta by remember { mutableStateOf(false) }
    var scontrinoInAttesa by remember { mutableStateOf<File?>(null) }
    var scontrino by remember { mutableStateOf<File?>(null) }
    var briefingAperto by remember { mutableStateOf(false) }
    var briefing by remember { mutableStateOf<Briefing?>(null) }

    val scegliFile = rememberLauncherForActivityResult(
        // Un itinerario e' un .md, ma i gestori file lo annunciano in mille
        // modi diversi: filtrare per tipo nasconderebbe il file da scegliere.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vista::importa) }

    val scatta = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { riuscito ->
        val file = fotoInAttesa
        fotoInAttesa = null
        if (riuscito && file != null) fotoScattata = file else file?.let(vista::scartaFoto)
    }

    // Lo scontrino ha un suo lanciatore: al ritorno non chiede una didascalia,
    // resta allegato alla spesa che si sta compilando.
    val scattaScontrino = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { riuscito ->
        val file = scontrinoInAttesa
        scontrinoInAttesa = null
        if (!riuscito || file == null) {
            file?.let(vista::scartaScontrino)
        } else {
            // Una foto gia' presa viene sostituita: non restano scarti.
            scontrino?.takeIf { it != file }?.let(vista::scartaScontrino)
            scontrino = file
        }
    }

    // Il permesso di notifica si chiede dalle impostazioni, dove c'e' la
    // funzione che lo giustifica sotto gli occhi.
    var notificheConcesse by remember { mutableStateOf(Avvisi(contesto).permessoConcesso()) }
    val chiediNotifiche = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        notificheConcesse = esiti.values.any { it }
        if (!notificheConcesse) {
            // Negato una seconda volta il sistema non richiede piu' niente:
            // l'unica strada che resta e' la schermata delle impostazioni.
            Sistema.apriNotifiche(contesto)
        }
    }

    // Il permesso si chiede quando serve, non all'avvio: prima di allora non
    // ci sarebbe una funzione da mostrare a giustificarlo.
    var dopoIlPermesso by remember { mutableStateOf<(() -> Unit)?>(null) }
    val chiediPosizione = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        val azione = dopoIlPermesso
        dopoIlPermesso = null
        if (esiti.values.any { it }) azione?.invoke() else vista.permessoPosizioneNegato()
    }

    fun conPosizione(azione: () -> Unit) {
        if (Posizioni(contesto).permessoConcesso()) {
            azione()
        } else {
            dopoIlPermesso = azione
            chiediPosizione.launch(Posizioni.PERMESSI)
        }
    }

    val avviso = stato.avviso
    val testoAvviso = avviso?.let { messaggio(it) }
    LaunchedEffect(avviso) {
        if (testoAvviso != null) {
            avvisi.showSnackbar(testoAvviso)
            vista.avvisoVisto()
        }
    }

    val aperto = stato.aperto
    BackHandler(enabled = aperto != null) { vista.chiudi() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(aperto?.nome ?: stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (aperto != null) {
                        IconButton(onClick = vista::chiudi) {
                            Icon(
                                painter = painterResource(R.drawable.ic_indietro),
                                contentDescription = stringResource(R.string.azione_indietro),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { impostazioniAperte = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_impostazioni),
                            contentDescription = stringResource(R.string.impostazioni_titolo),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (aperto != null) {
                NavigationBar {
                    Scheda.entries.forEach { voce ->
                        NavigationBarItem(
                            selected = scheda == voce,
                            onClick = { scheda = voce },
                            icon = {
                                Icon(
                                    painter = painterResource(voce.icona),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(voce.etichetta)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when {
                stato.caricamento -> Unit

                aperto == null -> ExtendedFloatingActionButton(
                    onClick = { scegliFile.launch(arrayOf("*/*")) },
                    text = { Text(stringResource(R.string.azione_importa)) },
                    icon = {
                        Icon(painter = painterResource(R.drawable.ic_importa), contentDescription = null)
                    },
                )

                scheda == Scheda.VIAGGIO -> ExtendedFloatingActionButton(
                    onClick = { aggiungiAperto = true },
                    text = { Text(stringResource(R.string.azione_aggiungi_tappa)) },
                    icon = {
                        Icon(painter = painterResource(R.drawable.ic_aggiungi), contentDescription = null)
                    },
                )

                else -> Unit
            }
        },
        snackbarHost = { SnackbarHost(avvisi) },
    ) { spazi ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(spazi),
        ) {
            when {
                stato.caricamento -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                aperto == null -> ElencoViaggiContent(
                    viaggi = stato.viaggi,
                    onApri = { viaggio -> scheda = Scheda.VIAGGIO; vista.apri(viaggio) },
                    onElimina = vista::elimina,
                )

                scheda == Scheda.VIAGGIO -> TappeContent(
                    tappe = stato.tappe,
                    corrente = stato.corrente,
                    prossima = stato.prossima,
                    versoProssima = stato.versoProssima,
                    onPosizione = { conPosizione { vista.registraPosizione() } },
                    onFoto = {
                        ambito.launch {
                            val file = vista.preparaFoto() ?: return@launch
                            fotoInAttesa = file
                            scatta.launch(uriDi(contesto, file))
                        }
                    },
                    onNota = { notaAperta = true },
                    onLitri = { rifornimentoAperto = true },
                    onSpesa = { spesaAperta = true },
                    onTappa = { tappaScelta = it },
                )

                scheda == Scheda.DIARIO -> DiarioContent(voci = stato.voci, giorni = stato.giorni)

                scheda == Scheda.NUMERI -> NumeriContent(
                    consumo = stato.consumo,
                    autonomia = stato.autonomia,
                    conto = stato.conto,
                    kmConUnPieno = stato.kmConUnPieno,
                    onImpostaKm = { impostazioniAperte = true },
                )

                else -> EsploraContent(
                    perCategoria = stato.perCategoria,
                    risultati = stato::vicini,
                    haScorta = stato.poi.isNotEmpty(),
                    onScarica = vista::aggiornaDintorni,
                    onApri = { vicino -> apriNellaMappa(contesto, vicino) },
                )
            }

            // Una barra sottile in cima mentre si scrive: le scritture sono
            // veloci, ma un fix satellitare puo' prendersi qualche secondo.
            if (stato.inCorso) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    tappaScelta?.let { tappa ->
        AzioniTappaDialog(
            tappa = tappa,
            onCheckin = { conPosizione { vista.checkin(tappa) } },
            onAlterna = { vista.alternaSalto(tappa) },
            onChiudi = { tappaScelta = null },
        )
    }

    if (notaAperta) {
        NotaDialog(
            onSalva = vista::registraNota,
            onChiudi = { notaAperta = false },
        )
    }

    fotoScattata?.let { file ->
        DidascaliaDialog(
            onSalva = { didascalia ->
                fotoScattata = null
                vista.registraFoto(file, didascalia)
            },
            onScarta = {
                fotoScattata = null
                vista.scartaFoto(file)
            },
        )
    }

    if (rifornimentoAperto) {
        RifornimentoDialog(
            ultimoKm = stato.ultimoKm,
            // L'adesso si prende all'apertura del dialogo e non a ogni
            // ricomposizione: se cambiasse sotto le dita, i campi precompilati
            // salterebbero mentre li stai correggendo.
            adesso = remember { OffsetDateTime.now() },
            onSalva = vista::registraRifornimento,
            onChiudi = { rifornimentoAperto = false },
        )
    }

    if (spesaAperta) {
        // La valuta e il cambio proposti sono gli ultimi usati nel viaggio: in
        // Svizzera si registrano dieci spese in franchi, non una.
        val valutaSuggerita = stato.spese.lastOrNull { it.estera }?.valuta ?: Spesa.EURO
        SpesaDialog(
            valutaSuggerita = valutaSuggerita,
            cambioSuggerito = Spese.ultimoCambio(stato.spese, valutaSuggerita),
            scontrino = scontrino,
            adesso = remember { OffsetDateTime.now() },
            onScontrino = {
                ambito.launch {
                    val file = vista.preparaScontrino() ?: return@launch
                    scontrinoInAttesa = file
                    scattaScontrino.launch(uriDi(contesto, file))
                }
            },
            onSalva = { categoria, importo, modalita, descrizione, valuta, cambio, istante ->
                vista.registraSpesa(
                    categoria = categoria,
                    importo = importo,
                    modalita = modalita,
                    descrizione = descrizione,
                    valuta = valuta,
                    cambio = cambio,
                    scontrino = scontrino,
                    istante = istante,
                )
                spesaAperta = false
                // Salvata: il file resta, e' l'allegato della spesa.
                scontrino = null
            },
            onChiudi = {
                spesaAperta = false
                // Chiudere senza salvare non deve lasciare in giro la foto di
                // uno scontrino che non appartiene a nessuna spesa.
                scontrino?.let(vista::scartaScontrino)
                scontrino = null
            },
        )
    }

    if (impostazioniAperte) {
        ImpostazioniDialog(
            impostazioni = stato.impostazioni,
            notificheConcesse = notificheConcesse,
            batteriaSenzaLimiti = Sistema.batteriaSenzaLimiti(contesto),
            avvioAutomaticoDisponibile = Sistema.avvioAutomaticoDisponibile(contesto),
            onSalva = vista::salvaImpostazioni,
            onProvaBriefing = {
                ambito.launch {
                    briefing = vista.briefingDiStasera()
                    briefingAperto = true
                }
            },
            onPermessoNotifiche = {
                if (notificheConcesse) Sistema.apriNotifiche(contesto)
                else chiediNotifiche.launch(Avvisi.PERMESSI)
            },
            onAggiornaScorta = vista::aggiornaScorta,
            scortaDisponibile = stato.aperto != null,
            onBatteria = { Sistema.apriBatteria(contesto) },
            onAvvioAutomatico = { Sistema.apriAvvioAutomatico(contesto) },
            onChiudi = { impostazioniAperte = false },
        )
    }

    if (briefingAperto) {
        BriefingDialog(
            briefing = briefing,
            onChiudi = { briefingAperto = false; briefing = null },
        )
    }

    if (aggiungiAperto) {
        AggiungiTappaDialog(
            tappe = stato.tappe,
            coordinatePronte = coordinateGps,
            onPrendiPosizione = {
                conPosizione {
                    ambito.launch {
                        val posizione = vista.posizioneAttuale()
                        coordinateGps = posizione?.let { it.lat to it.lon }
                    }
                }
            },
            onSalva = { nome, lat, lon, giorno, primaDi ->
                coordinateGps = null
                vista.aggiungiTappa(nome, lat, lon, giorno, primaDi)
            },
            onChiudi = { aggiungiAperto = false; coordinateGps = null },
        )
    }
}

/**
 * Apre un punto di interesse nell'app di mappe.
 *
 * Un intent `geo:` e non un modulo di navigazione: Organic Maps e OsmAnd fanno
 * quel lavoro meglio di quanto potremmo farlo noi, funzionano offline, e sono
 * probabilmente gia' installati. Due righe invece di un gigabyte di grafo
 * stradale.
 */
private fun apriNellaMappa(contesto: android.content.Context, vicino: PoiVicino) {
    val lat = String.format(java.util.Locale.ROOT, "%.6f", vicino.poi.lat)
    val lon = String.format(java.util.Locale.ROOT, "%.6f", vicino.poi.lon)
    val etichetta = Uri.encode(vicino.poi.etichetta())
    val intento = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("geo:$lat,$lon?q=$lat,$lon($etichetta)"),
    )
    // Se non c'e' nessuna app di mappe non si fa niente, invece di cadere.
    if (intento.resolveActivity(contesto.packageManager) != null) contesto.startActivity(intento)
}

/**
 * L'Uri con cui la fotocamera di sistema scrive nel nostro file.
 *
 * Passa dal FileProvider e non da un percorso: da Android 7 un `file://`
 * verso un'altra app fa cadere il processo.
 */
private fun uriDi(contesto: android.content.Context, file: File): Uri =
    FileProvider.getUriForFile(contesto, "${contesto.packageName}.file", file)

/**
 * I messaggi. Un errore dice **perche'**: "non ho trovato le tappe" senza
 * motivo lascia l'utente a indovinare quale file andava scelto.
 */
@Composable
private fun messaggio(avviso: ViaggiViewModel.Avviso): String = when (avviso) {
    is ViaggiViewModel.Avviso.ImportRiuscito ->
        if (avviso.scartate == 0) {
            stringResource(R.string.import_riuscito, avviso.tappe)
        } else {
            stringResource(R.string.import_riuscito_con_scarti, avviso.tappe, avviso.scartate)
        }

    is ViaggiViewModel.Avviso.ImportFallito -> when (avviso.motivo) {
        Itinerario.Motivo.NESSUN_JSON -> stringResource(R.string.import_senza_json)
        Itinerario.Motivo.NESSUN_WAYPOINTS -> stringResource(R.string.import_senza_waypoints)
        Itinerario.Motivo.NESSUNA_TAPPA -> stringResource(R.string.import_senza_coordinate)
        null -> stringResource(R.string.import_illeggibile)
    }

    ViaggiViewModel.Avviso.PosizioneAssente -> stringResource(R.string.posizione_assente)
    ViaggiViewModel.Avviso.PosizioneRegistrata -> stringResource(R.string.posizione_registrata)
    ViaggiViewModel.Avviso.PermessoPosizioneNegato -> stringResource(R.string.posizione_negata)
    is ViaggiViewModel.Avviso.TappaAggiunta -> stringResource(R.string.tappa_aggiunta, avviso.nome)
    ViaggiViewModel.Avviso.NotaRegistrata -> stringResource(R.string.nota_registrata)
    ViaggiViewModel.Avviso.FotoRegistrata -> stringResource(R.string.foto_registrata)
    ViaggiViewModel.Avviso.RifornimentoRegistrato -> stringResource(R.string.rifornimento_registrato)
    ViaggiViewModel.Avviso.SpesaRegistrata -> stringResource(R.string.spesa_registrata)
    ViaggiViewModel.Avviso.ImpostazioniSalvate -> stringResource(R.string.impostazioni_salvate)
    ViaggiViewModel.Avviso.ScortaAggiornata -> stringResource(R.string.scorta_aggiornata)
    ViaggiViewModel.Avviso.ScortaNonAggiornata -> stringResource(R.string.scorta_non_aggiornata)
    ViaggiViewModel.Avviso.DintorniAggiornati -> stringResource(R.string.dintorni_aggiornati)
}
