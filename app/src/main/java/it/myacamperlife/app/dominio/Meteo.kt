package it.myacamperlife.app.dominio

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * La previsione di un giorno in un posto.
 *
 * I campi sono tutti facoltativi tranne il giorno: una previsione a dodici
 * giorni puo' non avere la probabilita' di pioggia, e mezza previsione e'
 * meglio di nessuna.
 */
@Serializable
data class Previsione(
    /** Il giorno, in forma ISO: e' anche la chiave con cui si ritrova. */
    val giorno: String,
    /** Il codice WMO che Open-Meteo restituisce. Vedi [CieloMeteo]. */
    val codice: Int? = null,
    val minima: Double? = null,
    val massima: Double? = null,
    val pioggiaMm: Double? = null,
    val probabilitaPioggia: Int? = null,
    val ventoKmh: Double? = null,
) {
    val data: LocalDate? get() = runCatching { LocalDate.parse(giorno) }.getOrNull()

    val cielo: CieloMeteo get() = CieloMeteo.da(codice)

    /**
     * Vero quando la giornata merita un avviso: pioggia probabile, o vento
     * forte. Con un camper il vento non e' un dettaglio.
     */
    val daGuardare: Boolean
        get() = (probabilitaPioggia ?: 0) >= 50 ||
            (pioggiaMm ?: 0.0) >= 2.0 ||
            (ventoKmh ?: 0.0) >= 50.0 ||
            cielo.severo
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
    SERENO("sereno"),
    POCO_NUVOLOSO("poco nuvoloso"),
    NUVOLOSO("nuvoloso"),
    NEBBIA("nebbia"),
    PIOGGIA("pioggia", severo = true),
    ROVESCI("rovesci", severo = true),
    NEVE("neve", severo = true),
    TEMPORALE("temporale", severo = true),
    IGNOTO("cielo non pervenuto");

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

    /** "Sereno, 18–31°, pioggia 10%". Le parti assenti non si nominano. */
    fun riga(previsione: Previsione): String {
        val pezzi = buildList {
            add(previsione.cielo.descrizione.replaceFirstChar { it.uppercase() })
            temperature(previsione)?.let { add(it) }
            pioggia(previsione)?.let { add(it) }
            vento(previsione)?.let { add(it) }
        }
        return pezzi.joinToString(", ")
    }

    private fun temperature(previsione: Previsione): String? {
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
    private fun pioggia(previsione: Previsione): String? {
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
    private fun vento(previsione: Previsione): String? =
        previsione.ventoKmh?.takeIf { it >= 30 }?.let { "vento ${it.roundToInt()} km/h" }

    private fun arrotonda(millimetri: Double): String =
        if (millimetri >= 10) millimetri.roundToInt().toString()
        else String.format(java.util.Locale.ITALIAN, "%.1f", millimetri)

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
