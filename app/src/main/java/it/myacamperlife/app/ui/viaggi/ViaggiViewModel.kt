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
import it.myacamperlife.app.dominio.Esplora
import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Indirizzo
import it.myacamperlife.app.dominio.Consumi
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Conto
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Modello
import it.myacamperlife.app.dominio.NomeFoto
import it.myacamperlife.app.dominio.Percorso
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.PoiVicino
import it.myacamperlife.app.dominio.Rifornimento
import it.myacamperlife.app.dominio.Schede
import it.myacamperlife.app.dominio.SchedaTappa
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.Tratte
import it.myacamperlife.app.dominio.Voce
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.rete.Assistente
import it.myacamperlife.app.rete.EsitoAi
import it.myacamperlife.app.rete.EsitoDintorni
import it.myacamperlife.app.rete.Geocodifica
import it.myacamperlife.app.rete.RicercaIndirizzo
import it.myacamperlife.app.rete.Scorte
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
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
    private val riarma: (Impostazioni) -> Unit = {},
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
        val ultimoKm: Int? = null,
        val inCorso: Boolean = false,
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

    /** Un messaggio da mostrare una volta e poi scartare. */
    sealed interface Avviso {
        data class ImportRiuscito(val tappe: Int, val scartate: Int) : Avviso
        data class ImportFallito(val motivo: Itinerario.Motivo?) : Avviso
        data object PosizioneAssente : Avviso
        data object PosizioneRegistrata : Avviso
        data object PermessoPosizioneNegato : Avviso
        data class TappaAggiunta(val nome: String) : Avviso
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
                meteo = archivio.meteo(slug),
                dossier = archivio.dossier(slug),
                quiVicino = archivio.dovePunto(slug),
                ultimoKm = Consumi.ultimoChilometraggio(rifornimenti),
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
                meteo = dati.meteo,
                dossier = dati.dossier,
                quiVicino = dati.quiVicino,
                ultimoKm = dati.ultimoKm,
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
        val meteo: Meteo?,
        val dossier: List<Dossier>,
        val quiVicino: Coordinate?,
        val ultimoKm: Int?,
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

    fun importa(uri: Uri) = viewModelScope.launch {
        _stato.update { it.copy(caricamento = true, avviso = null) }

        val esito = withContext(Dispatchers.IO) {
            val documento = documenti.leggi(uri)
                ?: return@withContext Esito(avviso = Avviso.ImportFallito(null))

            when (val letto = Itinerario.leggi(documento.testo)) {
                is Itinerario.Esito.Fallito -> Esito(avviso = Avviso.ImportFallito(letto.motivo))
                is Itinerario.Esito.Riuscito -> {
                    val nome = letto.nome
                        ?: documento.nome?.substringBeforeLast('.')?.trim()?.takeUnless { it.isEmpty() }
                        ?: "Viaggio senza nome"
                    archivio.prepara()
                    val viaggio = archivio.creaViaggio(nome, letto.tappe, documento.nome)
                    Esito(viaggio, Avviso.ImportRiuscito(letto.tappe.size, letto.scartati))
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
            // All'import il fallimento non si mostra: l'avviso dell'import
            // appena riuscito e' piu' importante, e i dintorni si riscaricano
            // con un pulsante che invece lo dice.
            val dintorni = scorte.aggiornaDintorni(viaggio.slug) is EsitoDintorni.Riuscito
            if (tratte || dintorni) aggiornaViaggio(viaggio)
        }
    }

    // --- la giornata --------------------------------------------------------

    fun checkin(tappa: Tappa) = operazione { slug ->
        archivio.checkin(slug, tappa, posizione = posizioni.attuale())
        null
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
        km: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        val fatto = archivio.correggiRifornimento(
            slug = slug, id = id, km = km, euro = euro, prezzoLitro = prezzoLitro,
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
        km: Int,
        euro: Double,
        prezzoLitro: Double,
        pieno: Boolean,
        istante: OffsetDateTime,
    ) = operazione { slug ->
        archivio.registraRifornimento(
            slug = slug,
            km = km,
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
        riarma(nuove)
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
     * Riscarica i dintorni: punti di interesse e toponimi.
     *
     * A parte dalla scorta generale perche' e' la richiesta piu' pesante che
     * l'app fa, e perche' ha senso rifarla quando l'itinerario cambia — non
     * ogni volta che si aggiorna il meteo.
     */
    fun aggiornaDintorni() = viewModelScope.launch {
        val viaggio = _stato.value.aperto ?: return@launch
        val scorte = scorte ?: return@launch
        _stato.update { it.copy(inCorso = true, avviso = null) }

        val esito = scorte.aggiornaDintorni(viaggio.slug)
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

    fun aiConfigurata(): Boolean =
        assistente?.configurato(_stato.value.impostazioni.modelloPrincipale) == true ||
            assistente?.configurato(
                Modello.entries.first { it != _stato.value.impostazioni.modelloPrincipale },
            ) == true

    fun chiaviDisponibili(): Boolean = assistente?.chiaviDisponibili() == true

    fun codaChiave(modello: Modello): String? = assistente?.coda(modello)

    fun salvaChiave(modello: Modello, chiave: String?) {
        assistente?.salvaChiave(modello, chiave)
        _stato.update { it.copy(avviso = Avviso.ImpostazioniSalvate) }
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
