package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tutto quello che l'app sa di una tappa, raccolto in un posto.
 *
 * **Non aggiunge dati, li mette insieme.** La descrizione viene
 * dall'itinerario, la previsione dalla scorta meteo, i dintorni dalla scorta
 * OpenStreetMap, la distanza dalle tratte precalcolate: sono quattro cose che
 * l'app ha gia' e che, sparse in quattro schermate, non rispondono alla
 * domanda che si fa davvero — *com'e' questa tappa, e cosa mi aspetta*.
 *
 * Ogni campo puo' essere nullo e la scheda vale comunque: senza scorta resta la
 * tappa con la sua descrizione, che e' quello che si aveva prima. **Nessuna
 * riga dice "non disponibile"**: quello che non si sa non si nomina.
 */
data class SchedaTappa(
    val tappa: Tappa,
    /** Il giorno previsto, quando il campo `giorno` si e' saputo leggere. */
    val giorno: LocalDate?,
    /** Giorni da oggi: negativo per una tappa datata nel passato. */
    val fraGiorni: Long?,
    /** La previsione per **quel giorno** in **quel posto**, se la scorta ce l'ha. */
    val previsione: Previsione?,
    /** Da quante ore la previsione e' ferma li'. Serve a dichiararne l'eta'. */
    val meteoOreFa: Long?,
    /** Cosa c'e' nei dintorni della tappa, una riga per categoria. */
    val dintorni: List<RiassuntoCategoria>,
    /** Vero quando una scorta di dintorni esiste: distingue "niente" da "non so". */
    val haScorta: Boolean,
    /** La tappa da cui ci si arriva, secondo l'itinerario. */
    val da: Tappa?,
    /** Quanto c'e' in mezzo, **su strada**. Nullo se le tratte non ce l'hanno. */
    val percorso: Percorso?,
    /** Le risposte del modello gia' salvate per questa tappa: si rileggono offline. */
    val dossier: List<Dossier>,
) {
    val descrizione: String? get() = tappa.descrizione?.trim()?.takeUnless { it.isEmpty() }

    val posizione: Coordinate get() = Coordinate(tappa.lat, tappa.lon)

    /**
     * Vero quando la scorta c'e' ma intorno alla tappa non ha trovato niente.
     *
     * E' un caso diverso dal non avere scorta, e va detto diversamente: "qui
     * non c'e' niente" e' un'informazione, "non lo so" e' un invito a scaricare.
     */
    val dintorniVuoti: Boolean get() = haScorta && dintorni.isEmpty()
}

/**
 * Compone la scheda di una tappa. Funzione pura: prende la data di oggi
 * invece di leggere l'orologio, e i dati invece dei file.
 */
object Schede {

    /**
     * @param tappe l'itinerario intero: serve a sapere da dove ci si arriva.
     * @param meteo la scorta di previsioni. Se e' **scaduta** non si usa
     *   affatto: una previsione di cinque giorni fa non e' un dato vecchio, e'
     *   un dato sbagliato, ed e' la stessa regola del riepilogo serale.
     * @param dossier i dossier del viaggio: si filtrano per nome della tappa.
     */
    fun componi(
        tappa: Tappa,
        tappe: List<Tappa>,
        oggi: LocalDate,
        poi: List<Poi> = emptyList(),
        tratte: Tratte? = null,
        meteo: Meteo? = null,
        adesso: OffsetDateTime? = null,
        dossier: List<Dossier> = emptyList(),
    ): SchedaTappa {
        val giorno = GiornoTappa.leggi(tappa.giorno, oggi)

        val valido = meteo?.takeUnless { adesso != null && it.scaduto(adesso) }
        val previsione = giorno?.let { valido?.per(tappa.lat, tappa.lon, it) }

        // La tappa prima nell'ordine dell'itinerario, non la piu' vicina: da
        // qui si arriva percorrendo l'itinerario, ed e' quella la distanza che
        // interessa.
        val da = tappe
            .filter { it.ordine < tappa.ordine }
            .maxByOrNull { it.ordine }

        return SchedaTappa(
            tappa = tappa,
            giorno = giorno,
            fraGiorni = giorno?.toEpochDay()?.minus(oggi.toEpochDay()),
            previsione = previsione,
            meteoOreFa = previsione?.let { adesso?.let { ora -> valido?.eta(ora)?.toHours() } },
            dintorni = Dintorni.riassunto(poi, tappa.lat, tappa.lon),
            haScorta = poi.isNotEmpty(),
            da = da,
            percorso = da?.let {
                tratte?.percorso(
                    listOf(Coordinate(it.lat, it.lon), Coordinate(tappa.lat, tappa.lon)),
                )
            },
            dossier = dossier.filter { it.tappa != null && it.tappa == tappa.nome },
        )
    }
}

/**
 * Come si dice una tappa, in italiano.
 *
 * Sta nel dominio con [TestoMeteo] e [TestoBriefing], per la stessa ragione:
 * decidere cosa dire e cosa tacere e' logica, e si verifica senza un telefono.
 */
object TestoTappa {

    /**
     * "oggi", "domani", "fra 3 giorni", "ieri", "6 giorni fa".
     *
     * Il numero di giorni conta piu' della data: davanti a "2026-08-14" si
     * calcola, davanti a "fra 3 giorni" si sa. La data si mostra comunque
     * accanto, perche' e' quella che si confronta con l'itinerario di carta.
     */
    fun quando(fraGiorni: Long?): String? = when {
        fraGiorni == null -> null
        fraGiorni == 0L -> "oggi"
        fraGiorni == 1L -> "domani"
        fraGiorni == 2L -> "dopodomani"
        fraGiorni > 2L -> "fra $fraGiorni giorni"
        fraGiorni == -1L -> "ieri"
        else -> "${-fraGiorni} giorni fa"
    }

    /** "gio 14 agosto": la forma che si legge di sfuggita. */
    fun data(giorno: LocalDate): String = giorno.format(GIORNO)

    private val GIORNO: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)
}
