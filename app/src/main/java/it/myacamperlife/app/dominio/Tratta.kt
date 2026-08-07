package it.myacamperlife.app.dominio

/**
 * Una tratta fra due punti, **su strada**.
 *
 * E' il dato che la linea d'aria non sa dare: sull'itinerario italiano il
 * rapporto fra i due sta di solito fra 1,2 e 1,4, ma su una statale di montagna
 * arriva al doppio, e nessun coefficiente lo indovina.
 */
data class Tratta(
    val daLat: Double,
    val daLon: Double,
    val aLat: Double,
    val aLon: Double,
    val km: Double,
    val minuti: Int,
    /** Nomi delle tappe, quando si conoscono: servono a leggere il file. */
    val da: String? = null,
    val a: String? = null,
)

/**
 * Le tratte precalcolate: la scorta stradale del viaggio.
 *
 * Si riempiono quando si importa un itinerario e c'e' rete. Da quel momento
 * distanza e tempo di guida sono un dato locale, e **una sola finestra di
 * connettivita' basta per l'intero viaggio**.
 *
 * Funzione pura: la ricerca non tocca file.
 */
class Tratte(val tutte: List<Tratta> = emptyList()) {

    val vuoto: Boolean get() = tutte.isEmpty()

    /**
     * La tratta fra due punti, se e' stata precalcolata.
     *
     * Si cerca **la piu' vicina entro una tolleranza** e non quella uguale: le
     * coordinate arrivano da file scritti in momenti diversi, arrotondate a sei
     * decimali, e un confronto esatto fra `Double` e' una promessa che prima o
     * poi si rompe.
     */
    fun fra(
        daLat: Double,
        daLon: Double,
        aLat: Double,
        aLon: Double,
        entro: Double = TOLLERANZA_KM,
    ): Tratta? = tutte
        .map { it to scarto(it, daLat, daLon, aLat, aLon) }
        .filter { it.second <= entro }
        .minByOrNull { it.second }
        ?.first

    private fun scarto(
        tratta: Tratta,
        daLat: Double,
        daLon: Double,
        aLat: Double,
        aLon: Double,
    ): Double = maxOf(
        Distanza.km(tratta.daLat, tratta.daLon, daLat, daLon),
        Distanza.km(tratta.aLat, tratta.aLon, aLat, aLon),
    )

    /**
     * Il percorso lungo una catena di punti: chilometri e minuti sommati.
     *
     * Restituisce `null` se **anche una sola** tratta manca. Un totale con un
     * pezzo mancante sarebbe piu' corto del vero, e chi lo legge deciderebbe
     * se rifornire su un numero sbagliato per difetto: e' l'errore che si vuole
     * evitare piu' di tutti. Meglio ripiegare sulla linea d'aria, che almeno
     * dichiara di essere una stima.
     */
    fun percorso(punti: List<Coordinate>, entro: Double = TOLLERANZA_KM): Percorso? {
        if (punti.size < 2) return null
        var km = 0.0
        var minuti = 0
        punti.zipWithNext().forEach { (primo, secondo) ->
            val tratta = fra(primo.lat, primo.lon, secondo.lat, secondo.lon, entro) ?: return null
            km += tratta.km
            minuti += tratta.minuti
        }
        return Percorso(km, minuti)
    }

    companion object {
        /**
         * Un chilometro: le tappe non si spostano, e se una si e' spostata di
         * piu' di cosi' la sua tratta e' un'altra tratta.
         */
        const val TOLLERANZA_KM = 1.0
    }
}

/** Chilometri e minuti di guida di un percorso su strada. */
data class Percorso(val km: Double, val minuti: Int) {
    /** "2 h 15" oppure "45 min": nessuno legge "135 minuti". */
    val durata: String
        get() = if (minuti < 60) "$minuti min" else "${minuti / 60} h ${(minuti % 60).toString().padStart(2, '0')}"
}
