package it.myacamperlife.app.dominio

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * I numeri di una previsione, senza dire di che periodo sono.
 *
 * Li condividono il giorno intero e le sue fasce: cielo, temperature, pioggia e
 * vento si leggono e si raccontano allo stesso modo, e senza questa interfaccia
 * ci sarebbero due copie di [TestoMeteo.riga] che divergono alla prima modifica.
 */
interface DatiMeteo {
    /** Il codice WMO che Open-Meteo restituisce. Vedi [CieloMeteo]. */
    val codice: Int?
    val minima: Double?
    val massima: Double?
    val pioggiaMm: Double?
    val probabilitaPioggia: Int?
    val ventoKmh: Double?
}

val DatiMeteo.cielo: CieloMeteo get() = CieloMeteo.da(codice)

/**
 * Vero quando il periodo merita un avviso: pioggia probabile, o vento forte.
 * Con un camper il vento non e' un dettaglio.
 */
val DatiMeteo.daGuardare: Boolean
    get() = (probabilitaPioggia ?: 0) >= 50 ||
        (pioggiaMm ?: 0.0) >= 2.0 ||
        (ventoKmh ?: 0.0) >= 50.0 ||
        cielo.severo

/**
 * Le tre parti di una giornata di viaggio.
 *
 * **Non quattro.** La notte non e' una fascia di cui si chiede il meteo: si
 * dorme, e se piove lo si scopre dal tetto. Le ore sono quelle locali del posto,
 * perche' Open-Meteo risponde nel fuso del punto richiesto — "sera" a Umago e
 * "sera" in Baviera sono la stessa ora del giorno, non lo stesso istante.
 */
enum class FasciaGiorno(val etichetta: String, val dalle: Int, val alle: Int) {
    MATTINO("Mattino", 6, 12),
    POMERIGGIO("Pomeriggio", 12, 18),
    SERA("Sera", 18, 24);

    companion object {
        fun di(ora: Int): FasciaGiorno? = entries.firstOrNull { ora >= it.dalle && ora < it.alle }
    }
}

/**
 * Il meteo di una parte della giornata.
 *
 * **Nasce da una domanda pratica**: "18–31°, pioggia 40%" su un giorno intero non
 * dice se conviene camminare la mattina o il pomeriggio, che e' la decisione che
 * si prende davvero. I numeri sono aggregati dalle ore dentro la fascia, con le
 * regole scritte in [RispostaMeteo].
 */
@Serializable
data class Fascia(
    val quale: FasciaGiorno,
    override val codice: Int? = null,
    override val minima: Double? = null,
    override val massima: Double? = null,
    override val pioggiaMm: Double? = null,
    override val probabilitaPioggia: Int? = null,
    override val ventoKmh: Double? = null,
) : DatiMeteo

/**
 * La previsione di un giorno in un posto.
 *
 * I campi sono tutti facoltativi tranne il giorno: una previsione a dodici
 * giorni puo' non avere la probabilita' di pioggia, e mezza previsione e'
 * meglio di nessuna.
 *
 * [fasce] puo' essere vuota, e lo e' per i file scritti dalle versioni prima
 * delle fasce e per i giorni oltre l'orizzonte orario del servizio: chi la
 * mostra ripiega sul giorno intero, che c'e' sempre.
 */
@Serializable
data class Previsione(
    /** Il giorno, in forma ISO: e' anche la chiave con cui si ritrova. */
    val giorno: String,
    override val codice: Int? = null,
    override val minima: Double? = null,
    override val massima: Double? = null,
    override val pioggiaMm: Double? = null,
    override val probabilitaPioggia: Int? = null,
    override val ventoKmh: Double? = null,
    val fasce: List<Fascia> = emptyList(),
) : DatiMeteo {
    val data: LocalDate? get() = runCatching { LocalDate.parse(giorno) }.getOrNull()

    /** La fascia, se c'e': l'ordine e' quello della giornata, non dell'arrivo. */
    fun fascia(quale: FasciaGiorno): Fascia? = fasce.firstOrNull { it.quale == quale }
}

/** Le previsioni di un posto. Le coordinate sono quelle chieste, non quelle della griglia. */
@Serializable
data class MeteoLuogo(
    val nome: String? = null,
    val lat: Double,
    val lon: Double,
    val previsioni: List<Previsione> = emptyList(),
) {
    fun del(giorno: LocalDate): Previsione? = previsioni.firstOrNull { it.giorno == giorno.toString() }
}

/**
 * La scorta di meteo: quello che si e' scaricato, e quando.
 *
 * **L'eta' e' un campo, non un dettaglio.** Una previsione di ieri sera vale
 * ancora; una di quattro giorni fa e' folklore. Chi la mostra deve dire quanto
 * e' vecchia, e questa classe glielo rende impossibile da dimenticare.
 */
@Serializable
data class Meteo(
    /** Istante ISO dello scarico. */
    val scaricatoIl: String,
    val luoghi: List<MeteoLuogo> = emptyList(),
) {
    val istante: OffsetDateTime?
        get() = runCatching { OffsetDateTime.parse(scaricatoIl) }.getOrNull()

    fun eta(adesso: OffsetDateTime): Duration? =
        istante?.let { Duration.between(it, adesso) }

    /**
     * Oltre questa eta' la previsione non si mostra piu': una previsione di
     * cinque giorni fa non e' un dato vecchio, e' un dato sbagliato.
     */
    fun scaduto(adesso: OffsetDateTime): Boolean {
        val eta = eta(adesso) ?: return true
        return eta > VALIDITA
    }

    /**
     * La previsione per un giorno vicino a certe coordinate.
     *
     * Si cerca il luogo **piu' vicino** e non quello uguale: Open-Meteo
     * risponde con le coordinate del suo nodo di griglia, che stanno a qualche
     * chilometro da quelle chieste, e un confronto esatto non troverebbe mai
     * niente.
     */
    fun per(lat: Double, lon: Double, giorno: LocalDate, entro: Double = VICINANZA_KM): Previsione? {
        val luogo = luoghi
            .map { it to Distanza.km(lat, lon, it.lat, it.lon) }
            .filter { it.second <= entro }
            .minByOrNull { it.second }
            ?.first
            ?: return null
        return luogo.del(giorno)
    }

    companion object {
        /** Tre giorni: oltre, una previsione non e' piu' una previsione. */
        val VALIDITA: Duration = Duration.ofDays(3)

        /** Entro quanto una previsione vale per un posto: la griglia e' larga. */
        const val VICINANZA_KM = 25.0
    }
}

/**
 * Il cielo, dal codice WMO che usa Open-Meteo.
 *
 * Sono raggruppati: la tabella WMO distingue trentacinque situazioni, e in un
 * riepilogo serale servono sette parole. Un codice sconosciuto diventa
 * [IGNOTO] invece di far cadere niente.
 */
enum class CieloMeteo(val descrizione: String, val severo: Boolean = false) {
    // L'ordine **e'** la scala di gravita': ci si appoggia [gravita], che
    // sceglie il cielo di una fascia fra le sue ore. Spostare una voce cambia
    // quella scelta, quindi non si sposta senza un motivo.

    SERENO("sereno"),
    POCO_NUVOLOSO("poco nuvoloso"),
    NUVOLOSO("nuvoloso"),
    NEBBIA("nebbia"),
    PIOGGIA("pioggia", severo = true),
    ROVESCI("rovesci", severo = true),
    NEVE("neve", severo = true),
    TEMPORALE("temporale", severo = true),
    IGNOTO("cielo non pervenuto");

    /**
     * Quanto e' grave, per confrontare due cieli.
     *
     * `IGNOTO` sta **sotto tutti** e non in mezzo: un'ora di cui non si sa
     * niente non deve vincere su un'ora di sole, altrimenti una mattina serena
     * con un buco nei dati diventa "cielo non pervenuto".
     */
    val gravita: Int get() = if (this == IGNOTO) -1 else ordinal

    companion object {
        /**
         * La tabella WMO, raggruppata. I numeri sono quelli dello standard e
         * non cambiano: e' l'unica ragione per cui si possono scrivere qui.
         */
        fun da(codice: Int?): CieloMeteo = when (codice) {
            null -> IGNOTO
            0 -> SERENO
            1, 2 -> POCO_NUVOLOSO
            3 -> NUVOLOSO
            45, 48 -> NEBBIA
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67 -> PIOGGIA
            80, 81, 82 -> ROVESCI
            71, 73, 75, 77, 85, 86 -> NEVE
            95, 96, 99 -> TEMPORALE
            else -> IGNOTO
        }
    }
}

/**
 * Come si dice una previsione, in italiano.
 *
 * Sta nel dominio con [TestoBriefing] e per la stessa ragione: e' logica su
 * cosa dire e cosa tacere, e la si verifica senza un telefono.
 */
object TestoMeteo {

    /**
     * "Sereno, 18–31°, pioggia 10%". Le parti assenti non si nominano.
     *
     * Vale per un giorno intero e per una fascia: sono gli stessi numeri, e
     * scriverne due versioni vorrebbe dire vederle divergere.
     */
    fun riga(previsione: DatiMeteo): String {
        val pezzi = buildList {
            add(previsione.cielo.descrizione.replaceFirstChar { it.uppercase() })
            temperature(previsione)?.let { add(it) }
            pioggia(previsione)?.let { add(it) }
            vento(previsione)?.let { add(it) }
        }
        return pezzi.joinToString(", ")
    }

    private fun temperature(previsione: DatiMeteo): String? {
        val minima = previsione.minima?.roundToInt()
        val massima = previsione.massima?.roundToInt()
        return when {
            minima != null && massima != null -> "$minima–$massima°"
            massima != null -> "max $massima°"
            minima != null -> "min $minima°"
            else -> null
        }
    }

    /**
     * La probabilita' sotto il venti per cento non si dice: "pioggia 5%" fa
     * pensare alla pioggia, che e' l'opposto di quello che il dato racconta.
     */
    private fun pioggia(previsione: DatiMeteo): String? {
        val probabilita = previsione.probabilitaPioggia
        val millimetri = previsione.pioggiaMm
        return when {
            probabilita != null && probabilita >= 20 && millimetri != null && millimetri >= 1 ->
                "pioggia $probabilita%, ${arrotonda(millimetri)} mm"
            probabilita != null && probabilita >= 20 -> "pioggia $probabilita%"
            millimetri != null && millimetri >= 1 -> "${arrotonda(millimetri)} mm di pioggia"
            else -> null
        }
    }

    /** Il vento si nomina solo quando conta: con un camper, dai 30 km/h in su. */
    private fun vento(previsione: DatiMeteo): String? =
        previsione.ventoKmh?.takeIf { it >= 30 }?.let { "vento ${it.roundToInt()} km/h" }

    private fun arrotonda(millimetri: Double): String =
        if (millimetri >= 10) millimetri.roundToInt().toString()
        else String.format(java.util.Locale.ITALIAN, "%.1f", millimetri)

    /**
     * La previsione in poche parole: "Sereno 18–31°".
     *
     * Serve dove lo spazio e' quello di un'etichetta — l'intestazione di una
     * giornata nell'itinerario — e la riga intera non ci sta. Si tengono le due
     * cose che si guardano davvero: com'e' il cielo e quanto fa caldo.
     */
    fun breve(previsione: DatiMeteo): String? {
        val cielo = previsione.cielo.takeUnless { it == CieloMeteo.IGNOTO }?.descrizione
            ?.replaceFirstChar { it.uppercase() }
        val gradi = temperature(previsione)
        return listOfNotNull(cielo, gradi).joinToString(" ").takeUnless { it.isBlank() }
    }

    /**
     * Le fasce di una giornata, una riga per fascia: "Mattino: sereno, 17–22°".
     *
     * Vuota quando le fasce non ci sono — file vecchio, o giorno oltre
     * l'orizzonte orario del servizio — e chi chiama mostra il giorno intero,
     * che c'e' sempre. **Meglio una riga sola che tre righe inventate.**
     */
    fun fasce(previsione: Previsione): List<String> = previsione.fasce
        .sortedBy { it.quale.ordinal }
        .map { "${it.quale.etichetta}: ${riga(it)}" }

    /**
     * Le fasce su una riga sola: "mattino sereno, pomeriggio rovesci, sera
     * nuvoloso". Serve dove non c'e' spazio per tre righe — la notifica della
     * sera — e dove quello che conta e' **come cambia** la giornata.
     *
     * Solo il cielo, senza numeri: tre temperature e tre probabilita' su una
     * riga sono una riga che non si legge.
     */
    fun fasceInLinea(previsione: Previsione): String? = previsione.fasce
        .sortedBy { it.quale.ordinal }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { "${it.quale.etichetta.lowercase()} ${it.cielo.descrizione}" }

    /**
     * Quanto e' vecchia la previsione, detto come lo direbbe una persona.
     *
     * Non e' cortesia: una previsione di tre giorni fa e chi la legge devono
     * incontrarsi, altrimenti quel dato viene creduto.
     *
     * Prende le ore e non un orologio, cosi' resta una funzione pura come
     * tutto il resto qui dentro.
     */
    fun eta(ore: Long?): String? = when {
        ore == null || ore < 0 -> null
        ore < 2 -> "meteo di poco fa"
        ore < 12 -> "meteo di $ore ore fa"
        ore < 36 -> "meteo di ieri"
        else -> "meteo di ${ore / 24} giorni fa"
    }
}
