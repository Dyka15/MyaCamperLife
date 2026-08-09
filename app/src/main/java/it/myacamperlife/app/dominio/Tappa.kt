package it.myacamperlife.app.dominio

/**
 * Una tappa dell'itinerario.
 *
 * Il modello di dominio e la riga di CSV sono due cose distinte: la
 * conversione sta in `archivio/TappeTabella.kt`. Qui non c'e' niente che
 * sappia di file o di Android.
 */
data class Tappa(
    val id: String,
    val ordine: Int,
    val nome: String,
    val lat: Double,
    val lon: Double,
    val tipo: String? = null,
    /** Come lo scrive l'itinerario: puo' essere una data, un numero, "gio 6". */
    val giorno: String? = null,
    val descrizione: String? = null,
    /**
     * Tutto il resto che l'itinerario diceva di questa tappa: orari, telefono,
     * quota, link. Vedi [Waypoint.altro] — si conserva perche' sono le cose che
     * servono arrivando, e prima finivano nel nulla.
     */
    val altro: List<Pair<String, String>> = emptyList(),
    val stato: StatoTappa = StatoTappa.DA_FARE,
    /** Istante ISO-8601 del check-in, quando c'e' stato. */
    val checkinIl: String? = null,
)

enum class StatoTappa(val codice: String) {
    DA_FARE("da_fare"),
    FATTA("fatta"),
    SALTATA("saltata"),
    ;

    companion object {
        /**
         * Uno stato sconosciuto non fa fallire la lettura: torna [DA_FARE].
         * Un file scritto a mano non deve poter rendere l'app inutilizzabile.
         */
        fun da(codice: String?): StatoTappa =
            entries.firstOrNull { it.codice == codice?.trim()?.lowercase() } ?: DA_FARE
    }
}
