package it.myacamperlife.app.dominio

import java.time.OffsetDateTime

/**
 * Una spesa del viaggio.
 *
 * **Quello che hai pagato si registra com'era scritto sullo scontrino**:
 * [importo] nella sua [valuta], e il [cambio] applicato in quel momento. Gli
 * euro sono un valore derivato, non un dato: cosi' una spesa in franchi resta
 * per sempre quarantacinque franchi, e il conto in euro si puo' ricalcolare
 * se il cambio che avevi in mente era sbagliato.
 *
 * Il carburante non sta qui: si registra con i litri, che ne chiedono gia'
 * l'importo. Se stesse in tutte e due le tabelle il conto lo conterebbe due
 * volte, e nessuna regola automatica potrebbe accorgersene.
 */
data class Spesa(
    val id: String,
    val istante: OffsetDateTime,
    val categoria: Categoria,
    val importo: Double,
    val modalita: Modalita,
    val descrizione: String? = null,
    val valuta: String = EURO,
    /**
     * Quanti euro vale un'unita' di [valuta]. Nullo per l'euro, e nullo
     * significa uno: una spesa senza cambio non sparisce dal conto.
     */
    val cambio: Double? = null,
    val tappa: String? = null,
    /** Nome del file della foto dello scontrino, nella cartella `scontrini/`. */
    val scontrino: String? = null,
) {
    val estera: Boolean get() = !valuta.equals(EURO, ignoreCase = true)

    /** L'importo in euro: e' con questo che si fanno i totali. */
    val euro: Double get() = if (estera) importo * (cambio ?: 1.0) else importo

    companion object {
        const val EURO = "EUR"
    }
}

/**
 * Le categorie di spesa.
 *
 * Sono poche di proposito. Un elenco lungo si compila male con una mano sola
 * in un'area di sosta, e a fine viaggio produce venti righe da un euro invece
 * di cinque numeri che dicono qualcosa.
 */
enum class Categoria(val codice: String) {
    SOSTA("sosta"),
    PEDAGGI("pedaggi"),
    SPESA("spesa"),
    RISTORANTE("ristorante"),
    VISITE("visite"),
    TRASPORTI("trasporti"),
    MEZZO("mezzo"),
    ALTRO("altro");

    companion object {
        /** Tollerante: un codice sconosciuto o assente diventa `altro`. */
        fun da(codice: String?): Categoria =
            entries.firstOrNull { it.codice.equals(codice?.trim(), ignoreCase = true) } ?: ALTRO
    }
}

/**
 * Come hai pagato.
 *
 * Non e' un'etichetta decorativa: serve a ritrovare la spesa sull'estratto
 * conto, e a sapere quanti contanti stanno finendo mentre sei lontano da un
 * bancomat.
 */
enum class Modalita(val codice: String) {
    CONTANTI("contanti"),
    POS("pos"),
    CARTA("carta");

    companion object {
        fun da(codice: String?): Modalita =
            entries.firstOrNull { it.codice.equals(codice?.trim(), ignoreCase = true) } ?: CONTANTI
    }
}
