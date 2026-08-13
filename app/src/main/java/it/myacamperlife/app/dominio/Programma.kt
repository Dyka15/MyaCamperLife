package it.myacamperlife.app.dominio

import java.time.LocalDate

/**
 * Il programma di una giornata, come sta scritto nell'itinerario.
 *
 * @param etichetta l'intestazione come l'ha scritta chi viaggia: `6/8 — Giovedì`.
 * @param titolo la riga di terzo livello che segue, quando c'e':
 *   `Lonigo → Garmisch-Partenkirchen → Eibsee`. E' il percorso del giorno, e sta
 *   a parte perche' e' la riga che si legge per prima.
 * @param testo tutto il resto, capoversi compresi.
 */
data class SezioneGiorno(
    val giorno: LocalDate,
    val etichetta: String,
    val titolo: String?,
    val testo: String,
) {
    val vuota: Boolean get() = testo.isBlank() && titolo.isNullOrBlank()
}

/**
 * Legge il programma giorno per giorno dal corpo Markdown dell'itinerario.
 *
 * **Il blocco `waypoints` non e' tutto l'itinerario.** Quello porta nomi e
 * coordinate — quanto basta a disegnare una mappa — mentre il *viaggio* sta nel
 * testo intorno: gli orari, le durate, cosa vale la pena vedere e perche', dove
 * si dorme. Un file vero ha ottocento parole per il 10 agosto a Monaco e
 * `"description": "Marienplatz, Residenz"` nel JSON. Fino a questa fase l'app
 * leggeva solo il secondo e buttava il primo.
 *
 * **Il legame e' il giorno, non la tappa.** Le sezioni sono giornate — `## 6/8 —
 * Giovedì` — e in una giornata ci stanno piu' tappe: il 6 agosto si passa da
 * Lonigo, Garmisch e l'Eibsee, e il programma e' lo stesso per tutte tre. E'
 * cosi' che il file e' scritto, ed e' anche vero: quel testo racconta la
 * giornata, non il singolo punto sulla mappa.
 *
 * **Le sezioni che non sono giorni si scartano da se'.** `## RIEPILOGO KM
 * GIORNALIERI` e `## BLOCCO MAPPA` non hanno una data nell'intestazione, e
 * [GiornoTappa] restituisce `null`: non serve un elenco di titoli da ignorare,
 * che invecchierebbe al primo itinerario scritto diversamente.
 *
 * Funzione pura.
 */
object Programmi {

    /**
     * Le giornate del documento, in ordine di apparizione.
     *
     * @param riferimento serve a dare un anno alle date parziali: `6/8` non ne
     *   ha uno, e si risolve in avanti come per il campo `giorno` di una tappa.
     */
    fun sezioni(documento: String, riferimento: LocalDate): List<SezioneGiorno> {
        val righe = documento.lines()
        val inizi = righe.indices.filter { intestazione(righe[it]) != null }
        if (inizi.isEmpty()) return emptyList()

        return inizi.mapIndexedNotNull { posizione, inizio ->
            val etichetta = intestazione(righe[inizio]) ?: return@mapIndexedNotNull null
            val giorno = GiornoTappa.leggi(etichetta, riferimento) ?: return@mapIndexedNotNull null

            val fine = inizi.getOrNull(posizione + 1) ?: righe.size
            val corpo = righe.subList(inizio + 1, fine)

            // Il primo `###` e' il percorso del giorno: si tiene a parte perche'
            // e' la riga che si legge per prima, e ripeterla dentro il testo
            // sarebbe rumore.
            val titolo = corpo.firstOrNull { it.trimStart().startsWith("### ") }
                ?.trimStart()
                ?.removePrefix("### ")
                ?.trim()
                ?.takeUnless { it.isEmpty() }

            SezioneGiorno(
                giorno = giorno,
                etichetta = etichetta,
                titolo = titolo,
                testo = pulisci(corpo),
            )
        }
    }

    /** La giornata di una data, se il documento ne parla. */
    fun per(sezioni: List<SezioneGiorno>, giorno: LocalDate?): SezioneGiorno? =
        giorno?.let { data -> sezioni.firstOrNull { it.giorno == data } }

    /**
     * Le giornate di piu' documenti, dove **l'ultimo vince** sui giorni che
     * copre.
     *
     * Un viaggio puo' avere piu' di un itinerario: arrivato al 13 agosto
     * riscrivi i dieci giorni che restano e carichi un file nuovo. Il vecchio
     * non si butta — racconta i giorni che hai vissuto, e quelli sono nel diario
     * — ma sui giorni di cui parlano entrambi ha ragione il nuovo: e' l'ultima
     * cosa che hai deciso.
     *
     * Ordine: i documenti arrivano dal piu' vecchio al piu' recente, come sono
     * stati scritti.
     */
    fun fondi(documenti: List<List<SezioneGiorno>>): List<SezioneGiorno> =
        documenti
            .flatten()
            .associateBy { it.giorno }
            .values
            .sortedBy { it.giorno }

    /**
     * L'intestazione di secondo livello di una riga, o `null`.
     *
     * Solo il secondo livello: il primo e' il titolo del documento e il terzo e'
     * il percorso dentro una giornata.
     */
    private fun intestazione(riga: String): String? {
        val pulita = riga.trim()
        if (!pulita.startsWith("## ") || pulita.startsWith("### ")) return null
        return pulita.removePrefix("## ").trim().takeUnless { it.isEmpty() }
    }

    /**
     * Il corpo di una giornata, senza il titolo e senza i separatori.
     *
     * Si toglie il `###` — sta gia' in [SezioneGiorno.titolo] — e le righe di
     * `---`, che nel Markdown separano le giornate e nel testo di una scheda
     * sarebbero una riga vuota con un trattino.
     *
     * I capoversi restano: **sono la struttura del programma**, e schiacciarli
     * trasformerebbe una giornata leggibile in un muro di parole.
     */
    private fun pulisci(corpo: List<String>): String = corpo
        .filterNot { it.trimStart().startsWith("### ") }
        .filterNot { it.trim().matches(SEPARATORE) }
        .joinToString("\n")
        .trim()
        // Tre o piu' capi diventano due: fra le sezioni di una giornata il file
        // ne ha di piu' del necessario, e a schermo si vedono come buchi.
        .replace(TROPPI_CAPI, "\n\n")

    private val SEPARATORE = "^-{3,}$".toRegex()
    private val TROPPI_CAPI = "\n{3,}".toRegex()
}
