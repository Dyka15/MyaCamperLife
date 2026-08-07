package it.myacamperlife.app.ui.viaggi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.Impostazioni
import it.myacamperlife.app.archivio.Posizione
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.archivio.Scontrino
import it.myacamperlife.app.archivio.Viaggio
import it.myacamperlife.app.dominio.Autonomia
import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Consumi
import it.myacamperlife.app.dominio.Consumo
import it.myacamperlife.app.dominio.Conto
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.NomeFoto
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Spese
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Tappa
import it.myacamperlife.app.dominio.Tappe
import it.myacamperlife.app.dominio.Voce
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
    private val scontrini: Scontrino,
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
        val kmConUnPieno: Int? = null,
        /** L'ultimo contachilometri registrato: precompila la form. */
        val ultimoKm: Int? = null,
        val inCorso: Boolean = false,
        val avviso: Avviso? = null,
    ) {
        val corrente: Tappa? get() = Tappe.corrente(tappe)
        val prossima: Tappa? get() = Tappe.prossima(tappe)
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
        data object ScontrinoIlleggibile : Avviso
        data object ImpostazioniSalvate : Avviso
    }

    private val _stato = MutableStateFlow(Stato())
    val stato: StateFlow<Stato> = _stato.asStateFlow()

    init {
        ricarica()
    }

    // --- viaggi -------------------------------------------------------------

    private fun ricarica(apri: Viaggio? = null) = viewModelScope.launch {
        val elenco = withContext(Dispatchers.IO) {
            archivio.prepara()
            archivio.viaggi()
        }
        val daAprire = apri
            ?: _stato.value.aperto?.let { aperto -> elenco.find { it.slug == aperto.slug } }
        _stato.update { it.copy(caricamento = false, viaggi = elenco, aperto = daAprire) }
        daAprire?.let { aggiornaViaggio(it) }
    }

    private suspend fun aggiornaViaggio(viaggio: Viaggio) {
        val dati = withContext(Dispatchers.IO) {
            val slug = viaggio.slug
            val rifornimenti = archivio.rifornimenti(slug)
            val kmConUnPieno = archivio.impostazioni().kmConUnPieno
            DatiViaggio(
                tappe = archivio.tappe(slug),
                voci = archivio.voci(slug),
                diario = archivio.diario(slug).testo(),
                consumo = Consumi.calcola(rifornimenti),
                conto = archivio.conto(slug),
                spese = archivio.spese(slug),
                autonomia = StimaAutonomia.calcola(
                    kmConUnPieno = kmConUnPieno,
                    rifornimenti = rifornimenti,
                    punti = archivio.punti(slug),
                ),
                kmConUnPieno = kmConUnPieno,
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
                kmConUnPieno = dati.kmConUnPieno,
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
        val kmConUnPieno: Int?,
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

    /**
     * Prova a leggere l'importo dalla foto dello scontrino.
     *
     * Il risultato e' una **proposta**: finisce nel campo dell'importo, dove si
     * corregge. Se la lettura non trova niente lo dice, invece di lasciare il
     * campo vuoto senza spiegazioni.
     */
    suspend fun leggiScontrino(file: File): Double? {
        val importo = scontrini.importo(Uri.fromFile(file))
        if (importo == null) _stato.update { it.copy(avviso = Avviso.ScontrinoIlleggibile) }
        return importo
    }

    /** Uno scontrino fotografato e poi non salvato non resta nella cartella. */
    fun scartaScontrino(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { file.delete() }
    }

    /**
     * Salva i km con un pieno. Vive fuori da [operazione] perche' e' una
     * impostazione globale: si puo' cambiare anche senza un viaggio aperto.
     */
    fun salvaKmConUnPieno(km: Int?) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            archivio.salvaImpostazioni(archivio.impostazioni().copy(kmConUnPieno = km))
        }
        _stato.update { it.copy(kmConUnPieno = km, avviso = Avviso.ImpostazioniSalvate) }
        _stato.value.aperto?.let { aggiornaViaggio(it) }
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
