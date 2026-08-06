package it.myacamperlife.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.ui.viaggi.ElencoViaggiContent
import it.myacamperlife.app.ui.viaggi.TappeContent
import it.myacamperlife.app.ui.viaggi.ViaggiViewModel

/**
 * Il contenitore dell'app.
 *
 * Barra superiore e pulsante di importazione stanno qui, non nelle singole
 * schermate: le due viste sono cosi' semplici contenuti, e le spaziature di
 * sistema restano coerenti passando dall'una all'altra.
 *
 * Fase 1: elenco dei viaggi ed elenco delle tappe. Le quattro schede in
 * basso — Viaggio, Diario, Numeri, Esplora — arrivano quando ci sara'
 * qualcosa da metterci dentro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyaApp(vista: ViaggiViewModel) {
    val stato by vista.stato.collectAsStateWithLifecycle()
    val avvisi = remember { SnackbarHostState() }

    val scegliFile = rememberLauncherForActivityResult(
        // Un itinerario e' un .md, ma i gestori file lo annunciano in mille
        // modi diversi: filtrare per tipo nasconderebbe il file da scegliere.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vista::importa) }

    // Il testo si risolve in composizione: stringResource e' una funzione
    // composable e dentro LaunchedEffect non si puo' chiamare.
    val esitoImport = stato.esitoImport
    val avvisoDaMostrare = esitoImport?.let { messaggio(it) }
    LaunchedEffect(esitoImport) {
        if (avvisoDaMostrare != null) {
            avvisi.showSnackbar(avvisoDaMostrare)
            vista.esitoVisto()
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (aperto == null && !stato.caricamento) {
                ExtendedFloatingActionButton(
                    onClick = { scegliFile.launch(arrayOf("*/*")) },
                    text = { Text(stringResource(R.string.azione_importa)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_importa),
                            contentDescription = null,
                        )
                    },
                )
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

                aperto != null -> TappeContent(tappe = stato.tappe)

                else -> ElencoViaggiContent(
                    viaggi = stato.viaggi,
                    onApri = vista::apri,
                    onElimina = vista::elimina,
                )
            }
        }
    }
}

/**
 * Il messaggio dopo un'importazione. Un errore dice **perche'**: "non ho
 * trovato le tappe" senza motivo lascia l'utente a indovinare quale file
 * andava scelto.
 */
@Composable
private fun messaggio(esito: ViaggiViewModel.EsitoImport): String = when (esito) {
    is ViaggiViewModel.EsitoImport.Riuscito ->
        if (esito.scartate == 0) {
            stringResource(R.string.import_riuscito, esito.tappe)
        } else {
            stringResource(R.string.import_riuscito_con_scarti, esito.tappe, esito.scartate)
        }

    is ViaggiViewModel.EsitoImport.Fallito -> when (esito.motivo) {
        Itinerario.Motivo.NESSUN_JSON -> stringResource(R.string.import_senza_json)
        Itinerario.Motivo.NESSUN_WAYPOINTS -> stringResource(R.string.import_senza_waypoints)
        Itinerario.Motivo.NESSUNA_TAPPA -> stringResource(R.string.import_senza_coordinate)
        null -> stringResource(R.string.import_illeggibile)
    }
}
