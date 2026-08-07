package it.myacamperlife.app.ui.viaggi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.Impostazioni
import it.myacamperlife.app.archivio.Posizione
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.archivio.Viaggio
import it.myacamperlife.app.dominio.Autonomia
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Consumi
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Conto
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.NomeFoto
import it.myacamperlife.app.dominio.Percorso
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.Tratte
import it.myacamperlife.app.dominio.Voce
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
        val autonomia: Autonomia? = null,
        val tratte: Tratte = Tratte(),
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
        data object FotoRegistrata : Avviso
        data object RifornimentoRegistrato : Avviso
        data object SpesaRegistrata : Avviso
        data object ImpostazioniSalvate : Avviso
        data object ScortaAggiornata : Avviso
        data object ScortaNonAggiornata : Avviso
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
                autonomia = StimaAutonomia.calcola(
                    kmConUnPieno = impostazioni.kmConUnPieno,
                    rifornimenti = rifornimenti,
                    punti = archivio.punti(slug),
                ),
                impostazioni = impostazioni,
                tratte = archivio.tratte(slug),
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
                autonomia = dati.autonomia,
                impostazioni = dati.impostazioni,
                tratte = dati.tratte,
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
        val autonomia: Autonomia?,
        val impostazioni: Impostazioni,
        val tratte: Tratte,
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
            if (scorte?.aggiornaTratte(viaggio.slug) == true) aggiornaViaggio(viaggio)
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
        return withContext(Dispatchers.IO) {
            val nome = NomeFoto.per(OffsetDateTime.now(), archivio.luogo(slug))
            File(archivio.cartellaFoto(slug), nome)
        }
    }

    fun registraRifornimento(km: Int, litri: Double, euro: Double?, pieno: Boolean) =
        operazione { slug ->
            archivio.registraRifornimento(
                slug = slug,
                km = km,
                litri = litri,
                euro = euro,
                pieno = pieno,
                posizione = posizioni.ultimaNota(),
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
        )
        Avviso.SpesaRegistrata
    }

    /** Il file dove la fotocamera scrivera' la foto dello scontrino. */
    suspend fun preparaScontrino(): File? {
        val slug = _stato.value.aperto?.slug ?: return null
        return withContext(Dispatchers.IO) {
            val nome = NomeFoto.scontrino(OffsetDateTime.now(), archivio.luogo(slug))
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
    }

    private data class Esito(val viaggio: Viaggio? = null, val avviso: Avviso)

    /** La posizione, quando serve mostrarla senza registrarla. */
    suspend fun posizioneAttuale(): Posizione? = posizioni.attuale()
}
