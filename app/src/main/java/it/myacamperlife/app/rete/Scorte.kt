package it.myacamperlife.app.rete

import android.content.Context
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.dominio.Coordinate
import it.myacamperlife.app.dominio.Dintorno
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Overpass
import it.myacamperlife.app.dominio.RispostaMeteo
import it.myacamperlife.app.dominio.RispostaOsrm
import it.myacamperlife.app.dominio.Tratta
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Riempie la scorta: le due cose che si prendono dalla rete in anticipo.
 *
 * **Nessuna di queste due chiamate puo' far fallire qualcos'altro.** Il meteo
 * si scarica prima del riepilogo serale, e se non arriva il riepilogo esce
 * comunque con le tappe — che e' gia' meta' del suo valore. Le tratte si
 * chiedono all'import dell'itinerario, e se non arrivano le distanze restano in
 * linea d'aria, dichiarate come tali.
 *
 * Ogni funzione dice solo se ha aggiornato qualcosa. Non ci sono errori da
 * mostrare: "non c'era campo" non e' un'informazione su cui l'utente possa
 * fare niente di diverso da quello che sta gia' facendo.
 */
class Scorte(private val context: Context, private val archivio: Archivio) {

    /**
     * Scarica le previsioni per le tappe dei prossimi giorni.
     *
     * Una richiesta sola per tutte le tappe: Open-Meteo accetta le coordinate
     * in fila. In una finestra di campo incerto e' la differenza fra avere il
     * meteo e non averlo.
     */
    suspend fun aggiornaMeteo(
        slug: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext false

        val punti = archivio.puntiMeteo(slug, adesso.toLocalDate())
        if (punti.isEmpty()) return@withContext false

        val corpo = Rete.prendi(RispostaMeteo.indirizzo(punti)) ?: return@withContext false
        val luoghi = RispostaMeteo.leggi(corpo, punti)
        if (luoghi.isEmpty()) return@withContext false

        archivio.salvaMeteo(
            slug = slug,
            meteo = Meteo(
                scaricatoIl = adesso.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                luoghi = luoghi,
            ),
        )
        true
    }

    /**
     * Precalcola le distanze su strada fra tappe consecutive.
     *
     * Si chiama quando si importa un itinerario e quando si aggiunge una tappa:
     * sono i due momenti in cui la catena cambia, e sono anche i due momenti in
     * cui verosimilmente c'e' ancora campo, perche' li fai a casa.
     *
     * L'itinerario lungo si spezza in richieste piu' corte: il server pubblico
     * ha un tetto, e mezze tratte sono meglio di nessuna — quelle che mancano
     * ripiegano da sole sulla linea d'aria.
     */
    suspend fun aggiornaTratte(
        slug: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext false

        val punti = archivio.puntiTratte(slug)
        if (punti.size < 2) return@withContext false

        val trovate = mutableListOf<Tratta>()
        // Le fette si sovrappongono di un punto: l'ultima tappa di una e' la
        // prima della successiva, altrimenti la tratta di mezzo sparirebbe.
        punti.windowed(
            size = RispostaOsrm.PUNTI_PER_RICHIESTA,
            step = RispostaOsrm.PUNTI_PER_RICHIESTA - 1,
            partialWindows = true,
        ).forEach { fetta ->
            if (fetta.size < 2) return@forEach
            val corpo = Rete.prendi(RispostaOsrm.indirizzo(fetta)) ?: return@forEach
            trovate += RispostaOsrm.leggi(corpo, fetta)
        }

        if (trovate.isEmpty()) return@withContext false
        archivio.salvaTratte(slug, trovate, adesso)
        true
    }

    /**
     * Cerca i dintorni **di un punto** e salva quello che trova.
     *
     * Un cerchio di dieci chilometri, tutte le categorie in una richiesta, e la
     * scrittura subito dopo. Niente scorta d'anticipo su tutto l'itinerario:
     * quella era una query da migliaia di chilometri quadrati su un server di
     * cortesia, e il modo in cui Overpass dice "hai chiesto troppo" e' rispondere
     * 200 con un `remark` e zero elementi — cioe' travestirsi da "in quella zona
     * non c'e' niente". Quattro fasi di dintorni vuoti sono venute da la'.
     *
     * Ora la scorta si riempie con le ricerche che si fanno: una tappa che apri
     * e cerchi resta cercata, e i suoi punti si rileggono offline per sempre.
     * Le righe si accodano, quindi cercare due volte lo stesso posto non
     * cancella niente — i doppioni li riconosce l'identificativo OSM in
     * lettura.
     *
     * @param punto dove cercare. `null` — nessuna tappa, nessuna posizione
     *   registrata — e' [EsitoDintorni.SenzaTappe], non un errore muto.
     */
    suspend fun dintorniAttorno(
        slug: String,
        punto: Coordinate?,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): EsitoDintorni = withContext(Dispatchers.IO) {
        val tentativo = cerca(punto)
        // L'esito si annota **sempre**, riuscito o no, e con dentro quale server
        // ha risposto: un messaggio in una notifica che scorre non si rilegge, e
        // la domanda "perche' non carica niente" arriva il giorno dopo, senza
        // rete e senza il messaggio.
        archivio.annotaDintorni(tentativo.scritto(), adesso)
        val esito = tentativo.esito
        if (esito is EsitoDintorni.Riuscito) archivio.salvaDintorni(slug, esito.dintorno, adesso)
        esito
    }

    /** Un esito e il server che l'ha prodotto, per poterlo scrivere. */
    private data class Tentativo(val esito: EsitoDintorni, val dove: String? = null) {
        fun scritto(): String =
            esito.riassunto() + (dove?.let { " [$it]" } ?: "")
    }

    /**
     * Prova i server di Overpass in fila, e si ferma al primo che risponde.
     *
     * **Il secondo esiste per un guasto vero**: un 504 con
     * `Dispatcher_Client::request_read_and_idx::timeout`, cioe' un server
     * congestionato, non una query da correggere. Contro quello l'unico rimedio
     * e' chiedere a un altro.
     *
     * Un `Vuoto` **non** fa passare al server dopo: "qui non c'e' niente" e' una
     * risposta, e ripeterla su tre server sarebbe strapazzarli per confermare
     * quello che il primo ha gia' detto. Si insiste solo su chi non ha risposto.
     */
    private suspend fun cerca(punto: Coordinate?): Tentativo {
        if (!Rete.disponibile(context)) return Tentativo(EsitoDintorni.SenzaRete)
        if (punto == null) return Tentativo(EsitoDintorni.SenzaTappe)

        val corpo = Overpass.corpoModulo(Overpass.query(punto))
        var ultimo = Tentativo(EsitoDintorni.SenzaRete)

        Overpass.SERVIZI.forEach { servizio ->
            val dove = Overpass.nomeServizio(servizio)
            val tentativo = Tentativo(interroga(servizio, corpo), dove)
            when (tentativo.esito) {
                // Risposte, non guasti: si smette di chiedere.
                is EsitoDintorni.Riuscito, EsitoDintorni.Vuoto -> return tentativo
                // Guasti: si tiene da parte e si prova il prossimo. L'ultimo
                // vince, perche' e' quello dell'ultimo server disponibile.
                else -> ultimo = tentativo
            }
        }
        return ultimo
    }

    private suspend fun interroga(servizio: String, corpo: String): EsitoDintorni {
        val esito = Rete.postaConEsito(
            indirizzo = servizio,
            corpo = corpo,
            tipo = Rete.MODULO,
            massimoCaratteri = Rete.MASSIMO_DINTORNI,
        )

        val risposta = when (esito) {
            is EsitoHttp.Riuscito -> esito.corpo
            // 429 vuol dire aspetta, 504 vuol dire che il server non ce la fa
            // adesso, 400 vuol dire che la query e' sbagliata — cioe' un difetto
            // nostro. Tre rimedi diversi, e nessuno indovinabile da "non
            // aggiornato".
            is EsitoHttp.Rifiutato ->
                return EsitoDintorni.Rifiutato(esito.codice, messaggio(esito.corpo))
            EsitoHttp.Muto -> return EsitoDintorni.SenzaRete
        }

        // Il `remark` si legge **prima** degli elementi: una risposta 200 con
        // zero risultati e un remark non e' una zona deserta, e' una query che il
        // server ha interrotto.
        Overpass.avvertimento(risposta)?.let { return EsitoDintorni.Avvertito(it) }

        val dintorno = Overpass.leggi(risposta)
        return when {
            !dintorno.vuoto -> EsitoDintorni.Riuscito(dintorno)
            // Elementi arrivati e zero salvati non e' una zona deserta: e' una
            // risposta che non sappiamo leggere.
            dintorno.illeggibile -> EsitoDintorni.Illeggibile(dintorno.elementi)
            else -> EsitoDintorni.Vuoto
        }
    }

    /**
     * La parte utile del corpo d'errore.
     *
     * Il taglio sta in [Overpass.causa], che e' dominio e si verifica senza
     * rete: un errore di Overpass arriva avvolto in due righe di licenza sempre
     * uguali, e in duecento caratteri quelle coprivano la frase che spiega
     * davvero cosa non va — che era proprio il caso del 504 del dispatcher.
     */
    private fun messaggio(corpo: String?): String? = corpo
        ?.let { Overpass.causa(it) }
        ?.takeUnless { it.isEmpty() }
        ?.take(200)
}

/**
 * Com'e' andata la richiesta dei dintorni.
 *
 * E' l'unica scorta che riferisce l'errore, e non per simmetria: meteo e tratte
 * hanno un ripiego — la previsione vecchia, la linea d'aria — mentre i dintorni
 * non ce l'hanno. Se non arrivano, Esplora e le schede delle tappe restano
 * vuote, e l'utente deve poter sapere **perche'**.
 */
sealed interface EsitoDintorni {
    /**
     * Porta il [Dintorno] con se' e non solo due conteggi: chi decide se salvare
     * non e' chi ha fatto la richiesta, e con due numeri dovrebbe chiedere il
     * resto una seconda volta.
     */
    data class Riuscito(val dintorno: Dintorno) : EsitoDintorni {
        val poi: Int get() = dintorno.poi.size
        val luoghi: Int get() = dintorno.luoghi.size
    }

    /** Ha risposto che li' non c'e' niente. E' una risposta. */
    data object Vuoto : EsitoDintorni

    /** Ha risposto, e non abbiamo saputo leggere la risposta. E' un difetto. */
    data class Illeggibile(val elementi: Int) : EsitoDintorni

    /**
     * Ha risposto **200 con un avvertimento**: la query e' stata interrotta per
     * tempo o memoria. E' il caso che per mesi si e' travestito da "qui non c'e'
     * niente", e adesso porta il messaggio del server con se'.
     */
    data class Avvertito(val messaggio: String) : EsitoDintorni

    data class Rifiutato(val codice: Int, val messaggio: String?) : EsitoDintorni

    /** Niente campo, timeout, o risposta oltre il tetto. */
    data object SenzaRete : EsitoDintorni

    /** Nessun punto su cui centrare la ricerca. */
    data object SenzaTappe : EsitoDintorni

    /**
     * Una riga che dice com'e' andata, da scrivere nelle impostazioni.
     *
     * **Non e' il messaggio per l'utente** — quello sta nelle stringhe, tradotto
     * e gentile — ma la traccia che resta: una notifica scorre, e la domanda
     * «perche' non carica niente?» arriva il giorno dopo, in mezzo al nulla, con
     * la notifica gia' dimenticata. Questa riga si rilegge nelle impostazioni
     * quando serve, ed e' la differenza fra sapere e indovinare.
     */
    fun riassunto(): String = when (this) {
        is Riuscito -> "riuscita: $poi punti, $luoghi toponimi"
        Vuoto -> "nessun risultato: qui non c'e' niente di censito"
        is Illeggibile -> "illeggibile: $elementi elementi arrivati, nessuno usabile"
        is Avvertito -> "il server avverte: $messaggio"
        is Rifiutato -> "rifiutata con $codice" + (messaggio?.let { ": $it" } ?: "")
        SenzaRete -> "senza rete, o risposta troppo grande"
        SenzaTappe -> "nessun punto su cui cercare"
    }
}
