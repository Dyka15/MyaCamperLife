package it.myacamperlife.app.dominio

/**
 * Trova l'importo totale nel testo di uno scontrino.
 *
 * Il riconoscimento dei caratteri lo fa il telefono, senza rete (fase 4,
 * `archivio/Scontrino.kt`). Questa e' la parte che decide **quale** dei numeri
 * letti sia il totale, ed e' pura: prende righe di testo e restituisce un
 * numero, quindi si puo' verificare su scontrini veri senza una fotocamera.
 *
 * Il risultato e' una proposta, non un dato: finisce nel campo dell'importo
 * gia' pronto da correggere. Sbagliare un numero costa una cifra digitata,
 * non una spesa sbagliata — purche' l'interfaccia lo dica, e lo dice.
 */
object LetturaScontrino {

    fun importo(testo: String): Double? = importo(testo.lines())

    /**
     * La strategia, in ordine:
     *
     * 1. si cerca una riga che parli di totale — dall'ultima verso l'alto,
     *    perche' il totale sta in fondo e sopra ci sono le singole voci
     * 2. su quella riga si prende **il numero piu' alto**: il layout tipico e'
     *    `TOTALE EURO 12,50`, ma capita `TOTALE 12,50 di cui IVA 2,25`
     * 3. se la riga del totale non ha numeri, si guarda quella dopo: certe
     *    stampanti mandano a capo l'importo
     * 4. se nessuna riga parla di totale, si prende il numero piu' alto di
     *    tutto lo scontrino, che sulla stragrande maggioranza degli scontrini
     *    e' il totale
     */
    fun importo(righe: List<String>): Double? {
        val pulite = righe.map { it.trim() }.filter { it.isNotEmpty() }

        for (i in pulite.indices.reversed()) {
            if (!parlaDiTotale(pulite[i])) continue
            val sulla = importi(pulite[i]).maxOrNull()
            if (sulla != null) return sulla
            val dopo = pulite.getOrNull(i + 1)?.let { riga ->
                if (parlaDiTotale(riga)) null else importi(riga).maxOrNull()
            }
            if (dopo != null) return dopo
        }

        return pulite.flatMap { importi(it) }.maxOrNull()
    }

    /**
     * Tutti gli importi plausibili in una riga.
     *
     * Un importo ha **due decimali**: e' la regola che tiene fuori le date, le
     * ore, le quantita' (`1,5 L`), le percentuali di IVA e i codici fiscali,
     * senza doverli riconoscere uno per uno.
     */
    fun importi(riga: String): List<Double> = IMPORTO.findAll(riga).mapNotNull { trovato ->
        val intera = trovato.groupValues[1].replace(GRUPPI, "")
        val decimali = trovato.groupValues[2]
        "$intera.$decimali".toDoubleOrNull()?.takeIf { it > 0 && it <= MASSIMO }
    }.toList()

    private fun parlaDiTotale(riga: String): Boolean {
        val alta = riga.uppercase()
        if (ESCLUSE.any { it in alta }) return false
        return TOTALI.any { it in alta }
    }

    /**
     * Le parole che annunciano il totale. `TOT` senza punto no: comparirebbe
     * dentro altre parole.
     */
    private val TOTALI = listOf("TOTALE", "TOTAL", "IMPORTO", "DA PAGARE", "TOT.")

    /**
     * Righe che contengono la parola totale ma non il totale.
     *
     * `SUBTOTALE` e' il parziale prima degli sconti; `IVA` e `IMPONIBILE`
     * scompongono l'importo; `RESTO` e `CONTANTI` dicono quanto hai dato al
     * cassiere, che e' quasi sempre piu' di quanto hai speso.
     */
    private val ESCLUSE = listOf(
        "SUBTOTALE", "SUB TOTALE", "SUBTOTAL", "PARZIALE",
        "IVA", "IMPONIBILE", "SCONTO", "RESTO", "CONTANT", "ARROTONDAMENTO",
    )

    /**
     * `1.234,56`, `1 234.56`, `12,50`. Le due cifre finali sono obbligatorie.
     *
     * Il primo `(?!...)` scarta i numeri attaccati a una lettera o a un'altra
     * cifra; il secondo scarta `06.08.2026`, dove dopo i due decimali arriva
     * un altro separatore e altre cifre.
     */
    private val IMPORTO = Regex(
        "(?<![0-9A-Za-z])(\\d{1,3}(?:$SEPARATORI\\d{3})*|\\d+)[.,](\\d{2})(?![0-9])(?![.,]\\d)",
    )

    private val GRUPPI = Regex(SEPARATORI)

    /**
     * Cosa puo' separare le migliaia: spazio, punto, e lo spazio insecabile
     * che le stampanti fiscali usano piu' spesso di quanto si creda.
     */
    private const val SEPARATORI = "[ .\\u00A0]"

    /** Sopra questa cifra non e' uno scontrino, e' una lettura sbagliata. */
    private const val MASSIMO = 100_000.0
}
