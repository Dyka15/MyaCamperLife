package it.myacamperlife.app.ui.viaggi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.EsitoFusione
import it.myacamperlife.app.archivio.Impostazioni
import it.myacamperlife.app.archivio.Posizione
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.archivio.Viaggio
import it.myacamperlife.app.archivio.VociDelGiorno
import it.myacamperlife.app.dominio.Autonomia
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Cronaca
import it.myacamperlife.app.dominio.Dintorni
import it.myacamperlife.app.dominio.Dossier
import it.myacamperlife.app.dominio.EsitoBriefing
import it.myacamperlife.app.dominio.Esplora
import it.myacamperlife.app.dominio.GiorniDelViaggio
import it.myacamperlife.app.dominio.GiornoTappa
import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Indirizzo
import it.myacamperlife.app.dominio.Consumi
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Conto
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Luoghi
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Modello
import it.myacamperlife.app.dominio.NomeFoto
import it.myacamperlife.app.dominio.Percorso
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.PoiVicino
import it.myacamperlife.app.dominio.Rifornimento
import it.myacamperlife.app.dominio.Rinnovi
import it.myacamperlife.app.dominio.Schede
import it.myacamperlife.app.dominio.SezioneGiorno
import it.myacamperlife.app.dominio.SchedaTappa
import it.myacamperlife.app.dominio.Slittamenti
import it.myacamperlife.app.dominio.Slittamento
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.TestoBriefing
import it.myacamperlife.app.dominio.Tratte
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.dominio.Waypoint
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.rete.Assistente
import it.myacamperlife.app.rete.EsitoAi
import it.myacamperlife.app.rete.EsitoDintorni
import it.myacamperlife.app.rete.EsitoModelli
import it.myacamperlife.app.rete.Geocodifica
import it.myacamperlife.app.rete.RicercaIndirizzo
import it.myacamperlife.app.rete.Scorte
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo stato dell'elenco dei viaggi, del viaggio aperto e del suo diario.
 *
 * Un solo ViewModel per tutte le schermate: lavorano sugli stessi dati e
 * passare dall'una all'altra non deve ricaricare niente.
 *
 * Ogni registrazione e' una scrittura locale che riesce sempre. La posizione
 * si prova a prendere, ma **non e' mai un requisito**: una nota senza
 * coordinate e' meglio di una nota non registrata.
 */
class ViaggiViewModel(
    private val archivio: Archivio,
    private val documenti: Documenti,
    private val posizioni: Posizioni,
    /**
     * Riarma la sveglia del riepilogo quando le impostazioni cambiano.
     * E' l'unico pezzo di Android che serve qui, e arriva da fuori cosi' il
     * resto della classe resta leggibile senza un telefono.
     */
    private val riarma: (Impostazioni) -> LocalDateTime? = { null },
    /**
     * Manda **davvero** la notifica del riepilogo, adesso. Torna `false` se il
     * sistema l'ha scartata per mancanza di permesso.
     *
     * Serve a un pulsante di prova, e quel pulsante e' l'unico modo di
     * distinguere «la sveglia non e' scattata» da «la notifica non passa»: due
     * guasti con due rimedi diversi che da fuori si vedono identici.
     */
    private val manda: (Briefing) -> Boolean = { false },
    /**
     * Riempie la scorta dalla rete. Torna `true` se ha aggiornato qualcosa.
     * Ha un valore di riposo che non fa niente: senza, questa classe non si
     * potrebbe costruire in un test.
     */
    private val scorte: Scorte? = null,
    /** Cerca le coordinate di un indirizzo: scorta prima, rete poi. */
    private val geocodifica: Geocodifica? = null,
    /**
     * Mette in coda una passata di specchio verso la cartella scelta.
     *
     * Si chiama **dopo** ogni scrittura, non prima e non durante: la
     * registrazione e' un append locale che riesce sempre, la copia fuori e'
     * differita e puo' fallire senza conseguenze.
     */
    private val rispecchia: () -> Unit = {},
    /**
     * Copia tutto l'archivio nella cartella scelta, adesso, e dice quanti file
     * ha toccato — `null` se non ha potuto.
     */
    private val esportaTutto: (suspend () -> Int?)? = null,
    /**
     * Fonde nell'archivio quello che c'e' gia' nella cartella scelta.
     *
     * Arriva da fuori come l'esportazione, e per la stessa ragione: leggere un
     * albero SAF ha bisogno di un `Context`, e questa classe deve restare
     * costruibile in un test.
     */
    private val fondiDallaCartella: (suspend () -> EsitoFusione?)? = null,
    /** Il client dei modelli: principale e riserva. */
    private val assistente: Assistente? = null,
) : ViewModel() {

    data class Stato(
        val caricamento: Boolean = true,
        val viaggi: List<Viaggio> = emptyList(),
        val aperto: Viaggio? = null,
        val tappe: List<Tappa> = emptyList(),
        val voci: List<Voce> = emptyList(),
        val diario: String = "",
        val consumo: Consumo = Consumo(emptyList()),
        val conto: Conto = Spese.conta(emptyList()),
        val spese: List<Spesa> = emptyList(),
        /**
         * I rifornimenti registrati: servono a riempire la form quando se ne
         * corregge uno, e sono gia' letti per calcolare il consumo.
         */
        val rifornimenti: List<Rifornimento> = emptyList(),
        val autonomia: Autonomia? = null,
        val tratte: Tratte = Tratte(),
        val poi: List<Poi> = emptyList(),
        /**
         * I toponimi salvati con i punti d'interesse: sono il geocoding inverso
         * offline, e servono a dire **in che paese** sta un punto. Le coordinate
         * da sole non rispondono alla domanda che si fa guardando un elenco.
         */
        val luoghi: Luoghi = Luoghi(),
        /**
         * La scorta di previsioni. Sta nello stato e non si rilegge a ogni
         * scheda: e' un file piccolo, e la schermata di una tappa deve aprirsi
         * senza toccare il disco.
         */
        val meteo: Meteo? = null,
        val dossier: List<Dossier> = emptyList(),
        /** Da dove si cerca nei dintorni: l'ultima posizione nota. */
        val quiVicino: Coordinate? = null,
        val impostazioni: Impostazioni = Impostazioni(),
        /** L'ultimo contachilometri registrato: precompila la form. */
        /**
         * Il programma giorno per giorno, dall'itinerario originale. Sta nello
         * stato per la stessa ragione del meteo: aprire la scheda di una tappa
         * non deve leggere un file.
         */
        val programma: List<SezioneGiorno> = emptyList(),
        /** Quando la scorta e' stata presa: si mostra, perche' l'eta' conta. */
        val meteoIl: OffsetDateTime? = null,
        val dintorniIl: OffsetDateTime? = null,
        val inCorso: Boolean = false,
        /**
         * L'itinerario nuovo, letto e non ancora scritto.
         *
         * Sta nello stato perche' e' una **domanda in sospeso**: sostituire dieci
         * tappe e' un gesto che si mostra prima di farlo, coi numeri veri, e i
         * numeri veri si sanno solo dopo aver letto il file.
         */
        val sostituzione: Sostituzione? = null,
        /** Un itinerario letto dall'elenco, di cui non si sa ancora cosa sia. */
        val sceltaImport: SceltaImport? = null,
        val avviso: Avviso? = null,
    ) {
        val kmConUnPieno: Int? get() = impostazioni.kmConUnPieno
        val corrente: Tappa? get() = Tappe.corrente(tappe)
        val prossima: Tappa? get() = Tappe.prossima(tappe)

        /**
         * Quanto manca alla prossima tappa, su strada.
         *
         * Solo con le tratte precalcolate: la linea d'aria in testata sarebbe
         * un numero che sembra una distanza di guida e non lo e'. Meglio non
         * mostrarla che mostrarla travestita.
         */
        val versoProssima: Percorso?
            get() {
                val da = corrente ?: return null
                val a = prossima ?: return null
                return tratte.percorso(
                    listOf(Coordinate(da.lat, da.lon), Coordinate(a.lat, a.lon)),
                )
            }
        val giorni: List<LocalDate>
            get() = voci.map { it.istante.toLocalDate() }.distinct().sortedDescending()

        /**
         * Quante cose ci sono per categoria, da dove sei.
         *
         * Serve a non offrire categorie vuote: toccare "Campeggi" e trovare una
         * lista bianca fa sembrare rotta l'app, quando invece li' non ci sono
         * campeggi.
         */
        val perCategoria: Map<CategoriaPoi, Int>
            get() = quiVicino?.let { Dintorni.quanti(poi, it.lat, it.lon) } ?: emptyMap()

        fun vicini(categoria: CategoriaPoi?): List<PoiVicino> {
            val da = quiVicino ?: return emptyList()
            return Dintorni.vicini(poi, da.lat, da.lon, categoria)
        }

        /**
         * Tutti i punti di una categoria attorno a una tappa, **senza tetto**.
         *
         * Il tetto dei trenta risultati vale dove si scorre per curiosita'; qui
         * si e' toccato "Da vedere · 24" e ci si aspetta ventiquattro righe.
         * Troncare a trenta senza dirlo farebbe sparire dei punti dal conteggio
         * che li ha annunciati — e' lo stesso numero, e deve tornare.
         */
        fun tuttiDi(tappa: Tappa, categoria: CategoriaPoi): List<PoiVicino> =
            Dintorni.vicini(poi, tappa.lat, tappa.lon, categoria, quanti = Int.MAX_VALUE)

        /**
         * La scheda di una tappa: descrizione, meteo di quel giorno, dintorni.
         *
         * Non sta nello stato perche' e' una **vista** su dati che ci sono
         * gia': tenerne una copia significherebbe doverla invalidare a ogni
         * check-in, e sarebbe l'unico pezzo di stato con quel problema.
         *
         * La schermata se la compone da se' — cosi' puo' congelare l'orologio e
         * ricalcolare solo quando i dati cambiano; questa serve al contesto da
         * dare al modello, dove l'adesso e' proprio adesso.
         */
        fun scheda(tappa: Tappa): SchedaTappa = Schede.componi(
            tappa = tappa,
            tappe = tappe,
            oggi = LocalDate.now(),
            poi = poi,
            tratte = tratte,
            meteo = meteo,
            adesso = OffsetDateTime.now(),
            dossier = dossier,
        )
    }

    /**
     * Un itinerario nuovo letto dal file, pronto a sostituire il seguito del
     * viaggio — e i numeri per poterlo chiedere.
     *
     * I waypoint e il documento viaggiano dentro: leggere due volte lo stesso
     * file vorrebbe dire che fra la domanda e la risposta il file puo' essere
     * cambiato, e la risposta sarebbe a una domanda diversa.
     */
    data class Sostituzione(
        val nomeFile: String?,
        /** Il titolo scritto nel file, per il caso in cui diventi un viaggio nuovo. */
        val nome: String?,
        /**
         * Il viaggio che verrebbe riscritto.
         *
         * Sta dentro la proposta e non si legge da «il viaggio aperto»: la
         * domanda arriva anche dall'elenco, dove nessun viaggio e' aperto, e
         * rispondere «sostituisci» dovrebbe comunque sapere a chi.
         */
        val bersaglio: Viaggio,
        val nuove: Int,
        val sostituite: Int,
        val tenute: Int,
        /** Il giorno della prima tappa nuova, come lo scrive il file. */
        val dal: String?,
        val scartate: Int,
        /**
         * Quante delle tappe che escono erano datate **prima di oggi** e non
         * spuntate.
         *
         * Si dice perche' sorprende: per l'app una tappa di tre giorni fa che
         * nessuno ha spuntato e' ancora «da fare», quindi la sostituzione se la
         * porta via. Quasi sempre e' giusto — non la farai piu' — ma va detto
         * prima, non scoperto dopo.
         */
        val arretrate: Int,
        internal val punti: List<Waypoint>,
        internal val documento: String,
    )

    /**
     * Un itinerario letto dall'elenco dei viaggi, in attesa di sapere **cos'e'**.
     *
     * Dall'elenco un file puo' voler dire due cose e i viaggi sono piu' d'uno:
     * prima si scopre quale, poi si contano le tappe di quello. Contarle per tutti
     * in anticipo sarebbe lavoro buttato per tutti tranne uno.
     */
    data class SceltaImport(
        val nomeFile: String?,
        val nome: String?,
        val scartate: Int,
        internal val punti: List<Waypoint>,
        internal val documento: String,
    )

    /** Un messaggio da mostrare una volta e poi scartare. */
    sealed interface Avviso {
        data class ImportRiuscito(
            val tappe: Int,
            val scartate: Int,
            /** Giorni che l'itinerario salta: si dice, non si corregge. */
            val buchi: Int = 0,
        ) : Avviso
        data class ImportFallito(val motivo: Itinerario.Motivo?) : Avviso

        /**
         * Il seguito del viaggio e' stato riscritto: quante tappe sono entrate,
         * quante sono uscite, quante sono restate.
         */
        data class TappeSostituite(
            val nuove: Int,
            val sostituite: Int,
            val tenute: Int,
            val buchi: Int = 0,
        ) : Avviso
        data object PosizioneAssente : Avviso
        data object PosizioneRegistrata : Avviso
        data object PermessoPosizioneNegato : Avviso
        data class TappaAggiunta(val nome: String) : Avviso

        /**
         * Un check-in fuori programma di almeno un giorno.
         *
         * Non e' un messaggio da mostrare e scartare come gli altri: porta la
         * proposta di spostare l'itinerario, e va chiesta.
         */
        data class FuoriProgramma(val tappa: Tappa, val slittamento: Slittamento) : Avviso

        data class ItinerarioSlittato(val tappe: Int, val giorni: Long) : Avviso

        /**
         * Nessuna tappa da spostare: erano tutte fatte, saltate, o senza una data
         * leggibile. Si dice, perche' un gesto che non fa niente in silenzio
         * sembra un gesto che non funziona.
         */
        data object NienteDaSpostare : Avviso

        data class CheckinAnnullato(val tappa: String) : Avviso
        data object NotaRegistrata : Avviso
        data object VoceCorretta : Avviso
        data object VoceCancellata : Avviso
        data object FotoRegistrata : Avviso
        data object RifornimentoRegistrato : Avviso
        data object SpesaRegistrata : Avviso
        data object ImpostazioniSalvate : Avviso
        data object ScortaAggiornata : Avviso
        data object ScortaNonAggiornata : Avviso
        data class DintorniAggiornati(val poi: Int, val luoghi: Int) : Avviso
        data class DintorniFalliti(val esito: EsitoDintorni) : Avviso

        /** Quali modelli vede una chiave: l'elenco, o perche' non si sa. */
        data class ModelliVerificati(val esito: EsitoModelli) : Avviso

        /** Com'e' finita la prova del riepilogo: mandata, o perche' no. */
        data class BriefingProvato(val esito: EsitoBriefing) : Avviso
        data class SpecchioScelto(val cartella: String) : Avviso
        data class SpecchioFatto(val file: Int) : Avviso
        data class CartellaFusa(val esito: EsitoFusione) : Avviso
        data object SpecchioFallito : Avviso
        data object SpecchioSpento : Avviso
        data class AiFallita(val guaio: GuaioAi) : Avviso
        data object AiDiRiserva : Avviso
        data object DiarioRiscritto : Avviso
    }

    private val _stato = MutableStateFlow(Stato())
    val stato: StateFlow<Stato> = _stato.asStateFlow()

    init {
        ricarica()
    }

    // --- viaggi -------------------------------------------------------------

    private fun ricarica(apri: Viaggio? = null) = viewModelScope.launch {
        // Le impostazioni si leggono anche senza un viaggio aperto: la
        // schermata delle impostazioni si apre da qualunque punto, e mostrare
        // valori di riposo su un file che ne ha altri li cancellerebbe al
        // primo salvataggio.
        val caricato = withContext(Dispatchers.IO) {
            archivio.prepara()
            archivio.viaggi() to archivio.impostazioni()
        }
        val (elenco, impostazioni) = caricato
        val daAprire = apri
            ?: _stato.value.aperto?.let { aperto -> elenco.find { it.slug == aperto.slug } }
        _stato.update {
            it.copy(
                caricamento = false,
                viaggi = elenco,
                aperto = daAprire,
                impostazioni = impostazioni,
            )
        }
        daAprire?.let { aggiornaViaggio(it) }
    }

    private suspend fun aggiornaViaggio(viaggio: Viaggio) {
        val dati = withContext(Dispatchers.IO) {
            val slug = viaggio.slug
            val rifornimenti = archivio.rifornimenti(slug)
            val impostazioni = archivio.impostazioni()
            DatiViaggio(
                tappe = archivio.tappe(slug),
                voci = archivio.voci(slug),
                diario = archivio.diario(slug).testo(),
                consumo = Consumi.calcola(rifornimenti),
                conto = archivio.conto(slug),
                spese = archivio.spese(slug),
                rifornimenti = rifornimenti,
                autonomia = StimaAutonomia.calcola(
                    kmConUnPieno = impostazioni.kmConUnPieno,
                    rifornimenti = rifornimenti,
                    punti = archivio.punti(slug),
                ),
                impostazioni = impostazioni,
                tratte = archivio.tratte(slug),
                poi = archivio.poi(slug),
                luoghi = archivio.luoghi(slug),
                meteo = archivio.meteo(slug),
                dossier = archivio.dossier(slug),
                quiVicino = archivio.dovePunto(slug),
                programma = archivio.programma(slug),
                meteoIl = archivio.meteoAggiornatoIl(slug),
                dintorniIl = archivio.dintorniAggiornatiIl(slug),
            )
        }
        _stato.update {
            it.copy(
                aperto = viaggio,
                tappe = dati.tappe,
                voci = dati.voci,
                diario = dati.diario,
                consumo = dati.consumo,
                conto = dati.conto,
                spese = dati.spese,
                rifornimenti = dati.rifornimenti,
                autonomia = dati.autonomia,
                impostazioni = dati.impostazioni,
                tratte = dati.tratte,
                poi = dati.poi,
                luoghi = dati.luoghi,
                meteo = dati.meteo,
                dossier = dati.dossier,
                quiVicino = dati.quiVicino,
                programma = dati.programma,
                meteoIl = dati.meteoIl,
                dintorniIl = dati.dintorniIl,
            )
        }
    }

    private data class DatiViaggio(
        val tappe: List<Tappa>,
        val voci: List<Voce>,
        val diario: String,
        val consumo: Consumo,
        val conto: Conto,
        val spese: List<Spesa>,
        val rifornimenti: List<Rifornimento>,
        val autonomia: Autonomia?,
        val impostazioni: Impostazioni,
        val tratte: Tratte,
        val poi: List<Poi>,
        val luoghi: Luoghi,
        val meteo: Meteo?,
        val dossier: List<Dossier>,
        val quiVicino: Coordinate?,
        val programma: List<SezioneGiorno>,
        val meteoIl: OffsetDateTime?,
        val dintorniIl: OffsetDateTime?,
    )

    fun apri(viaggio: Viaggio) = viewModelScope.launch { aggiornaViaggio(viaggio) }

    fun chiudi() = _stato.update {
        it.copy(aperto = null, tappe = emptyList(), voci = emptyList(), diario = "")
    }

    fun elimina(viaggio: Viaggio) = viewModelScope.launch {
        withContext(Dispatchers.IO) { archivio.elimina(viaggio.slug) }
        _stato.update { it.copy(aperto = null, tappe = emptyList(), voci = emptyList(), diario = "") }
        ricarica()
    }

    /**
     * Carica un file di itinerario.
     *
     * **Con un viaggio aperto lo stesso gesto ha due significati**, e l'app non
     * puo' indovinare quale: «un viaggio nuovo» oppure «riscrivi il seguito di
     * questo». Prima ne assumeva uno — creava un viaggio nuovo — e chi voleva
     * l'altro si ritrovava con un doppione e col vecchio piano intatto, senza
     * capire cosa non aveva funzionato. Ora si chiede, coi numeri davanti.
     *
     * Fuori da un viaggio la domanda non esiste: non c'e' un seguito da
     * riscrivere, e si crea.
     */
    fun importa(uri: Uri) = viewModelScope.launch {
        val aperto = _stato.value.aperto
        if (aperto != null) {
            preparaSostituzione(uri, aperto)
            return@launch
        }
        // Dall'elenco, con dei viaggi in casa, la stessa domanda: viaggio nuovo o
        // seguito di uno di questi? Solo col primo viaggio in assoluto non c'e'
        // niente da chiedere.
        if (_stato.value.viaggi.isNotEmpty()) {
            preparaScelta(uri)
            return@launch
        }
        _stato.update { it.copy(caricamento = true, avviso = null) }

        val esito = withContext(Dispatchers.IO) {
            val documento = documenti.leggi(uri)
            if (documento == null) {
                archivio.annotaImport("file non leggibile")
                return@withContext Esito(avviso = Avviso.ImportFallito(null))
            }

            when (val letto = Itinerario.leggi(documento.testo)) {
                is Itinerario.Esito.Fallito -> {
                    archivio.annotaImport("file non capito (${letto.motivo}): ${documento.nome}")
                    Esito(avviso = Avviso.ImportFallito(letto.motivo))
                }
                is Itinerario.Esito.Riuscito -> {
                    val nome = nomeViaggio(letto.nome, documento.nome)
                    archivio.prepara()
                    val viaggio = archivio.creaViaggio(
                        nome = nome,
                        punti = letto.tappe,
                        importatoDa = documento.nome,
                        // Il documento intero, non solo i waypoint: il programma
                        // delle giornate sta nel testo, e prima si buttava.
                        documento = documento.testo,
                    )
                    // I giorni che l'itinerario salta si dicono subito: **un
                    // giorno di viaggio e' un giorno di viaggio anche se non ci si
                    // sposta**, e un giorno mancante e' quasi sempre una
                    // dimenticanza. Meglio scoprirla a casa che la sera del giorno
                    // che manca.
                    val buchi = GiorniDelViaggio.buchi(archivio.tappe(viaggio.slug), LocalDate.now())
                    Esito(
                        viaggio,
                        Avviso.ImportRiuscito(letto.tappe.size, letto.scartati, buchi.size),
                    )
                }
            }
        }

        _stato.update { it.copy(avviso = esito.avviso) }
        ricarica(apri = esito.viaggio)

        // Le distanze su strada si chiedono adesso, non quando serviranno:
        // l'itinerario si importa a casa, dove il campo c'e'. Da qui in poi
        // sono un dato locale, e basta questa finestra per tutto il viaggio.
        esito.viaggio?.let { viaggio ->
            val scorte = scorte ?: return@let
            val tratte = scorte.aggiornaTratte(viaggio.slug)
            // **Niente dintorni all'import.** Prima si provava a scaricare i
            // punti di interesse di tutto l'itinerario in un colpo, e era la
            // richiesta che non funzionava: troppo larga per un server di
            // cortesia, che rispondeva 200 con zero risultati. Adesso i dintorni
            // si cercano una tappa per volta, quando si apre quella tappa e si
            // chiede — dove il risultato si vede subito e un guasto si nota.
            if (tratte) aggiornaViaggio(viaggio)
        }
    }

    /**
     * Legge un itinerario nuovo e **chiede** se sostituire il seguito del viaggio.
     *
     * Due tempi di proposito: qui si legge e si contano le tappe, la scrittura la
     * fa [confermaSostituzione]. Sostituire dieci tappe al primo tocco su un file
     * scelto da un gestore file — dove il nome sbagliato e' un dito di distanza —
     * sarebbe un gesto senza rete di sicurezza.
     */
    fun preparaSostituzione(uri: Uri, bersaglio: Viaggio? = null) = viewModelScope.launch {
        val viaggio = bersaglio ?: _stato.value.aperto ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }

        // `Any?` perche' le due uscite sono di tipi diversi: una proposta da
        // mostrare, oppure un avviso da dire. Il `when` sotto le separa.
        val esito: Any? = withContext(Dispatchers.IO) {
            val documento = documenti.leggi(uri)
            if (documento == null) {
                archivio.annotaImport("file non leggibile")
                return@withContext null
            }
            when (val letto = Itinerario.leggi(documento.testo)) {
                is Itinerario.Esito.Fallito -> {
                    archivio.annotaImport("file non capito (${letto.motivo}): ${documento.nome}")
                    Avviso.ImportFallito(letto.motivo)
                }
                is Itinerario.Esito.Riuscito -> {
                    // I conti si fanno sulle tappe vere, non si stimano: e' il
                    // numero che finisce nella domanda.
                    val prova = Rinnovi.componi(archivio.tappe(viaggio.slug), letto.tappe) { "" }
                    val oggi = LocalDate.now()
                    Sostituzione(
                        nomeFile = documento.nome,
                        nome = letto.nome,
                        bersaglio = viaggio,
                        arretrate = prova.sostituite.count { tappa ->
                            GiornoTappa.leggi(tappa.giorno, oggi)?.isBefore(oggi) == true
                        },
                        nuove = prova.nuove.size,
                        sostituite = prova.sostituite.size,
                        tenute = prova.tenute.size,
                        dal = letto.tappe.firstOrNull()?.giorno,
                        scartate = letto.scartati,
                        punti = letto.tappe,
                        documento = documento.testo,
                    )
                }
            }
        }

        when (esito) {
            null -> _stato.update { it.copy(inCorso = false, avviso = Avviso.ImportFallito(null)) }
            is Sostituzione -> _stato.update { it.copy(inCorso = false, sostituzione = esito) }
            is Avviso -> _stato.update { it.copy(inCorso = false, avviso = esito) }
        }
    }

    /**
     * Legge l'itinerario scelto dall'elenco e **chiede cos'e'**.
     *
     * Il file si legge una volta sola: la risposta — viaggio nuovo, o seguito di
     * quello — arriva dopo, e rileggere vorrebbe dire rispondere a una domanda
     * fatta su un file che nel frattempo puo' essere cambiato.
     */
    private fun preparaScelta(uri: Uri) = viewModelScope.launch {
        _stato.update { it.copy(caricamento = true, avviso = null) }

        val esito: Any? = withContext(Dispatchers.IO) {
            val documento = documenti.leggi(uri)
            if (documento == null) {
                archivio.annotaImport("file non leggibile")
                return@withContext null
            }
            when (val letto = Itinerario.leggi(documento.testo)) {
                is Itinerario.Esito.Fallito -> {
                    archivio.annotaImport("file non capito (${letto.motivo}): ${documento.nome}")
                    Avviso.ImportFallito(letto.motivo)
                }
                is Itinerario.Esito.Riuscito -> SceltaImport(
                    nomeFile = documento.nome,
                    nome = letto.nome,
                    scartate = letto.scartati,
                    punti = letto.tappe,
                    documento = documento.testo,
                )
            }
        }

        when (esito) {
            null -> _stato.update {
                it.copy(caricamento = false, avviso = Avviso.ImportFallito(null))
            }
            is SceltaImport -> _stato.update { it.copy(caricamento = false, sceltaImport = esito) }
            is Avviso -> _stato.update { it.copy(caricamento = false, avviso = esito) }
        }
    }

    fun scartaScelta() = _stato.update { it.copy(sceltaImport = null) }

    /**
     * «E' il seguito di questo viaggio»: si passa alla proposta, coi numeri di
     * **quel** viaggio.
     *
     * La scelta arriva **come parametro** e non si rilegge dallo stato. Non e' un
     * dettaglio di stile: e' il difetto che ha reso inerte questa funzione dal
     * primo giorno. Il pulsante del dialogo chiudeva e poi agiva — `onChiudi();
     * onAzione()`, come tutti gli altri dialoghi dell'app — ma qui chiudere
     * **cancella la domanda dallo stato**, e l'azione la rileggeva da la'
     * trovando `null`: usciva in silenzio, senza scrivere niente e senza dirlo.
     * Un dato che arriva come parametro non puo' essere cancellato da chi lo
     * passa.
     */
    fun seguitoDi(scelta: SceltaImport, viaggio: Viaggio) = viewModelScope.launch {
        _stato.update { it.copy(sceltaImport = null, inCorso = true) }

        val proposta = withContext(Dispatchers.IO) {
            val prova = Rinnovi.componi(archivio.tappe(viaggio.slug), scelta.punti) { "" }
            val oggi = LocalDate.now()
            Sostituzione(
                nomeFile = scelta.nomeFile,
                nome = scelta.nome,
                bersaglio = viaggio,
                arretrate = prova.sostituite.count { tappa ->
                    GiornoTappa.leggi(tappa.giorno, oggi)?.isBefore(oggi) == true
                },
                nuove = prova.nuove.size,
                sostituite = prova.sostituite.size,
                tenute = prova.tenute.size,
                dal = scelta.punti.firstOrNull()?.giorno,
                scartate = scelta.scartate,
                punti = scelta.punti,
                documento = scelta.documento,
            )
        }
        _stato.update { it.copy(inCorso = false, sostituzione = proposta) }
    }

    /** «E' un viaggio nuovo»: dall'elenco, senza rileggere il file. */
    fun viaggioNuovoDallaScelta(scelta: SceltaImport) = viewModelScope.launch {
        _stato.update { it.copy(sceltaImport = null) }
        crea(scelta.nome, scelta.nomeFile, scelta.punti, scelta.documento, scelta.scartate)
    }

    /** Chiude la domanda senza toccare niente. */
    fun scartaSostituzione() = _stato.update { it.copy(sostituzione = null) }

    /**
     * L'altra risposta alla stessa domanda: **un viaggio nuovo**, dai punti gia'
     * letti.
     *
     * Non rilegge il file — quello letto e' quello su cui si e' risposto — e non
     * tocca il viaggio aperto: lo lascia dov'e' e apre quello nuovo.
     */
    fun creaViaggioDaProposta(proposta: Sostituzione) = viewModelScope.launch {
        _stato.update { it.copy(sostituzione = null) }
        crea(proposta.nome, proposta.nomeFile, proposta.punti, proposta.documento, proposta.scartate)
    }

    /**
     * Crea un viaggio da punti **gia' letti**, e lo apre.
     *
     * La coda dell'import, condivisa da tutte le strade che portano a un viaggio
     * nuovo: dall'elenco, dalla domanda dell'elenco, o dalla proposta di
     * sostituzione a cui si e' risposto «no, e' un viaggio nuovo».
     */
    private suspend fun crea(
        titolo: String?,
        nomeFile: String?,
        punti: List<Waypoint>,
        documento: String,
        scartate: Int,
    ) {
        _stato.update { it.copy(caricamento = true, avviso = null) }

        val esito = withContext(Dispatchers.IO) {
            archivio.prepara()
            val viaggio = archivio.creaViaggio(
                nome = nomeViaggio(titolo, nomeFile),
                punti = punti,
                importatoDa = nomeFile,
                documento = documento,
            )
            // La traccia dice **cosa** e' diventato il file e **dove** e' andato:
            // e' la risposta alla domanda che nasce quando sullo schermo compare
            // altro da quello che si aspettava.
            archivio.annotaImport(
                "viaggio nuovo «${viaggio.nome}» (${viaggio.slug}): ${punti.size} tappe" +
                    (nomeFile?.let { ", da $it" } ?: ""),
            )
            val buchi = GiorniDelViaggio.buchi(archivio.tappe(viaggio.slug), LocalDate.now())
            Esito(viaggio, Avviso.ImportRiuscito(punti.size, scartate, buchi.size))
        }

        _stato.update { it.copy(caricamento = false, avviso = esito.avviso) }
        ricarica(apri = esito.viaggio)

        esito.viaggio?.let { viaggio ->
            val scorte = scorte ?: return@let
            if (scorte.aggiornaTratte(viaggio.slug)) aggiornaViaggio(viaggio)
        }
    }

    /**
     * Come si chiama un viaggio: il titolo dell'itinerario, o il nome del file,
     * o un ripiego. Mai vuoto — un viaggio senza nome non si ritrova in un
     * elenco.
     */
    private fun nomeViaggio(titolo: String?, nomeFile: String?): String = titolo
        ?: nomeFile?.substringBeforeLast('.')?.trim()?.takeUnless { it.isEmpty() }
        ?: "Viaggio senza nome"

    /**
     * Scrive la sostituzione proposta: le tappe da fare escono, quelle del file
     * nuovo entrano, tutto il resto resta dov'e'.
     *
     * La proposta arriva **come parametro** e non si rilegge dallo stato: il
     * pulsante che la conferma chiude prima il dialogo, e chiudere cancella la
     * proposta. Con la rilettura dallo stato questa funzione uscira' sempre
     * subito — ed e' esattamente quello che ha fatto per due giri di
     * segnalazioni, senza scrivere niente e senza dire niente.
     */
    fun confermaSostituzione(proposta: Sostituzione) = viewModelScope.launch {
        // Il viaggio della proposta, non quello aperto: la domanda puo' essere
        // arrivata dall'elenco, e la risposta va scritta dove si e' chiesto.
        val viaggio = proposta.bersaglio
        _stato.update { it.copy(sostituzione = null, inCorso = true, avviso = null) }

        val rinnovo = withContext(Dispatchers.IO) {
            val fatto = archivio.sostituisciTappe(viaggio.slug, proposta.punti, proposta.documento)
            archivio.annotaImport(
                "seguito di «${viaggio.nome}» (${viaggio.slug}): " +
                    "${fatto.sostituite.size} fuori, ${fatto.nuove.size} dentro, " +
                    "${fatto.tenute.size} restate" + (proposta.nomeFile?.let { ", da $it" } ?: ""),
            )
            fatto
        }
        val buchi = withContext(Dispatchers.IO) {
            GiorniDelViaggio.buchi(archivio.tappe(viaggio.slug), LocalDate.now()).size
        }

        aggiornaViaggio(viaggio)
        _stato.update {
            it.copy(
                inCorso = false,
                avviso = Avviso.TappeSostituite(
                    nuove = rinnovo.nuove.size,
                    sostituite = rinnovo.sostituite.size,
                    tenute = rinnovo.tenute.size,
                    buchi = buchi,
                ),
            )
        }
        rispecchia()

        // Le distanze su strada delle tappe nuove: come all'import, si chiedono
        // adesso — di solito si riscrive un itinerario dove c'e' campo, e da qui
        // in poi sono un dato locale.
        scorte?.let { if (it.aggiornaTratte(viaggio.slug)) aggiornaViaggio(viaggio) }
    }

    // --- la giornata --------------------------------------------------------

    fun checkin(tappa: Tappa) = operazione { slug ->
        archivio.checkin(slug, tappa, posizione = posizioni.attuale())
        // Misurato **dopo** il check-in: la tappa e' appena diventata fatta, e
        // quelle da spostare sono le altre. Non si sposta niente qui — si
        // propone, e chi decide e' l'utente.
        val oggi = LocalDate.now()
        Slittamenti.misura(tappa, archivio.tappe(slug), quando = oggi, oggi = oggi)
            ?.takeIf { it.daChiedere }
            ?.let { Avviso.FuoriProgramma(tappa, it) }
    }

    /**
     * Sposta di [giorni] le tappe che restano dopo [tappa].
     *
     * Lo si chiama solo dalla proposta che segue un check-in fuori programma: e'
     * una riscrittura di date, e non e' una cosa da fare senza che sia stata
     * chiesta.
     */
    fun slitta(tappa: Tappa, giorni: Long) = operazione { slug ->
        val quante = archivio.slittaTappe(slug, tappa, giorni)
        if (quante == 0) null else Avviso.ItinerarioSlittato(quante, giorni)
    }

    /**
     * Sposta l'itinerario **a mano**, da questa tappa in avanti, questa compresa.
     *
     * Serve dove la proposta automatica non arriva: un ritardo che si sa la sera
     * prima, o il rimedio a uno slittamento accettato per sbaglio — che prima non
     * aveva nessun gesto inverso, perche' [slitta] viveva solo dentro la proposta
     * che segue un check-in.
     */
    fun spostaDate(tappa: Tappa, giorni: Long) = operazione { slug ->
        val quante = archivio.slittaTappe(slug, tappa, giorni, compresa = true)
        if (quante == 0) Avviso.NienteDaSpostare else Avviso.ItinerarioSlittato(quante, giorni)
    }

    /**
     * Disfa un check-in dato per errore.
     *
     * Era l'unico gesto dell'app senza ritorno, e non per scelta: lo stato di una
     * tappa si cambiava solo con "salta/ripristina", che su una tappa fatta —
     * giustamente — non fa niente.
     */
    fun annullaCheckin(tappa: Tappa) = operazione { slug ->
        if (archivio.annullaCheckin(slug, tappa)) Avviso.CheckinAnnullato(tappa.nome) else null
    }

    fun alternaSalto(tappa: Tappa) = operazione { slug ->
        archivio.alternaSalto(slug, tappa)
        null
    }

    fun registraPosizione() = operazione { slug ->
        if (!posizioni.permessoConcesso()) return@operazione Avviso.PermessoPosizioneNegato
        val posizione = posizioni.attuale() ?: return@operazione Avviso.PosizioneAssente
        archivio.registraPosizione(slug, posizione)
        Avviso.PosizioneRegistrata
    }

    fun registraNota(testo: String) = operazione { slug ->
        // La posizione si prende senza attendere: una nota non deve stare
        // ferma venti secondi ad aspettare un fix satellitare.
        archivio.registraNota(slug, testo, posizioni.ultimaNota())
        Avviso.NotaRegistrata
    }

    /**
     * Il file dell'allegato di una voce, quando c'e'.
     *
     * Foto e scontrini stanno in due cartelle diverse, e il genere della voce
     * dice quale: e' l'unica cosa che l'interfaccia non puo' dedurre dal nome del
     * file. Restituisce un `File` anche se non esiste — chi lo mostra distingue
     * "sto caricando" da "non c'e' piu'", e sono due messaggi diversi.
     */
    fun allegato(voce: Voce): File? {
        val slug = _stato.value.aperto?.slug ?: return null
        val nome = voce.allegato ?: return null
        return when (voce.genere) {
            Genere.FOTO -> File(archivio.cartellaFoto(slug), nome)
            Genere.SPESA -> File(archivio.cartellaScontrini(slug), nome)
            else -> null
        }
    }

    // --- tornare su quello che si e' registrato -------------------------------

    /**
     * Cancella una voce di diario.
     *
     * Nessuna riga viene distrutta: si accoda una lapide, e l'originale resta nel
     * file per chi lo apre. E' il formato che lo permette, ed e' la ragione per
     * cui offrire questa funzione non fa paura.
     */
    fun cancellaVoce(voce: Voce) = operazione { slug ->
        val id = voce.id ?: return@operazione null
        if (archivio.cancellaVoce(slug, voce.genere, id)) Avviso.VoceCancellata else null
    }

    fun correggiNota(id: String, testo: String) = operazione { slug ->
        if (archivio.correggiNota(slug, id, testo)) Avviso.VoceCorretta else null
    }

    fun correggiDidascalia(id: String, didascalia: String?) = operazione { slug ->
        if (archivio.correggiDidascalia(slug, id, didascalia)) Avviso.VoceCorretta else null
    }

    fun correggiRifornimento(
        id: String,
        kmDaPieno: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        val fatto = archivio.correggiRifornimento(
            slug = slug, id = id, kmDaPieno = kmDaPieno, euro = euro, prezzoLitro = prezzoLitro,
            pieno = pieno, istante = istante,
        )
        if (fatto) Avviso.VoceCorretta else null
    }

    fun correggiSpesa(
        id: String,
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String?,
        valuta: String,
        cambio: Double?,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        val fatto = archivio.correggiSpesa(
            slug = slug, id = id, categoria = categoria, importo = importo,
            modalita = modalita, descrizione = descrizione, valuta = valuta,
            cambio = cambio, istante = istante,
        )
        if (fatto) Avviso.VoceCorretta else null
    }

    fun aggiungiTappa(nome: String, lat: Double, lon: Double, giorno: String?, primaDi: String?) =
        operazione { slug ->
            val tappa = archivio.aggiungiTappa(slug, nome, lat, lon, giorno, primaDi)
            // Una tappa in mezzo spezza una tratta in due: se c'e' campo si
            // richiedono, altrimenti quel tratto ripiega sulla linea d'aria.
            scorte?.aggiornaTratte(slug)
            Avviso.TappaAggiunta(tappa.nome)
        }

    /**
     * Prepara il file dove la fotocamera di sistema scrivera' lo scatto.
     *
     * Il nome si decide adesso, non dopo: porta l'ora e il nome della tappa
     * dove sei, e sono entrambi noti prima di scattare.
     */
    suspend fun preparaFoto(): File? {
        val slug = _stato.value.aperto?.slug ?: return null
        val posizione = posizioni.ultimaNota()
        return withContext(Dispatchers.IO) {
            // Il nome porta il toponimo quando la scorta ce l'ha: "Bolsena" e
            // non "Orvieto", che era solo l'ultimo check-in.
            val nome = NomeFoto.per(OffsetDateTime.now(), archivio.dove(slug, posizione))
            File(archivio.cartellaFoto(slug), nome)
        }
    }

    fun registraRifornimento(
        kmDaPieno: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        archivio.registraRifornimento(
            slug = slug,
            kmDaPieno = kmDaPieno,
            euro = euro,
            prezzoLitro = prezzoLitro,
            pieno = pieno,
            posizione = posizioni.ultimaNota(),
            istante = istante,
        )
        Avviso.RifornimentoRegistrato
    }

    // --- spese ---------------------------------------------------------------

    fun registraSpesa(
        categoria: Categoria,
        importo: Double,
        modalita: Modalita,
        descrizione: String?,
        valuta: String,
        cambio: Double?,
        scontrino: File?,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        archivio.registraSpesa(
            slug = slug,
            categoria = categoria,
            importo = importo,
            modalita = modalita,
            descrizione = descrizione,
            valuta = valuta,
            cambio = cambio,
            scontrino = scontrino?.name,
            posizione = posizioni.ultimaNota(),
            istante = istante,
        )
        Avviso.SpesaRegistrata
    }

    /** Il file dove la fotocamera scrivera' la foto dello scontrino. */
    suspend fun preparaScontrino(): File? {
        val slug = _stato.value.aperto?.slug ?: return null
        val posizione = posizioni.ultimaNota()
        return withContext(Dispatchers.IO) {
            val nome = NomeFoto.scontrino(OffsetDateTime.now(), archivio.dove(slug, posizione))
            File(archivio.cartellaScontrini(slug), nome)
        }
    }

    /** Uno scontrino fotografato e poi non salvato non resta nella cartella. */
    fun scartaScontrino(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { file.delete() }
    }

    // --- impostazioni e briefing ---------------------------------------------

    /**
     * Salva le impostazioni e riarma la sveglia del riepilogo.
     *
     * Vive fuori da [operazione] perche' sono impostazioni globali: si possono
     * cambiare anche senza un viaggio aperto.
     */
    fun salvaImpostazioni(nuove: Impostazioni) = viewModelScope.launch {
        withContext(Dispatchers.IO) { archivio.salvaImpostazioni(nuove) }
        _stato.update { it.copy(impostazioni = nuove, avviso = Avviso.ImpostazioniSalvate) }
        // La sveglia segue l'impostazione senza aspettare il prossimo avvio:
        // spegnere il riepilogo e vederlo arrivare stasera sarebbe assurdo.
        val prossima = riarma(nuove)
        val conSveglia = withContext(Dispatchers.IO) {
            archivio.annotaSveglia(prossima)
            archivio.impostazioni()
        }
        _stato.update { it.copy(impostazioni = conSveglia) }
        _stato.value.aperto?.let { aggiornaViaggio(it) }
    }

    /**
     * Riempie la scorta adesso, su richiesta.
     *
     * Serve quando si sa di stare per entrare in una zona senza campo: si
     * scarica prima invece di aspettare le 19:00. Le tratte si richiedono
     * insieme al meteo, perche' la finestra di rete e' la stessa.
     */
    fun aggiornaScorta() = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        val scorte = scorte ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }

        val meteo = scorte.aggiornaMeteo(viaggio.slug)
        // Le tratte si richiedono solo se mancano: non cambiano, e il server
        // pubblico di OSRM e' una cortesia, non un servizio da tempestare.
        val servono = withContext(Dispatchers.IO) { archivio.tratte(viaggio.slug).vuoto }
        val tratte = servono && scorte.aggiornaTratte(viaggio.slug)

        aggiornaViaggio(viaggio)
        _stato.update {
            it.copy(
                inCorso = false,
                avviso = if (meteo || tratte) Avviso.ScortaAggiornata else Avviso.ScortaNonAggiornata,
            )
        }
    }

    /**
     * Cerca i dintorni **di una tappa** e li salva.
     *
     * Il gesto sta sulla tappa e non nelle impostazioni perche' e' li' che uno
     * si chiede cosa c'e' intorno, e perche' una ricerca su un punto e' una
     * richiesta che Overpass serve in un secondo. La scorta si riempie cosi',
     * una tappa per volta: quello che hai cercato resta cercato, e si rilegge
     * senza rete per il resto del viaggio.
     */
    fun cercaDintorniDi(tappa: Tappa) = cercaDintorni { Coordinate(tappa.lat, tappa.lon) }

    /** Cerca i dintorni di dove sei: l'ultima posizione, o la tappa corrente. */
    fun cercaDintorniQui() = cercaDintorni { slug -> archivio.dovePunto(slug) }

    private fun cercaDintorni(dove: suspend (String) -> Coordinate?) = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        val scorte = scorte ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }

        val punto = withContext(Dispatchers.IO) { dove(viaggio.slug) }
        val esito = scorte.dintorniAttorno(viaggio.slug, punto)
        aggiornaViaggio(viaggio)
        _stato.update {
            it.copy(
                inCorso = false,
                avviso = when (esito) {
                    is EsitoDintorni.Riuscito -> Avviso.DintorniAggiornati(esito.poi, esito.luoghi)
                    // Un fallimento si dice per quello che e': su questa
                    // richiesta non c'e' ripiego, e "non aggiornato" lascerebbe
                    // l'utente davanti a due schermate vuote senza un perche'.
                    else -> Avviso.DintorniFalliti(esito)
                },
            )
        }
    }

    // --- la cartella d'archivio ----------------------------------------------

    /**
     * Registra la cartella scelta e ci copia subito tutto l'archivio.
     *
     * La prima passata e' immediata e non differita: l'utente ha appena scelto
     * una cartella e si aspetta di trovarci i file, non di scoprirli fra dieci
     * minuti.
     */
    /**
     * Assegna la cartella, e **prima di tutto legge quello che c'e' dentro**.
     *
     * L'ordine e' l'unica cosa che conta qui. Fino alla fase 12 questa funzione
     * esportava e basta: dopo una reinstallazione l'app ripartiva vuota, e il
     * primo specchio sovrascriveva le impostazioni nella cartella con quelle di
     * riposo. Adesso si fonde e poi si esporta, cosi' quello che c'e' nella
     * cartella entra invece di essere seppellito.
     */
    fun scegliCartella(uri: String, nome: String?) = viewModelScope.launch {
        val nuove = _stato.value.impostazioni.copy(cartellaSpecchio = uri)
        withContext(Dispatchers.IO) { archivio.salvaImpostazioni(nuove) }
        _stato.update {
            it.copy(impostazioni = nuove, avviso = Avviso.SpecchioScelto(nome ?: uri))
        }
        sincronizza()
    }

    /**
     * Fonde la cartella nell'archivio, poi riporta fuori il risultato.
     *
     * I due versi in fila, e in quest'ordine: leggere prima significa che una
     * riga che sta solo nella cartella entra; esportare dopo significa che la
     * cartella finisce per contenere l'unione delle due. Fatta una volta, il
     * verso torna quello di sempre — dentro e' l'autorita', fuori e' la copia.
     */
    fun sincronizza() = viewModelScope.launch {
        val fondi = fondiDallaCartella
        if (fondi == null) {
            esporta()
            return@launch
        }

        _stato.update { it.copy(inCorso = true, avviso = null) }
        val fusione = withContext(Dispatchers.IO) { fondi() }
        // L'archivio puo' essere cambiato sotto: viaggi nuovi, righe nuove,
        // impostazioni adottate. Si ricarica tutto prima di dire com'e' andata.
        ricarica()

        val copiati = esportaTutto?.let { withContext(Dispatchers.IO) { it() } }

        // La data si scrive **dopo**, e solo se e' andata: una sincronizzazione
        // fallita che lascia scritto "sincronizzato adesso" e' peggio di nessuna
        // data. E si scrive dopo la fusione, non prima, perche' la fusione
        // giudica "intatte" le impostazioni e questo campo le sporcherebbe.
        if (fusione != null) {
            val quando = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val aggiornate = archivio.impostazioni().copy(sincronizzatoIl = quando)
            withContext(Dispatchers.IO) { archivio.salvaImpostazioni(aggiornate) }
            _stato.update { it.copy(impostazioni = aggiornate) }
        }

        _stato.update {
            it.copy(
                inCorso = false,
                avviso = when {
                    fusione == null -> Avviso.SpecchioFallito
                    fusione.qualcosa -> Avviso.CartellaFusa(fusione)
                    copiati == null -> Avviso.SpecchioFallito
                    else -> Avviso.SpecchioFatto(copiati)
                },
            )
        }
    }

    /** Smette di rispecchiare. I file gia' copiati restano dove sono. */
    fun spegniCartella() = viewModelScope.launch {
        val nuove = _stato.value.impostazioni.copy(cartellaSpecchio = null)
        withContext(Dispatchers.IO) { archivio.salvaImpostazioni(nuove) }
        _stato.update { it.copy(impostazioni = nuove, avviso = Avviso.SpecchioSpento) }
    }

    /**
     * Copia adesso tutto l'archivio nella cartella scelta.
     *
     * Serve alla prima volta — l'archivio esiste da prima della cartella — e
     * quando si vuole essere certi che fuori ci sia tutto prima di disinstallare
     * o di cambiare telefono.
     */
    fun esporta() = viewModelScope.launch {
        val esporta = esportaTutto ?: return@launch
        _stato.update { it.copy(inCorso = true) }
        val copiati = withContext(Dispatchers.IO) { esporta() }
        _stato.update {
            it.copy(
                inCorso = false,
                avviso = if (copiati == null) Avviso.SpecchioFallito else Avviso.SpecchioFatto(copiati),
            )
        }
    }


    // --- cercare un indirizzo -------------------------------------------------

    /**
     * Le coordinate di un posto dal suo nome.
     *
     * Non passa da [operazione] e non tocca lo stato: e' una domanda, non una
     * registrazione, e la risposta la usa il dialogo che l'ha chiesta.
     */
    suspend fun cercaIndirizzo(testo: String): RicercaIndirizzo? =
        geocodifica?.cerca(testo, _stato.value.aperto?.slug)

    // --- il modello -----------------------------------------------------------

    /**
     * Se c'e' **almeno una** chiave configurata, qualunque sia il fornitore.
     *
     * Prima guardava il principale e il primo diverso da lui: con due fornitori
     * era l'insieme di tutti, con tre lasciava fuori il terzo — e chi aveva
     * configurato solo quello si vedeva l'app dire che l'AI non c'era.
     */
    fun aiConfigurata(): Boolean =
        Modello.entries.any { assistente?.configurato(it) == true }

    fun chiaviDisponibili(): Boolean = assistente?.chiaviDisponibili() == true

    fun codaChiave(modello: Modello): String? = assistente?.coda(modello)

    fun salvaChiave(modello: Modello, chiave: String?) {
        assistente?.salvaChiave(modello, chiave)
        _stato.update { it.copy(avviso = Avviso.ImpostazioniSalvate) }
    }

    /**
     * Chiede al fornitore quali modelli vede questa chiave, e **scrive l'esito**.
     *
     * La scrittura non e' un extra: e' il motivo per cui la funzione serve. Chi
     * usa l'app ha un telefono e nient'altro, e la domanda «quale identificativo
     * devo scrivere» si presenta di sera, in mezzo al nulla, dopo un 404 che
     * sembrava un problema di chiave. L'elenco finisce in `impostazioni.json`
     * accanto alle altre tracce, quindi si rilegge anche dopo aver chiuso tutto —
     * e nell'elenco non c'e' niente di riservato, solo nomi di modelli.
     */
    fun verificaModelli(modello: Modello) = viewModelScope.launch {
        val assistente = assistente ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }
        val esito = assistente.modelliVisibili(modello)
        val impostazioni = withContext(Dispatchers.IO) {
            archivio.annotaModelli(esito.riassunto())
            archivio.impostazioni()
        }
        _stato.update {
            it.copy(
                inCorso = false,
                impostazioni = impostazioni,
                avviso = Avviso.ModelliVerificati(esito),
            )
        }
    }

    /**
     * Chiede al modello, e **salva la risposta su file**.
     *
     * Il dossier e' il pezzo che rende utile una funzione altrimenti solo
     * online: una risposta letta e chiusa e' persa, scritta su file si ritrova
     * arrivando sul posto tre giorni dopo, senza campo.
     */
    fun chiedi(domanda: String) = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        val stato = _stato.value
        val posizione = stato.quiVicino

        val contesto = withContext(Dispatchers.IO) {
            Esplora.contesto(
                dove = archivio.doveDetto(viaggio.slug, posizione?.let { Posizione(it.lat, it.lon) }),
                posizione = posizione,
                oggi = LocalDate.now(),
                meteo = posizione?.let { qui ->
                    archivio.meteo(viaggio.slug)?.per(qui.lat, qui.lon, LocalDate.now())
                },
                vicini = stato.vicini(null).take(Esplora.VICINI_NEL_CONTESTO),
                prossima = stato.prossima,
            )
        }

        interroga(domanda, contesto, posizione, tappa = null)
    }

    /**
     * Chiede al modello **di una tappa**, che non e' dove sei.
     *
     * Il contesto lo compone la scheda: il giorno previsto, la previsione per
     * quel giorno, i dintorni di **quel** posto. Chiedere di Bolsena stando a
     * Orvieto e' il caso normale, quindi la risposta si attribuisce a Bolsena e
     * non a dove eri: e' con quel nome che la si ritrova nella sua scheda, tre
     * giorni dopo, senza campo.
     */
    fun chiediDiTappa(tappa: Tappa) = viewModelScope.launch {
        if (_stato.value.aperto == null) return@launch
        val scheda = _stato.value.scheda(tappa)

        val contesto = Esplora.contestoDiTappa(
            tappa = tappa,
            giorno = scheda.giorno,
            oggi = LocalDate.now(),
            previsione = scheda.previsione,
            vicini = Dintorni.vicini(_stato.value.poi, tappa.lat, tappa.lon)
                .take(Esplora.VICINI_NEL_CONTESTO),
            da = scheda.da,
        )

        interroga(Esplora.DOMANDA_TAPPA, contesto, posizione = null, tappa = tappa.nome)
    }

    /**
     * Il pezzo comune alle due domande: manda, salva su file, aggiorna.
     *
     * **Il salvataggio non e' facoltativo.** Una risposta letta e chiusa e'
     * persa; e' il dossier a rendere utile una funzione altrimenti solo online,
     * quindi sta qui dentro e non nel chiamante, dove si potrebbe dimenticare.
     */
    private suspend fun interroga(
        domanda: String,
        contesto: String,
        posizione: Coordinate?,
        tappa: String?,
    ) {
        val viaggio = _stato.value.aperto ?: return
        val assistente = assistente ?: return
        val impostazioni = _stato.value.impostazioni

        _stato.update { it.copy(inCorso = true, avviso = null) }

        val esito = assistente.chiedi(
            sistema = impostazioni.prompt(),
            domanda = Esplora.domanda(contesto, domanda),
            impostazioni = impostazioni,
        )

        when (esito) {
            is EsitoAi.Risposta -> {
                withContext(Dispatchers.IO) {
                    archivio.salvaDossier(
                        slug = viaggio.slug,
                        domanda = domanda,
                        contesto = contesto,
                        risposta = esito.risposta,
                        posizione = posizione?.let { Posizione(it.lat, it.lon) },
                        tappa = tappa,
                    )
                    annotaRisposta(esito, impostazioni)
                }
                aggiornaViaggio(viaggio)
                _stato.update {
                    it.copy(
                        inCorso = false,
                        avviso = if (esito.diRiserva) Avviso.AiDiRiserva else null,
                    )
                }
                rispecchia()
            }

            is EsitoAi.Guaio -> _stato.update {
                it.copy(inCorso = false, avviso = Avviso.AiFallita(esito.guaio))
            }
        }
    }

    /**
     * Scrive com'e' andata l'ultima domanda a un modello.
     *
     * **Il conteggio delle fonti e i nomi dei campi stanno nella stessa riga**, e
     * insieme rispondono alla domanda che da fuori non si puo' rispondere: se le
     * fonti mancano perche' il modello non ha cercato, oppure perche' le ha messe
     * in un campo che l'app non guarda. Sono nomi e numeri: niente del contenuto,
     * niente della chiave.
     */
    private fun annotaRisposta(esito: EsitoAi.Risposta, impostazioni: Impostazioni) {
        val risposta = esito.risposta
        archivio.annotaAi(
            buildString {
                append(risposta.modello.nome)
                append(" ")
                append(impostazioni.modello(risposta.modello))
                append(": ")
                append(risposta.testo.length)
                append(" caratteri, ")
                append(risposta.fonti.size)
                append(" fonti")
                esito.impronta?.let { append(" — ").append(it) }
            },
        )
    }

    /**
     * Manda il riepilogo **adesso**, come lo manderebbe alle 19:00.
     *
     * Non e' l'anteprima: quella compone il testo e lo mostra dentro l'app, e
     * quindi non prova niente della catena che conta — canale, permesso,
     * consegna. Questo pulsante percorre la strada vera, e scrive l'esito nello
     * stesso posto dove lo scrive la sveglia. Se la notifica arriva toccandolo ma
     * non arriva la sera, il guasto e' nella sveglia (e su HyperOS si sa dove
     * andare a guardare); se non arriva nemmeno cosi', e' il permesso.
     */
    fun provaBriefing() = viewModelScope.launch {
        val briefing = briefingDiStasera()
        val esito = when {
            briefing == null -> EsitoBriefing.SenzaViaggio
            briefing.vuoto -> EsitoBriefing.NienteDaDire
            !manda(briefing) -> EsitoBriefing.SenzaPermesso
            else -> EsitoBriefing.Mandato(TestoBriefing.titolo(briefing))
        }
        val impostazioni = withContext(Dispatchers.IO) {
            archivio.annotaBriefing(esito.riassunto())
            archivio.impostazioni()
        }
        _stato.update {
            it.copy(impostazioni = impostazioni, avviso = Avviso.BriefingProvato(esito))
        }
    }

    /** Il testo di un dossier salvato. */
    suspend fun testoDossier(nome: String): String? {
        val slug = _stato.value.aperto?.slug ?: return null
        return withContext(Dispatchers.IO) { archivio.testoDossier(slug, nome) }
    }

    /**
     * Riscrive in prosa la giornata di diario di un giorno.
     *
     * **Gli eventi restano nei CSV**: il modello riscrive solo la sezione di
     * `diario.md`, che e' una vista. Se la prosa non piace, "rigenera il diario"
     * la riporta a cronaca — e' il motivo per cui quella funzione esiste.
     *
     * Niente ricerca web: la cronaca e' tutta nel prompt, e lasciarlo cercare
     * sarebbe un invito ad aggiungere dettagli che quel giorno non c'erano.
     */
    fun riscriviGiornata(giorno: LocalDate) = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        val assistente = assistente ?: return@launch
        val stato = _stato.value

        val cronaca = withContext(Dispatchers.IO) {
            val voci = archivio.voci(viaggio.slug)
            Cronaca.sezione(giorno, VociDelGiorno.delGiorno(voci, giorno))
        }

        _stato.update { it.copy(inCorso = true, avviso = null) }

        val esito = assistente.chiedi(
            sistema = Esplora.PROMPT_DIARIO,
            domanda = cronaca,
            impostazioni = stato.impostazioni,
            conRicerca = false,
        )

        when (esito) {
            is EsitoAi.Risposta -> {
                withContext(Dispatchers.IO) {
                    archivio.scriviProsa(viaggio.slug, giorno, esito.risposta.testo)
                    annotaRisposta(esito, stato.impostazioni)
                }
                aggiornaViaggio(viaggio)
                _stato.update { it.copy(inCorso = false, avviso = Avviso.DiarioRiscritto) }
                rispecchia()
            }

            is EsitoAi.Guaio -> _stato.update {
                it.copy(inCorso = false, avviso = Avviso.AiFallita(esito.guaio))
            }
        }
    }

    /** Riporta il diario a cronaca, cancellando la prosa. */
    fun rigeneraDiario() = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        withContext(Dispatchers.IO) { archivio.rigeneraDiario(viaggio.slug) }
        aggiornaViaggio(viaggio)
        _stato.update { it.copy(avviso = Avviso.DiarioRiscritto) }
        rispecchia()
    }

    /**
     * Il riepilogo che arriverebbe stasera, calcolato adesso.
     *
     * Serve a verificarlo senza aspettare le 19:00 — che e' l'unico modo
     * sensato di provare una funzione che scatta una volta al giorno.
     */
    suspend fun briefingDiStasera(): Briefing? = withContext(Dispatchers.IO) {
        _stato.value.aperto?.let { archivio.briefing(it.slug) } ?: archivio.briefingCorrente()
    }



    fun registraFoto(file: File, didascalia: String?) = operazione { slug ->
        archivio.registraFoto(slug, file.name, didascalia, posizioni.ultimaNota())
        Avviso.FotoRegistrata
    }

    /** Uno scatto annullato non lascia un file vuoto nella cartella. */
    fun scartaFoto(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { file.delete() }
    }

    fun permessoPosizioneNegato() = _stato.update { it.copy(avviso = Avviso.PermessoPosizioneNegato) }

    fun avvisoVisto() = _stato.update { it.copy(avviso = null) }

    /**
     * Il giro comune a ogni registrazione: segna il lavoro in corso, scrive su
     * un thread di I/O, ricarica il viaggio, mostra l'avviso.
     */
    private fun operazione(corpo: suspend (String) -> Avviso?) = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }
        // Su un thread di I/O: sono scritture su file, e il thread principale
        // non deve aspettare un fsync.
        val avviso = withContext(Dispatchers.IO) {
            runCatching { corpo(viaggio.slug) }.getOrNull()
        }
        aggiornaViaggio(viaggio)
        _stato.update { it.copy(inCorso = false, avviso = avviso) }
        rispecchia()
    }

    private data class Esito(val viaggio: Viaggio? = null, val avviso: Avviso)

    /** La posizione, quando serve mostrarla senza registrarla. */
    suspend fun posizioneAttuale(): Posizione? = posizioni.attuale()
}
