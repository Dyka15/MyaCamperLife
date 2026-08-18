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
    /**
     * Da dove arriva questa tappa: dall'itinerario, o aggiunta a mano.
     *
     * **Serve a sapere di chi parla il programma della giornata.** Quel testo si
     * aggancia alle tappe per data, e per una tappa aggiunta a mano il giorno
     * combacia ma il racconto e' di altri posti: si finiva per leggere
     * "Abensberg → Regensburg" sotto il nome di Landshut.
     */
    val origine: OrigineTappa = OrigineTappa.IGNOTA,
)

/**
 * Chi ha messo una tappa nell'itinerario.
 *
 * [IGNOTA] non e' un caso da evitare: e' quello di tutte le righe scritte prima
 * che questa colonna esistesse, ed e' il motivo per cui il valore di riposo non
 * puo' essere nessuno dei due veri — indovinare vorrebbe dire sbagliare su
 * ventiquattro tappe per indovinarne una.
 */
enum class OrigineTappa(val codice: String) {
    ITINERARIO("itinerario"),
    MANO("mano"),
    IGNOTA(""),
    ;

    companion object {
        fun da(codice: String?): OrigineTappa =
            entries.firstOrNull { it.codice.isNotEmpty() && it.codice == codice?.trim()?.lowercase() }
                ?: IGNOTA
    }
}

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
