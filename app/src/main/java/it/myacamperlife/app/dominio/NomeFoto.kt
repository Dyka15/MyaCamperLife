package it.myacamperlife.app.dominio

import java.text.Normalizer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Il nome di un file di foto: `foto_AAAAMMGG_HHMMSS[_localita].jpg`.
 *
 * E' la stessa convenzione del sistema n8n, e si tiene per un motivo pratico:
 * le foto vecchie e quelle nuove stanno nella stessa cartella e si ordinano
 * insieme. Data e ora davanti perche' l'ordine alfabetico diventa cronologico.
 *
 * La localita' non viene da un geocoding — quello arriva alla fase 7 — ma dal
 * nome della tappa dove sei. Per un diario di viaggio e' anche meglio: "Orvieto"
 * dice piu' di un indirizzo civico.
 */
object NomeFoto {

    fun per(istante: OffsetDateTime, luogo: String? = null): String {
        val quando = istante.format(FORMATO)
        val dove = luogo?.let { ripulisci(it) }?.takeUnless { it.isEmpty() }
        return if (dove == null) "foto_$quando.jpg" else "foto_${quando}_$dove.jpg"
    }

    /**
     * Toglie accenti e caratteri che un filesystem o un gestore file
     * potrebbero non gradire, e tiene i trattini al posto degli spazi cosi'
     * il nome resta leggibile: "Lago di Bolsena" fa "Lago-di-Bolsena".
     */
    private fun ripulisci(luogo: String): String = Normalizer
        .normalize(luogo, Normalizer.Form.NFD)
        .replace(SEGNI, "")
        .replace(NON_AMMESSI, "-")
        .trim('-')
        .take(40)
        .trim('-')

    private val FORMATO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val SEGNI = "\\p{Mn}+".toRegex()
    private val NON_AMMESSI = "[^A-Za-z0-9]+".toRegex()
}
