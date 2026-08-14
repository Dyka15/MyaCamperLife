package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Esplora
import it.myacamperlife.app.dominio.Modello
import kotlinx.serialization.Serializable

/**
 * Le impostazioni, in `impostazioni.json`.
 *
 * Stanno nella cartella d'archivio e non nelle preferenze dell'app perche'
 * l'archivio deve bastare a se stesso: reinstallando l'app, i km con un pieno
 * tornano da soli invece di lasciare l'autonomia muta senza spiegazione.
 *
 * Le chiavi API **non stanno qui**: quelle vanno nell'archivio cifrato
 * dell'app. Questa cartella puo' finire dentro una cartella sincronizzata su
 * un cloud, e una chiave in chiaro li' dentro sarebbe un errore difficile da
 * accorgersi.
 *
 * Ogni campo ha un valore di riposo, cosi' un file scritto da una versione
 * piu' vecchia si legge comunque: e' la stessa tolleranza dell'intestazione
 * dei CSV.
 */
@Serializable
data class Impostazioni(
    /** Quanti chilometri fa il mezzo con un serbatoio pieno. */
    val kmConUnPieno: Int? = null,
    /**
     * Se il riepilogo della sera deve arrivare. Acceso di riposo: e' la
     * funzione, e chi non la vuole la spegne in due tocchi.
     */
    val briefingAttivo: Boolean = true,
    /** L'ora del riepilogo, 0-23. Le 19:00 come nel sistema di prima. */
    val oraBriefing: Int = 19,
    /**
     * L'Uri della cartella in cui rispecchiare l'archivio, scelta dall'utente.
     *
     * Nullo finche' non se ne sceglie una: allora i file vivono solo nell'area
     * privata dell'app, dove funzionano ma dove nessun gestore file arriva.
     *
     * E' un Uri SAF, illeggibile a occhio, e sta qui e non nelle preferenze per
     * la stessa ragione di tutto il resto: l'archivio deve bastare a se stesso.
     * Il **permesso** su quella cartella pero' non e' nel file — vive
     * nell'installazione dell'app — quindi dopo una reinstallazione la cartella
     * va riscelta.
     */
    val cartellaSpecchio: String? = null,

    /**
     * Quando la cartella e' stata sincronizzata l'ultima volta, in forma ISO.
     *
     * E' l'unica delle tre date di aggiornamento che ha bisogno di un campo: il
     * meteo la porta dentro la sua scorta e i dintorni nel `ts` delle righe,
     * mentre una sincronizzazione non lascia una traccia propria — e senza
     * saperne la data non si sa se il telefono nuovo ha davvero preso tutto.
     */
    val sincronizzatoIl: String? = null,

    /**
     * Com'e' andata l'ultima ricerca dei dintorni, in una riga, e quando.
     *
     * **Non e' un vezzo diagnostico, e' la risposta a una domanda vera**: per
     * quattro fasi i dintorni sono tornati vuoti, e ogni volta l'unica cosa che
     * si sapeva era «non carica niente». La notifica che spiegava il perche'
     * durava tre secondi; questa riga resta, e si legge dalle impostazioni.
     *
     * Non contiene niente di riservato — un messaggio di Overpass e un codice
     * HTTP — quindi puo' stare in un file che finisce in una cartella
     * sincronizzata.
     */
    val dintorniEsito: String? = null,
    val dintorniProvatoIl: String? = null,

    /**
     * Cos'e' diventato l'ultimo file di itinerario caricato, e quando.
     *
     * **Stessa medicina della riga dei dintorni, per la stessa malattia.** Un
     * file caricato puo' diventare due cose — un viaggio nuovo, o il seguito di
     * uno che c'era — e quando quello che si vede sullo schermo non e' quello che
     * si aspettava, l'unica domanda che conta e' «cos'ha fatto?». Una notifica
     * dura tre secondi; questa riga si rilegge dalle impostazioni, e dice anche
     * **su quale viaggio** ha scritto.
     */
    val importEsito: String? = null,
    val importProvatoIl: String? = null,

    /**
     * Quale modello si prova per primo. L'altro fa da riserva.
     */
    val principale: String = "gemini",

    /**
     * Gli identificativi dei modelli, uno per fornitore.
     *
     * **Sono impostazioni e non costanti** perche' i nomi dei modelli vengono
     * ritirati ogni pochi mesi: compilati dentro, un ritiro renderebbe l'app
     * muta finche' non se ne pubblica una nuova. Qui si correggono in dieci
     * secondi, leggendo l'errore che il servizio ha restituito.
     */
    val modelloGemini: String = "gemini-flash-latest",
    val modelloGrok: String = "grok-4-fast",
    val modelloGroq: String = "groq/compound-mini",

    /**
     * Quali modelli ha visto l'ultima verifica, e quando.
     *
     * **Stessa medicina delle righe dei dintorni e dell'import.** Un
     * identificativo ritirato si presenta come un 404 che sembra un problema di
     * chiave; questa riga porta l'elenco che il fornitore ha dichiarato, e da qui
     * si copia il nome giusto nel campo di sopra. Sono nomi di modelli e codici
     * HTTP: niente di riservato, quindi puo' stare in un file rispecchiato.
     */
    val modelliEsito: String? = null,
    val modelliProvatoIl: String? = null,

    /**
     * Com'e' andata l'ultima domanda a un modello, e quando.
     *
     * Porta il fornitore, l'identificativo usato, quanto testo e' tornato, quante
     * fonti, e **i nomi dei campi** della risposta grezza. Gli ultimi servono a
     * una domanda che si fa solo a distanza: dove mette le fonti questo
     * fornitore. Nomi di campi e conteggi, niente del contenuto e niente della
     * chiave: puo' stare in un file rispecchiato.
     */
    val aiEsito: String? = null,
    val aiProvatoIl: String? = null,

    /**
     * Il prompt di sistema di Esplora. Vuoto significa "usa quello di riposo",
     * cosi' migliorandolo in una versione nuova chi non l'ha toccato lo riceve.
     */
    val promptEsplora: String = "",
) {
    val modelloPrincipale: Modello get() = Modello.da(principale) ?: Modello.GEMINI

    /** L'identificativo del modello per un fornitore, col ripiego. */
    fun modello(modello: Modello): String = when (modello) {
        Modello.GEMINI -> modelloGemini
        Modello.GROK -> modelloGrok
        Modello.GROQ -> modelloGroq
    }.trim().ifEmpty { modello.modelloDiRiposo }

    /** Il prompt di Esplora: il tuo se c'e', altrimenti quello di riposo. */
    fun prompt(): String = promptEsplora.trim().ifEmpty { Esplora.PROMPT_DI_RIPOSO }
    /** L'ora riportata dentro il quadrante, comunque sia scritta nel file. */
    val ora: Int get() = oraBriefing.coerceIn(0, 23)
}
