package it.myacamperlife.app.dominio

import kotlin.math.roundToInt

/**
 * Un punto di interesse: quello che un camper cerca nei dintorni.
 *
 * Non e' una copia di OpenStreetMap: sono le sette categorie che servono in
 * viaggio, con nome e coordinate. Tutto il resto — orari, recensioni, foto —
 * sta nelle app di mappe, che lo fanno di mestiere.
 */
data class Poi(
    val id: String,
    val nome: String?,
    val categoria: CategoriaPoi,
    val lat: Double,
    val lon: Double,
    /** Una riga di dettaglio: il gestore, il tipo di carburante, il telefono. */
    val dettaglio: String? = null,
) {
    /** Il nome, o la categoria quando il posto non ne ha uno. */
    fun etichetta(): String = nome?.takeUnless { it.isBlank() } ?: categoria.senzaNome
}

/**
 * Le categorie, con i tag OpenStreetMap che le riempiono.
 *
 * Poche e scelte: un elenco che comprende tutto non si scorre con una mano
 * mentre si cerca un posto per la notte.
 */
enum class CategoriaPoi(
    val codice: String,
    val nome: String,
    val senzaNome: String,
    /** I filtri Overpass, uno per riga di query. */
    val filtri: List<String>,
) {
    SOSTA(
        codice = "sosta",
        nome = "Aree di sosta",
        senzaNome = "Area di sosta",
        filtri = listOf("[\"tourism\"=\"caravan_site\"]"),
    ),
    CAMPEGGIO(
        codice = "campeggio",
        nome = "Campeggi",
        senzaNome = "Campeggio",
        filtri = listOf("[\"tourism\"=\"camp_site\"]"),
    ),
    SERVIZIO(
        codice = "servizio",
        nome = "Carico e scarico",
        senzaNome = "Camper service",
        filtri = listOf("[\"amenity\"=\"sanitary_dump_station\"]"),
    ),
    ACQUA(
        codice = "acqua",
        nome = "Acqua potabile",
        senzaNome = "Fontana",
        filtri = listOf("[\"amenity\"=\"drinking_water\"]"),
    ),
    CARBURANTE(
        codice = "carburante",
        nome = "Distributori",
        senzaNome = "Distributore",
        filtri = listOf("[\"amenity\"=\"fuel\"]"),
    ),
    SPESA(
        codice = "spesa",
        nome = "Supermercati",
        senzaNome = "Supermercato",
        filtri = listOf("[\"shop\"=\"supermarket\"]"),
    ),
    ATTRAZIONE(
        codice = "attrazione",
        nome = "Da vedere",
        senzaNome = "Punto d'interesse",
        filtri = listOf(
            "[\"tourism\"=\"attraction\"]",
            "[\"tourism\"=\"viewpoint\"]",
            "[\"tourism\"=\"museum\"]",
        ),
    );

    companion object {
        fun da(codice: String?): CategoriaPoi? =
            entries.firstOrNull { it.codice.equals(codice?.trim(), ignoreCase = true) }

        /**
         * Dai tag OSM alla categoria. Il primo che corrisponde vince: un
         * campeggio con anche `caravan_site` finisce fra i campeggi, e va bene
         * cosi' — l'importante e' che compaia una volta.
         */
        fun daTag(tag: Map<String, String>): CategoriaPoi? = when {
            tag["tourism"] == "caravan_site" -> SOSTA
            tag["tourism"] == "camp_site" -> CAMPEGGIO
            tag["amenity"] == "sanitary_dump_station" -> SERVIZIO
            tag["amenity"] == "drinking_water" -> ACQUA
            tag["amenity"] == "fuel" -> CARBURANTE
            tag["shop"] == "supermarket" -> SPESA
            tag["tourism"] in setOf("attraction", "viewpoint", "museum") -> ATTRAZIONE
            else -> null
        }
    }
}

/** Un punto di interesse con la distanza da dove sei. */
data class PoiVicino(val poi: Poi, val km: Double) {
    /**
     * "450 m" oppure "3,2 km": sotto il chilometro i metri dicono di piu', e
     * "0,45 km" non lo scriverebbe nessuno.
     */
    val distanza: String
        get() = if (km < 1.0) "${(km * 1000).roundToInt()} m"
        else String.format(java.util.Locale.ITALIAN, "%.1f km", km)
}

/**
 * Cosa c'e' di una categoria intorno a un punto: quanti, e il piu' vicino.
 *
 * Sta insieme perche' separati non servono: il numero senza la distanza non
 * dice se vale la pena, la distanza senza il numero non dice se c'e' scelta.
 */
data class RiassuntoCategoria(
    val categoria: CategoriaPoi,
    val quanti: Int,
    val piuVicino: PoiVicino,
)

/**
 * La ricerca nei dintorni. Funzione pura: nessun file, nessuna rete.
 *
 * E' il primo dei due strati di Esplora: **questo risponde sempre**, perche'
 * legge una scorta locale. Il secondo — la domanda libera a un modello — sta
 * sopra a questo, non al suo posto.
 */
object Dintorni {

    fun vicini(
        poi: List<Poi>,
        lat: Double,
        lon: Double,
        categoria: CategoriaPoi? = null,
        entro: Double = RAGGIO_KM,
        quanti: Int = QUANTI,
    ): List<PoiVicino> = poi
        .asSequence()
        .filter { categoria == null || it.categoria == categoria }
        .map { PoiVicino(it, Distanza.km(lat, lon, it.lat, it.lon)) }
        .filter { it.km <= entro }
        .sortedBy { it.km }
        .take(quanti)
        .toList()

    /** Quante cose ci sono per categoria: serve a non offrire liste vuote. */
    fun quanti(poi: List<Poi>, lat: Double, lon: Double, entro: Double = RAGGIO_KM): Map<CategoriaPoi, Int> =
        CategoriaPoi.entries.associateWith { categoria ->
            poi.count {
                it.categoria == categoria && Distanza.km(lat, lon, it.lat, it.lon) <= entro
            }
        }.filterValues { it > 0 }

    /**
     * Cosa c'e' intorno a un punto, una riga per categoria.
     *
     * **Il numero da solo non risponde alla domanda.** "Aree di sosta: 3" non
     * dice se sono a due chilometri o a diciotto, e su un camper la differenza
     * decide la giornata. Ogni riga porta quindi anche il piu' vicino della
     * categoria, con la sua distanza.
     *
     * Le categorie vuote non compaiono, come nell'elenco: una riga con zero
     * accanto occupa lo spazio di una riga e non dice niente.
     *
     * Ordinate per vicinanza del capofila, non per l'ordine dell'enum: quello
     * che e' a un chilometro conta prima di quello che e' a quindici.
     */
    fun riassunto(
        poi: List<Poi>,
        lat: Double,
        lon: Double,
        entro: Double = RAGGIO_KM,
    ): List<RiassuntoCategoria> = CategoriaPoi.entries
        .mapNotNull { categoria ->
            val dentro = vicini(poi, lat, lon, categoria, entro, quanti = Int.MAX_VALUE)
            val primo = dentro.firstOrNull() ?: return@mapNotNull null
            RiassuntoCategoria(categoria, dentro.size, primo)
        }
        .sortedBy { it.piuVicino.km }

    /** Venti chilometri: oltre non e' piu' "nei dintorni". */
    const val RAGGIO_KM = 20.0

    /** Trenta risultati: piu' di cosi' non si scorrono. */
    const val QUANTI = 30
}
