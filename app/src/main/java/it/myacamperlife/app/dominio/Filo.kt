package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una tappa nel filo del viaggio, con quello che serve a disegnarla.
 *
 * [arrivoDa] e' quanto si guida **per arrivarci** dalla tappa precedente
 * dell'itinerario: sta sulla fermata e non fra due fermate perche' cosi' non
 * esiste il caso di un tratto senza capo — e perche' un tratto che scavalca la
 * mezzanotte appartiene al giorno in cui si arriva, non a quello da cui si parte.
 */
data class Fermata(
    val tappa: Tappa,
    val arrivoDa: Percorso? = null,
    val corrente: Boolean = false,
)

/**
 * Una giornata dell'itinerario: le sue tappe, e che tempo fa.
 *
 * [giorno] e' nullo per le tappe di cui non si e' capita la data. Non si
 * buttano e non si mescolano: finiscono in un gruppo a se' in fondo, con la
 * loro etichetta — una tappa che sparisce dall'elenco e' peggio di una tappa
 * senza data.
 *
 * Si chiama `GiornataFilo` e non `Giornata` perche' quel nome e' gia' del
 * riepilogo serale, nello stesso package. Il compilatore l'ha scoperto prima di
 * me, e in un modo istruttivo: la chiamata al costruttore risolveva sull'altra
 * classe, e l'errore parlava di parametri che non avevo mai scritto.
 */
data class GiornataFilo(
    val giorno: LocalDate?,
    val etichetta: String,
    val previsione: Previsione? = null,
    val fermate: List<Fermata> = emptyList(),
)

/**
 * L'itinerario raccontato per giornate invece che per righe.
 *
 * **Un viaggio si pensa a giorni.** "Cosa si fa domani" e "quanto si guida per
 * arrivarci" sono le due domande vere, e in un elenco piatto di ventiquattro
 * righe nessuna delle due ha una risposta a colpo d'occhio. Qui le tappe stanno
 * sotto il loro giorno, con il meteo di quel giorno e la strada fra una e
 * l'altra — tutti dati che l'app ha gia' in casa, nessuna richiesta in piu'.
 *
 * Funzione pura: prende la data di oggi e le scorte, non legge orologi ne' file.
 */
object Filo {

    /**
     * @param tratte le distanze su strada gia' calcolate. Senza, le fermate non
     *   portano il tratto: la linea d'aria sembrerebbe una distanza di guida
     *   senza esserlo.
     * @param meteo la scorta di previsioni. Se e' **scaduta** non si usa: una
     *   previsione di cinque giorni fa non e' un dato vecchio, e' un dato
     *   sbagliato. E' la stessa regola della scheda di tappa e del riepilogo.
     */
    fun componi(
        tappe: List<Tappa>,
        oggi: LocalDate,
        tratte: Tratte? = null,
        meteo: Meteo? = null,
        adesso: OffsetDateTime? = null,
    ): List<GiornataFilo> {
        if (tappe.isEmpty()) return emptyList()

        val ordinate = tappe.sortedBy { it.ordine }
        val corrente = Tappe.corrente(ordinate)
        val valido = meteo?.takeUnless { adesso != null && it.scaduto(adesso) }

        val fermate = ordinate.mapIndexed { indice, tappa ->
            val prima = ordinate.getOrNull(indice - 1)
            Fermata(
                tappa = tappa,
                arrivoDa = prima?.let { partenza ->
                    tratte?.percorso(
                        listOf(
                            Coordinate(partenza.lat, partenza.lon),
                            Coordinate(tappa.lat, tappa.lon),
                        ),
                    )
                },
                corrente = corrente != null && corrente.id == tappa.id,
            )
        }

        // **Gruppi di tappe consecutive con la stessa data**, non un
        // raggruppamento globale: se una data ricompare piu' avanti
        // nell'itinerario, ricompare anche qui. L'ordine dell'itinerario e' la
        // cosa che non si tocca — riordinare le tappe per data vorrebbe dire
        // mostrare un viaggio diverso da quello scritto nel file.
        val giornate = mutableListOf<GiornataFilo>()
        var correnti = mutableListOf<Fermata>()
        var giornoInCorso: LocalDate? = null
        var primo = true

        fun chiudi() {
            if (correnti.isEmpty()) return
            giornate += giornataFilo(giornoInCorso, correnti.toList(), oggi, valido)
            correnti = mutableListOf()
        }

        fermate.forEach { fermata ->
            val giorno = GiornoTappa.leggi(fermata.tappa.giorno, oggi)
            if (primo || giorno != giornoInCorso) {
                if (!primo) chiudi()
                giornoInCorso = giorno
                primo = false
            }
            correnti += fermata
        }
        chiudi()

        // Le tappe senza data vanno in fondo, tutte insieme: sparse in mezzo
        // spezzerebbero le giornate vere senza dire niente in cambio.
        val (conData, senzaData) = giornate.partition { it.giorno != null }
        if (senzaData.isEmpty()) return conData
        val raccolte = senzaData.flatMap { it.fermate }
        return conData + GiornataFilo(null, SENZA_DATA, null, raccolte)
    }

    private fun giornataFilo(
        giorno: LocalDate?,
        fermate: List<Fermata>,
        oggi: LocalDate,
        meteo: Meteo?,
    ): GiornataFilo {
        // Il meteo si prende **dove si dorme**, cioe' sull'ultima tappa del
        // giorno: e' la previsione che decide come si passa la sera, e in una
        // giornata che attraversa duecento chilometri le due punte possono avere
        // tempi diversi.
        val dove = fermate.lastOrNull()?.tappa
        val previsione = giorno?.let { data ->
            dove?.let { meteo?.per(it.lat, it.lon, data) }
        }
        return GiornataFilo(giorno, etichetta(giorno, oggi), previsione, fermate)
    }

    /**
     * "giovedì 6 agosto", con "oggi" e "domani" davanti quando servono.
     *
     * I due nomi relativi non sostituiscono la data, la precedono: "oggi" da
     * solo costringe a ricordare che giorno e', e in viaggio non lo sa nessuno.
     */
    fun etichetta(giorno: LocalDate?, oggi: LocalDate): String {
        if (giorno == null) return SENZA_DATA
        val scritta = giorno.format(GIORNO)
        return when (giorno.toEpochDay() - oggi.toEpochDay()) {
            0L -> "Oggi · $scritta"
            1L -> "Domani · $scritta"
            else -> scritta.replaceFirstChar { it.uppercase() }
        }
    }

    const val SENZA_DATA = "Senza data"

    private val GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
}
