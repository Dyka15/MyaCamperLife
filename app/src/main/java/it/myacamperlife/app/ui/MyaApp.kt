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
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.ui.diario.DiarioContent
import it.myacamperlife.app.ui.numeri.NumeriContent
import it.myacamperlife.app.ui.viaggi.AggiungiTappaDialog
import it.myacamperlife.app.ui.viaggi.AzioniTappaDialog
import it.myacamperlife.app.ui.viaggi.DidascaliaDialog
import it.myacamperlife.app.ui.viaggi.ElencoViaggiContent
import it.myacamperlife.app.ui.viaggi.ImpostazioniDialog
import it.myacamperlife.app.ui.viaggi.NotaDialog
import it.myacamperlife.app.ui.viaggi.RifornimentoDialog
import it.myacamperlife.app.ui.viaggi.TappeContent
import it.myacamperlife.app.ui.viaggi.ViaggiViewModel
import java.io.File
import kotlinx.coroutines.launch

private enum class Scheda(val etichetta: Int, val icona: Int) {
    VIAGGIO(R.string.scheda_viaggio, R.drawable.ic_tab_viaggio),
    DIARIO(R.string.scheda_diario, R.drawable.ic_tab_diario),
    NUMERI(R.string.scheda_numeri, R.drawable.ic_tab_numeri),
}

/**
 * Il contenitore dell'app.
 *
 * Barra superiore, schede e pulsanti stanno qui, non nelle singole schermate:
 * le viste sono cosi' semplici contenuti, e le spaziature di sistema restano
 * coerenti passando dall'una all'altra.
 *
 * Le schede compaiono solo dentro un viaggio: fuori non ci sarebbe niente da
 * separare. **Esplora** si aggiunge alla fase 7, quando avra' qualcosa da
 * mostrare.
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
                    onTappa = { tappaScelta = it },
                )

                scheda == Scheda.DIARIO -> DiarioContent(voci = stato.voci, giorni = stato.giorni)

                else -> NumeriContent(
                    consumo = stato.consumo,
                    autonomia = stato.autonomia,
                    kmConUnPieno = stato.kmConUnPieno,
                    onImpostaKm = { impostazioniAperte = true },
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
            onSalva = vista::registraRifornimento,
            onChiudi = { rifornimentoAperto = false },
        )
    }

    if (impostazioniAperte) {
        ImpostazioniDialog(
            kmConUnPieno = stato.kmConUnPieno,
            onSalva = vista::salvaKmConUnPieno,
            onChiudi = { impostazioniAperte = false },
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
    ViaggiViewModel.Avviso.ImpostazioniSalvate -> stringResource(R.string.impostazioni_salvate)
}
