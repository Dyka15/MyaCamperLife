package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * Un giorno di viaggio previsto, con le tappe da fare.
 *
 * @param restaA dove si resta, quando quel giorno non ha tappe. **Un giorno
 *   senza spostamenti e' un giorno del viaggio come gli altri**: saltarlo
 *   nell'elenco fa sembrare che il riepilogo abbia perso un pezzo, mentre "si
 *   resta a Bolsena" e' un'informazione.
 */
data class Giornata(
    val giorno: LocalDate,
    val tappe: List<Tappa>,
    val restaA: String? = null,
) {
    val nomi: List<String> get() = tappe.map { it.nome }
    val fermo: Boolean get() = tappe.isEmpty()
}

/**
 * Il riepilogo della sera: cosa ti aspetta domani, e se domani devi rifornire.
 *
 * @param kmDomani chilometri per domani, da dove sei fino all'ultima tappa,
 *   passando per quelle in mezzo. Sono quelli **su strada** se le tratte sono
 *   state precalcolate, altrimenti la linea d'aria — che e' una sottostima, e
 *   [suStrada] dice quale dei due e'.
 * @param minutiDomani il tempo di guida, che solo le tratte su strada sanno.
 */
data class Briefing(
    val oggi: LocalDate,
    val giornate: List<Giornata>,
    /** Tappe da fare che l'itinerario non ha datato, o che non si e' saputo leggere. */
    val senzaData: List<Tappa>,
    val kmDomani: Double?,
    val suStrada: Boolean = false,
    val minutiDomani: Int? = null,
    val autonomia: Autonomia?,
    val rifornire: Boolean,
    /** La previsione di domani nel posto dove sarai, quando la scorta ce l'ha. */
    val meteoDomani: Previsione? = null,
    /** Da quante ore la previsione e' ferma li'. Nulla se non si sa. */
    val meteoOreFa: Long? = null,
) {
    val domani: Giornata? get() = giornate.firstOrNull { it.giorno == oggi.plusDays(1) }

    /** I giorni dopo domani, quelli che il riepilogo cita di sfuggita. */
    val poi: List<Giornata> get() = giornate.filter { it.giorno > oggi.plusDays(1) }

    /**
     * Vero quando non c'e' niente da dire: nessuna tappa in vista e nessun
     * avviso. Un briefing vuoto **non si notifica**: una notifica che non
     * porta informazione insegna a ignorare le notifiche.
     */
    val vuoto: Boolean get() = giornate.isEmpty() && senzaData.isEmpty() && !rifornire
}

/**
 * Compone il briefing dalle tappe dell'itinerario.
 *
 * Guarda solo le tappe **da fare**: quelle spuntate e quelle saltate non
 * riguardano domani. Le date arrivano dal campo `giorno`, letto da
 * [GiornoTappa], che e' tollerante e puo' non riconoscerne qualcuna: quelle
 * finiscono in [Briefing.senzaData] e vengono comunque nominate, perche' una
 * tappa che sparisce dal riepilogo e' peggio di una tappa senza data.
 *
 * Funzione pura: prende la data di oggi invece di leggere l'orologio.
 */
object Briefings {

    /**
     * @param da dove sei adesso: serve a stimare i chilometri di domani. Se
     *   manca, i chilometri si contano da tappa a tappa e il primo tratto —
     *   quello da qui alla prima tappa — non entra nel conto.
     * @param tratte la scorta stradale, quando c'e'. Con quella i chilometri
     *   sono veri e arriva anche il tempo di guida.
     * @param meteo la scorta di previsioni, quando c'e' e non e' scaduta.
     * @param giorni quanti giorni guardare avanti, domani compreso.
     */
    fun componi(
        tappe: List<Tappa>,
        oggi: LocalDate,
        autonomia: Autonomia? = null,
        da: Coordinate? = null,
        tratte: Tratte? = null,
        meteo: Meteo? = null,
        adesso: OffsetDateTime? = null,
        giorni: Int = GIORNI,
    ): Briefing {
        val daFare = tappe.filter { it.stato == StatoTappa.DA_FARE }
        val (perGiorno, senzaData) = GiornoTappa.perGiorno(daFare, oggi)

        // **Ogni giorno della finestra, anche quelli senza tappe.** Prima si
        // elencavano solo i giorni che avevano qualcosa, e un giorno fermo
        // spariva dal riepilogo: chi lo leggeva vedeva "domani Bolsena,
        // dopodomani Roma" senza sapere che in mezzo c'era un giorno intero. Un
        // giorno di viaggio e' un giorno di viaggio anche se non si guida.
        val giornate = GiorniDelViaggio.giorni(
            tappe = daFare,
            da = oggi.plusDays(1),
            a = oggi.plusDays(giorni.toLong()),
            oggi = oggi,
            dove = tappe.lastOrNull { it.stato == StatoTappa.FATTA }?.nome,
        ).map { Giornata(it.giorno, it.tappe, restaA = it.restaA) }
            // Le code vuote non si mostrano: se l'itinerario finisce domani, i
            // due giorni dopo non sono giorni di viaggio, sono niente.
            .dropLastWhile { it.fermo }

        // Le tappe arretrate — datate ieri o prima, mai spuntate — si mostrano
        // insieme a domani: sono comunque cose che devi ancora fare.
        val arretrate = perGiorno.filterKeys { it <= oggi }.values.flatten()

        val domani = giornate.firstOrNull { it.giorno == oggi.plusDays(1) }
        val catena = domani?.let { listOfNotNull(da) + it.tappe.map { t -> Coordinate(t.lat, t.lon) } }

        // Prima la strada, poi la linea d'aria: sono due numeri diversi, e
        // quale dei due sia finisce dentro il briefing, non solo nel commento.
        val suStrada = catena?.let { tratte?.percorso(it) }
        val kmDomani = suStrada?.km ?: catena?.let { chilometri(it) }

        // Una previsione scaduta non e' un dato vecchio, e' un dato sbagliato:
        // non si mostra affatto.
        val valido = meteo?.takeUnless { adesso != null && it.scaduto(adesso) }
        val previsione = domani?.tappe?.firstOrNull()?.let { tappa ->
            valido?.per(tappa.lat, tappa.lon, domani.giorno)
        }
        val oreFa = adesso?.let { valido?.eta(it)?.toHours() }

        return Briefing(
            oggi = oggi,
            giornate = giornate,
            senzaData = arretrate + senzaData,
            kmDomani = kmDomani,
            suStrada = suStrada != null,
            minutiDomani = suStrada?.minuti,
            autonomia = autonomia,
            rifornire = serveRifornire(autonomia, kmDomani, suStrada != null),
            meteoDomani = previsione,
            meteoOreFa = previsione?.let { oreFa },
        )
    }

    /** In linea d'aria, da un punto all'altro della catena. */
    private fun chilometri(punti: List<Coordinate>): Double? {
        if (punti.size < 2) return null
        return punti.zipWithNext { primo, secondo ->
            Distanza.km(primo.lat, primo.lon, secondo.lat, secondo.lon)
        }.sum()
    }

    /**
     * Se domani conviene fare benzina.
     *
     * Due ragioni indipendenti, e basta una:
     *
     * - **sei in riserva** — sotto [RISERVA_KM] si rifornisce comunque, che tu
     *   debba guidare o no
     * - **domani non ci arrivi con margine** — i chilometri moltiplicati per un
     *   margine, perche' i conti tirano tutti nella stessa direzione: se le
     *   distanze sono in linea d'aria sono piu' corte di quelle su strada, e
     *   l'autonomia non conta i chilometri fatti senza registrare niente. Il
     *   numero vero e' peggiore di quello calcolato, sempre
     *
     * **Il margine dipende da che numero e'.** Sulla linea d'aria vale
     * [MARGINE_ARIA], perche' le approssimazioni sono due; sulle tratte
     * precalcolate scende a [MARGINE_STRADA], perche' una delle due e' sparita
     * e gonfiare oltre farebbe suonare l'avviso quando non serve — che e' il
     * modo in cui un avviso smette di essere ascoltato.
     *
     * L'avviso va letto come "probabilmente domani ti serve", non come una
     * misura. Meglio un pieno in piu' che restare a secco su una provinciale.
     */
    private fun serveRifornire(autonomia: Autonomia?, kmDomani: Double?, suStrada: Boolean): Boolean {
        if (autonomia == null) return false
        if (autonomia.residui <= RISERVA_KM) return true
        if (kmDomani == null) return false
        val margine = if (suStrada) MARGINE_STRADA else MARGINE_ARIA
        return autonomia.residui < kmDomani * margine
    }

    /**
     * Quando scatta il prossimo riepilogo: oggi se l'ora non e' ancora
     * passata, domani altrimenti.
     *
     * Sta qui e non accanto alla sveglia perche' e' aritmetica di calendario,
     * ed e' l'unico modo di verificarla senza aspettare le 19:00.
     */
    fun prossimoScatto(ora: Int, adesso: LocalDateTime): LocalDateTime {
        val oggi = LocalDateTime.of(adesso.toLocalDate(), LocalTime.of(ora.coerceIn(0, 23), 0))
        return if (oggi.isAfter(adesso)) oggi else oggi.plusDays(1)
    }

    /** Tre giorni: oltre, un itinerario cambia comunque. */
    const val GIORNI = 3

    /** Sotto questi chilometri si rifornisce e basta. */
    const val RISERVA_KM = 80.0

    /** Quanto si gonfia la linea d'aria prima di confrontarla: due incertezze. */
    const val MARGINE_ARIA = 1.4

    /** Sulle tratte vere ne resta una sola, e il margine si stringe. */
    const val MARGINE_STRADA = 1.15
}
