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
import it.myacamperlife.app.BuildConfig
import it.myacamperlife.app.R
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.archivio.Specchio
import it.myacamperlife.app.avvisi.Avvisi
import it.myacamperlife.app.avvisi.Sistema
import it.myacamperlife.app.rete.EsitoDintorni
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Dossier
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Modello
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Slittamenti
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.ui.diario.DiarioContent
import it.myacamperlife.app.ui.esplora.EsploraContent
import it.myacamperlife.app.ui.foto.FotoDialog
import it.myacamperlife.app.ui.numeri.NumeriContent
import it.myacamperlife.app.ui.viaggi.AggiungiTappaDialog
import it.myacamperlife.app.ui.viaggi.AzioniVoceDialog
import it.myacamperlife.app.ui.viaggi.BriefingDialog
import it.myacamperlife.app.ui.viaggi.DidascaliaDialog
import it.myacamperlife.app.ui.viaggi.DossierDialog
import it.myacamperlife.app.ui.viaggi.ElencoViaggiContent
import it.myacamperlife.app.ui.viaggi.AnnullaCheckinDialog
import it.myacamperlife.app.ui.viaggi.FuoriProgrammaDialog
import it.myacamperlife.app.ui.viaggi.ImpostazioniDialog
import it.myacamperlife.app.ui.viaggi.ModelliDialog
import it.myacamperlife.app.ui.viaggi.NotaDialog
import it.myacamperlife.app.ui.viaggi.RifornimentoDialog
import it.myacamperlife.app.ui.viaggi.SchedaTappaContent
import it.myacamperlife.app.ui.viaggi.SostituisciTappeDialog
import it.myacamperlife.app.ui.viaggi.SpostaDateDialog
import it.myacamperlife.app.ui.viaggi.SpesaDialog
import it.myacamperlife.app.ui.viaggi.TappeContent
import it.myacamperlife.app.ui.viaggi.TestoDialog
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
    // Si tiene l'**id** e non la tappa: dopo un check-in l'elenco si ricarica e
    // gli oggetti sono nuovi, mentre l'id resta. Tenendo la tappa, la scheda
    // mostrerebbe ancora "da fare" su una tappa appena spuntata.
    var tappaAperta by rememberSaveable { mutableStateOf<String?>(null) }
    var notaAperta by remember { mutableStateOf(false) }
    var aggiungiAperto by remember { mutableStateOf(false) }
    var coordinateGps by remember { mutableStateOf<Coordinate?>(null) }
    var fotoScattata by remember { mutableStateOf<File?>(null) }
    var fotoInAttesa by remember { mutableStateOf<File?>(null) }
    var rifornimentoAperto by remember { mutableStateOf(false) }
    var impostazioniAperte by remember { mutableStateOf(false) }
    var spesaAperta by remember { mutableStateOf(false) }
    var scontrinoInAttesa by remember { mutableStateOf<File?>(null) }
    var scontrino by remember { mutableStateOf<File?>(null) }
    var briefingAperto by remember { mutableStateOf(false) }
    var modelliAperti by remember { mutableStateOf(false) }
    var dossierAperto by remember { mutableStateOf<Dossier?>(null) }
    var testoDossier by remember { mutableStateOf<String?>(null) }
    var briefing by remember { mutableStateOf<Briefing?>(null) }

    // Tornare su una voce gia' registrata: prima cosa se ne puo' fare, poi la
    // form giusta per il suo genere.
    var voceScelta by remember { mutableStateOf<Voce?>(null) }
    var voceDaCorreggere by remember { mutableStateOf<Voce?>(null) }

    /** La voce di cui si sta guardando la foto — o lo scontrino. */
    var fotoAperta by remember { mutableStateOf<Voce?>(null) }

    // I due gesti che si chiedono prima di fare: disfare un check-in e
    // riscrivere delle date sono entrambi rimedi a un errore, e un rimedio che
    // parte al primo tocco puo' diventare l'errore dopo.
    var checkinDaAnnullare by remember { mutableStateOf<Tappa?>(null) }
    var dateDaSpostare by remember { mutableStateOf<Tappa?>(null) }

    val scegliFile = rememberLauncherForActivityResult(
        // Un itinerario e' un .md, ma i gestori file lo annunciano in mille
        // modi diversi: filtrare per tipo nasconderebbe il file da scegliere.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vista::importa) }

    // Un lanciatore a parte da quello dell'import: lo stesso gesto — scegli un
    // file .md — con due esiti molto diversi, e confonderli vorrebbe dire creare
    // un viaggio nuovo quando volevi riscrivere questo.
    val scegliSostituzione = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vista::preparaSostituzione) }

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

    // La cartella d'archivio: il selettore di sistema restituisce un albero su
    // cui l'app puo' scrivere, e nient'altro. Nessun permesso di archiviazione.
    val scegliCartella = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null && Specchio.ricorda(contesto, uri)) {
            vista.scegliCartella(uri.toString(), Specchio.nome(contesto, uri))
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

    // Un check-in fuori programma non e' un messaggio da mostrare e scartare:
    // porta una domanda, e la domanda la fa un dialogo. Si tiene da parte finche'
    // non le si risponde.
    var fuoriProgramma by remember { mutableStateOf<ViaggiViewModel.Avviso.FuoriProgramma?>(null) }
    LaunchedEffect(avviso) {
        if (avviso is ViaggiViewModel.Avviso.FuoriProgramma) {
            fuoriProgramma = avviso
            vista.avvisoVisto()
        }
    }

    val testoAvviso = avviso
        ?.takeUnless { it is ViaggiViewModel.Avviso.FuoriProgramma }
        ?.let { messaggio(it) }
    LaunchedEffect(testoAvviso) {
        if (testoAvviso != null) {
            avvisi.showSnackbar(testoAvviso)
            vista.avvisoVisto()
        }
    }

    val aperto = stato.aperto

    // La tappa aperta si risolve dall'elenco vivo, cosi' la scheda segue le
    // modifiche; se sparisce — viaggio chiuso, tappa eliminata — si torna
    // all'elenco invece di restare su una schermata orfana.
    val tappaScelta = tappaAperta?.let { id -> stato.tappe.firstOrNull { it.id == id } }

    // Indietro chiude prima la scheda e poi il viaggio: sono due livelli, e
    // saltarne uno farebbe uscire dal viaggio da dentro una tappa.
    BackHandler(enabled = aperto != null || tappaScelta != null) {
        if (tappaScelta != null) tappaAperta = null else vista.chiudi()
    }

    // L'Uri della cartella scelta, letto dalle impostazioni. Puo' essere
    // scritto nel file e non piu' accessibile: dopo una reinstallazione il
    // permesso e' perso, e le impostazioni lo dicono invece di tacere.
    val cartellaScelta = stato.impostazioni.cartellaSpecchio?.let { salvata ->
        runCatching { Uri.parse(salvata) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(tappaScelta?.nome ?: aperto?.nome ?: stringResource(R.string.app_name))
                },
                navigationIcon = {
                    if (aperto != null) {
                        IconButton(
                            onClick = { if (tappaScelta != null) tappaAperta = null else vista.chiudi() },
                        ) {
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
                            // La barra resta anche dentro una scheda di tappa, e
                            // toccare una linguetta ne esce: nascondere le schede
                            // li' dentro sarebbe un vicolo con una sola uscita.
                            selected = scheda == voce && tappaScelta == null,
                            onClick = { tappaAperta = null; scheda = voce },
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

                // Dentro una tappa il pulsante non c'entra: aggiungerne una da
                // li' vorrebbe dire aggiungerla dove?
                tappaScelta != null -> Unit

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
                    // Manca una cartella usabile: mai scelta, o scelta da
                    // un'installazione che non c'e' piu'. In entrambi i casi il
                    // rimedio e' lo stesso, e la fusione fa il resto.
                    cartellaDaScegliere = cartellaScelta == null ||
                        !Specchio.accessibile(contesto, cartellaScelta),
                    onApri = { viaggio -> scheda = Scheda.VIAGGIO; vista.apri(viaggio) },
                    onElimina = vista::elimina,
                    onScegliCartella = { scegliCartella.launch(null) },
                )

                // La scheda di una tappa sta sopra a tutte le schede: si e'
                // arrivati qui da una tappa, e si torna indietro da dove si e'
                // venuti.
                tappaScelta != null -> SchedaTappaContent(
                    tappe = stato.tappe,
                    inizialeId = tappaScelta.id,
                    poi = stato.poi,
                    tratte = stato.tratte,
                    meteo = stato.meteo,
                    dossier = stato.dossier,
                    programma = stato.programma,
                    aiConfigurata = vista.aiConfigurata(),
                    inCorso = stato.inCorso,
                    onCheckin = { tappa -> conPosizione { vista.checkin(tappa) } },
                    onAlterna = vista::alternaSalto,
                    onMappa = { tappa ->
                        apriNellaMappa(contesto, tappa.lat, tappa.lon, tappa.nome)
                    },
                    onChiedi = vista::chiediDiTappa,
                    onDossier = { salvato ->
                        ambito.launch {
                            testoDossier = vista.testoDossier(salvato.file)
                            dossierAperto = salvato
                        }
                    },
                    onScarica = vista::cercaDintorniDi,
                    onAnnullaCheckin = { tappa -> checkinDaAnnullare = tappa },
                    onSpostaDate = { tappa -> dateDaSpostare = tappa },
                    onTappaCambiata = { tappa -> tappaAperta = tappa.id },
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
                    onTappa = { tappaAperta = it.id },
                    onSostituisci = { scegliSostituzione.launch(arrayOf("*/*")) },
                )

                scheda == Scheda.DIARIO -> DiarioContent(
                    voci = stato.voci,
                    giorni = stato.giorni,
                    prosaPossibile = vista.aiConfigurata(),
                    onProsa = vista::riscriviGiornata,
                    onCronaca = vista::rigeneraDiario,
                    onVoce = { voceScelta = it },
                    allegato = vista::allegato,
                    onFoto = { fotoAperta = it },
                )

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
                    dossier = stato.dossier,
                    aiConfigurata = vista.aiConfigurata(),
                    inCorso = stato.inCorso,
                    onScarica = vista::cercaDintorniQui,
                    onApri = { vicino ->
                        apriNellaMappa(contesto, vicino.poi.lat, vicino.poi.lon, vicino.poi.etichetta())
                    },
                    onChiedi = vista::chiedi,
                    onDossier = { salvato ->
                        ambito.launch {
                            testoDossier = vista.testoDossier(salvato.file)
                            dossierAperto = salvato
                        }
                    },
                    onImpostaAi = { modelliAperti = true },
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

    fuoriProgramma?.let { fuori ->
        FuoriProgrammaDialog(
            tappa = fuori.tappa,
            slittamento = fuori.slittamento,
            onSlitta = { vista.slitta(fuori.tappa, fuori.slittamento.giorni) },
            onChiudi = { fuoriProgramma = null },
        )
    }

    if (notaAperta) {
        NotaDialog(
            onSalva = vista::registraNota,
            onChiudi = { notaAperta = false },
        )
    }

    fotoAperta?.let { voce ->
        FotoDialog(
            file = vista.allegato(voce),
            didascalia = voce.testo,
            onApriFuori = { vista.allegato(voce)?.let { apriFuori(contesto, it) } },
            onChiudi = { fotoAperta = null },
        )
    }

    stato.sostituzione?.let { proposta ->
        SostituisciTappeDialog(
            sostituzione = proposta,
            onConferma = vista::confermaSostituzione,
            onChiudi = vista::scartaSostituzione,
        )
    }

    checkinDaAnnullare?.let { tappa ->
        AnnullaCheckinDialog(
            tappa = tappa,
            onAnnulla = { vista.annullaCheckin(tappa) },
            onChiudi = { checkinDaAnnullare = null },
        )
    }

    dateDaSpostare?.let { tappa ->
        SpostaDateDialog(
            tappa = tappa,
            // Il numero sta nella domanda: "sposto questa e altre tre" e' una
            // cosa a cui si puo' rispondere, "sposto l'itinerario" no.
            quante = Slittamenti.quante(stato.tappe, tappa, compresa = true),
            onSposta = { giorni -> vista.spostaDate(tappa, giorni) },
            onChiudi = { dateDaSpostare = null },
        )
    }

    voceScelta?.let { voce ->
        AzioniVoceDialog(
            voce = voce,
            allegato = vista.allegato(voce),
            onCorreggi = { voceDaCorreggere = voce },
            onCancella = { vista.cancellaVoce(voce) },
            onChiudi = { voceScelta = null },
        )
    }

    // Ogni genere ha la sua form, e sono le stesse della registrazione: una
    // spesa si corregge dove la si scrive, non in una schermata gemella che
    // andrebbe tenuta allineata a mano.
    voceDaCorreggere?.let { voce ->
        // Niente chiamate a "chiudi" da qui dentro: cambiare stato **durante** la
        // composizione e' il modo per farla ricominciare da sola. Quando non c'e'
        // niente da mostrare non si mostra niente, e il prossimo tocco sovrascrive.
        val id = voce.id ?: return@let
        val chiudi = { voceDaCorreggere = null }

        when (voce.genere) {
            Genere.NOTA -> TestoDialog(
                titolo = R.string.voce_correggi_nota,
                etichetta = R.string.nota_campo,
                iniziale = voce.testo,
                facoltativo = false,
                onSalva = { testo -> vista.correggiNota(id, testo) },
                onChiudi = chiudi,
            )

            Genere.FOTO -> TestoDialog(
                titolo = R.string.voce_correggi_didascalia,
                etichetta = R.string.foto_didascalia,
                iniziale = voce.testo,
                facoltativo = true,
                onSalva = { testo ->
                    vista.correggiDidascalia(id, testo.takeIf { it.isNotBlank() })
                },
                onChiudi = chiudi,
            )

            Genere.RIFORNIMENTO -> stato.rifornimenti.firstOrNull { it.id == id }?.let { quello ->
                RifornimentoDialog(
                    ultimoKm = stato.ultimoKm,
                    adesso = remember { OffsetDateTime.now() },
                    iniziale = quello,
                    onSalva = { km, euro, prezzo, pieno, istante ->
                        vista.correggiRifornimento(id, km, euro, prezzo, pieno, istante)
                    },
                    onChiudi = chiudi,
                )
            }

            Genere.SPESA -> stato.spese.firstOrNull { it.id == id }?.let { quella ->
                SpesaDialog(
                    valutaSuggerita = quella.valuta,
                    cambioSuggerito = quella.cambio,
                    // Lo scontrino non si ricambia correggendo: e' un file, e
                    // sostituirlo e' un'altra operazione. Quello allegato resta —
                    // la correzione non lo perde — e la form non offre un pulsante
                    // che non farebbe niente.
                    scontrino = null,
                    adesso = remember { OffsetDateTime.now() },
                    iniziale = quella,
                    onScontrino = null,
                    onSalva = { categoria, importo, modalita, descrizione, valuta, cambio, istante ->
                        vista.correggiSpesa(
                            id, categoria, importo, modalita, descrizione, valuta, cambio, istante,
                        )
                        // SpesaDialog non si chiude da se' al salvataggio —
                        // registrando, chiudere scarterebbe la foto dello
                        // scontrino — quindi qui chiude chi salva.
                        chiudi()
                    },
                    onChiudi = chiudi,
                )
            }

            Genere.ARRIVO, Genere.POSIZIONE -> Unit
        }
    }

    fotoScattata?.let { file ->
        DidascaliaDialog(
            file = file,
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
            onScaricaDintorni = vista::cercaDintorniQui,
            scortaDisponibile = stato.aperto != null,
            cartella = cartellaScelta?.let { Specchio.nome(contesto, it) },
            cartellaAccessibile = cartellaScelta?.let { Specchio.accessibile(contesto, it) } ?: false,
            versione = stringResource(
                R.string.impostazioni_versione,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.COMMIT,
            ),
            sincronizzatoIl = stato.impostazioni.sincronizzatoIl?.let { salvata ->
                runCatching { OffsetDateTime.parse(salvata) }.getOrNull()
            },
            meteoIl = stato.meteoIl,
            dintorniIl = stato.dintorniIl,
            onScegliCartella = { scegliCartella.launch(null) },
            onEsporta = vista::esporta,
            onSincronizza = vista::sincronizza,
            onSpegniCartella = {
                cartellaScelta?.let { Specchio.dimentica(contesto, it) }
                vista.spegniCartella()
            },
            onBatteria = { Sistema.apriBatteria(contesto) },
            onAvvioAutomatico = { Sistema.apriAvvioAutomatico(contesto) },
            onModelli = { modelliAperti = true },
            onChiudi = { impostazioniAperte = false },
        )
    }

    if (modelliAperti) {
        ModelliDialog(
            impostazioni = stato.impostazioni,
            chiaviDisponibili = vista.chiaviDisponibili(),
            coda = vista::codaChiave,
            onChiave = vista::salvaChiave,
            onSalva = vista::salvaImpostazioni,
            onChiudi = { modelliAperti = false },
        )
    }

    dossierAperto?.let { salvato ->
        DossierDialog(
            titolo = salvato.titolo(),
            testo = testoDossier,
            onChiudi = { dossierAperto = null; testoDossier = null },
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
                        coordinateGps = posizione?.let { Coordinate(it.lat, it.lon) }
                    }
                }
            },
            onCerca = vista::cercaIndirizzo,
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
private fun apriNellaMappa(
    contesto: android.content.Context,
    lat: Double,
    lon: Double,
    nome: String,
) {
    val gradiLat = String.format(java.util.Locale.ROOT, "%.6f", lat)
    val gradiLon = String.format(java.util.Locale.ROOT, "%.6f", lon)
    val etichetta = Uri.encode(nome)
    val intento = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("geo:$gradiLat,$gradiLon?q=$gradiLat,$gradiLon($etichetta)"),
    )
    // Se non c'e' nessuna app di mappe non si fa niente, invece di cadere.
    if (intento.resolveActivity(contesto.packageManager) != null) contesto.startActivity(intento)
}

/**
 * Apre una foto nell'app che le apre di mestiere.
 *
 * Serve a quello che la vista dentro l'app non fa: ingrandire uno scontrino per
 * leggere una cifra, condividere una foto, ruotarla. L'Uri passa dal
 * FileProvider con il permesso di lettura per una volta sola — il file resta
 * dov'e', non se ne fa una copia in giro.
 *
 * `try`/`catch` e non `resolveActivity`: da Android 11 quest'ultimo restituisce
 * null per le app non dichiarate in `<queries>`, e il pulsante sembrerebbe rotto
 * su un telefono che invece la galleria ce l'ha.
 */
private fun apriFuori(contesto: android.content.Context, file: File) {
    val uri = uriDi(contesto, file)
    val intento = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/*")
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { contesto.startActivity(intento) }
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
    is ViaggiViewModel.Avviso.ImportRiuscito -> {
        val testa = if (avviso.scartate == 0) {
            stringResource(R.string.import_riuscito, avviso.tappe)
        } else {
            stringResource(R.string.import_riuscito_con_scarti, avviso.tappe, avviso.scartate)
        }
        if (avviso.buchi == 0) testa
        else "$testa. " + stringResource(R.string.import_buchi, avviso.buchi)
    }

    is ViaggiViewModel.Avviso.ItinerarioSlittato ->
        stringResource(R.string.itinerario_slittato, avviso.tappe, avviso.giorni)

    // Non e' un messaggio: e' una domanda, e la fa un dialogo. Qui non compare.
    is ViaggiViewModel.Avviso.FuoriProgramma -> ""

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
    ViaggiViewModel.Avviso.VoceCorretta -> stringResource(R.string.voce_corretta)
    ViaggiViewModel.Avviso.VoceCancellata -> stringResource(R.string.voce_cancellata)
    ViaggiViewModel.Avviso.FotoRegistrata -> stringResource(R.string.foto_registrata)
    ViaggiViewModel.Avviso.RifornimentoRegistrato -> stringResource(R.string.rifornimento_registrato)
    ViaggiViewModel.Avviso.SpesaRegistrata -> stringResource(R.string.spesa_registrata)
    ViaggiViewModel.Avviso.ImpostazioniSalvate -> stringResource(R.string.impostazioni_salvate)
    ViaggiViewModel.Avviso.ScortaAggiornata -> stringResource(R.string.scorta_aggiornata)
    ViaggiViewModel.Avviso.ScortaNonAggiornata -> stringResource(R.string.scorta_non_aggiornata)
    is ViaggiViewModel.Avviso.TappeSostituite -> {
        val conti = stringResource(
            R.string.tappe_sostituite,
            avviso.nuove,
            avviso.sostituite,
            avviso.tenute,
        )
        // I giorni saltati si dicono attaccati all'esito, come all'import: sono
        // quasi sempre una dimenticanza nel file, e si scoprono meglio adesso.
        if (avviso.buchi == 0) conti
        else conti + " " + stringResource(R.string.import_buchi, avviso.buchi)
    }
    is ViaggiViewModel.Avviso.CheckinAnnullato ->
        stringResource(R.string.checkin_annullato, avviso.tappa)
    ViaggiViewModel.Avviso.NienteDaSpostare -> stringResource(R.string.niente_da_spostare)
    is ViaggiViewModel.Avviso.DintorniAggiornati ->
        stringResource(R.string.dintorni_aggiornati, avviso.poi, avviso.luoghi)

    // Ogni motivo ha il suo rimedio, e il rimedio sta nel messaggio: aspettare,
    // chiedere meno, o segnalare un difetto. "Non aggiornato" non ne suggeriva
    // nessuno.
    is ViaggiViewModel.Avviso.DintorniFalliti -> when (val esito = avviso.esito) {
        EsitoDintorni.SenzaRete -> stringResource(R.string.dintorni_senza_rete)
        EsitoDintorni.SenzaTappe -> stringResource(R.string.dintorni_senza_tappe)
        EsitoDintorni.Vuoto -> stringResource(R.string.dintorni_vuoto)
        is EsitoDintorni.Illeggibile ->
            stringResource(R.string.dintorni_illeggibile, esito.elementi)
        // Il messaggio del server, alla lettera: dice se ha finito il tempo o la
        // memoria, e sono due cose con due rimedi.
        is EsitoDintorni.Avvertito ->
            stringResource(R.string.dintorni_avvertito, esito.messaggio)
        is EsitoDintorni.Rifiutato -> when (esito.codice) {
            429 -> stringResource(R.string.dintorni_troppe_richieste)
            504 -> stringResource(R.string.dintorni_troppo_grande)
            else -> stringResource(
                R.string.dintorni_rifiutati,
                esito.codice,
                esito.messaggio ?: "",
            )
        }
        is EsitoDintorni.Riuscito -> stringResource(R.string.dintorni_aggiornati, esito.poi, esito.luoghi)
    }
    is ViaggiViewModel.Avviso.SpecchioScelto ->
        stringResource(R.string.specchio_scelto, avviso.cartella)
    is ViaggiViewModel.Avviso.SpecchioFatto ->
        stringResource(R.string.specchio_fatto, avviso.file)

    // Cosa e' entrato, con i numeri: "sincronizzato" non dice se ha trovato un
    // viaggio intero o niente, e dopo una reinstallazione e' proprio quello che
    // si vuole sapere.
    is ViaggiViewModel.Avviso.CartellaFusa -> {
        val esito = avviso.esito
        val testa = when {
            esito.viaggiNuovi > 0 && esito.righeNuove > 0 -> stringResource(
                R.string.cartella_fusa_viaggi_righe, esito.viaggiNuovi, esito.righeNuove,
            )
            esito.viaggiNuovi > 0 -> stringResource(R.string.cartella_fusa_viaggi, esito.viaggiNuovi)
            esito.righeNuove > 0 -> stringResource(R.string.cartella_fusa_righe, esito.righeNuove)
            else -> stringResource(R.string.cartella_fusa_niente)
        }
        val allegati = if (esito.allegati > 0) {
            stringResource(R.string.cartella_fusa_allegati, esito.allegati)
        } else {
            ""
        }
        val impostazioni = if (esito.impostazioni) {
            stringResource(R.string.cartella_fusa_impostazioni)
        } else {
            ""
        }
        val falliti = if (esito.falliti > 0) {
            stringResource(R.string.cartella_fusa_falliti, esito.falliti)
        } else {
            ""
        }
        "$testa$allegati$impostazioni$falliti"
    }
    ViaggiViewModel.Avviso.SpecchioFallito -> stringResource(R.string.specchio_fallito)
    ViaggiViewModel.Avviso.SpecchioSpento -> stringResource(R.string.specchio_spento)
    ViaggiViewModel.Avviso.AiDiRiserva -> stringResource(R.string.ai_di_riserva)
    ViaggiViewModel.Avviso.DiarioRiscritto -> stringResource(R.string.diario_riscritto)
    is ViaggiViewModel.Avviso.AiFallita -> when (val guaio = avviso.guaio) {
        GuaioAi.SenzaChiave -> stringResource(R.string.ai_senza_chiave)
        GuaioAi.SenzaRete -> stringResource(R.string.ai_senza_rete)
        is GuaioAi.Vuota -> stringResource(R.string.ai_vuota, guaio.modello.nome)
        // Il messaggio del servizio si mostra com'e': una chiave scaduta, un
        // modello ritirato e una quota finita hanno tre rimedi diversi.
        is GuaioAi.Rifiutata -> stringResource(
            R.string.ai_rifiutata,
            guaio.modello.nome,
            guaio.codice,
            guaio.messaggio ?: "",
        )
    }
}
