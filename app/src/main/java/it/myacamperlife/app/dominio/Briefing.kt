package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Un giorno di viaggio previsto, con le tappe da fare. */
data class Giornata(val giorno: LocalDate, val tappe: List<Tappa>) {
    val nomi: List<String> get() = tappe.map { it.nome }
}

/**
 * Il riepilogo della sera: cosa ti aspetta domani, e se domani devi rifornire.
 *
 * @param kmDomani chilometri stimati per domani, **in linea d'aria**: da dove
 *   sei fino all'ultima tappa di domani, passando per quelle in mezzo. E' una
 *   sottostima, e chi lo mostra lo dice.
 */
data class Briefing(
    val oggi: LocalDate,
    val giornate: List<Giornata>,
    /** Tappe da fare che l'itinerario non ha datato, o che non si e' saputo leggere. */
    val senzaData: List<Tappa>,
    val kmDomani: Double?,
    val autonomia: Autonomia?,
    val rifornire: Boolean,
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
     * @param giorni quanti giorni guardare avanti, domani compreso.
     */
    fun componi(
        tappe: List<Tappa>,
        oggi: LocalDate,
        autonomia: Autonomia? = null,
        da: Coordinate? = null,
        giorni: Int = GIORNI,
    ): Briefing {
        val daFare = tappe.filter { it.stato == StatoTappa.DA_FARE }
        val (perGiorno, senzaData) = GiornoTappa.perGiorno(daFare, oggi)

        val finestra = oggi.plusDays(1)..oggi.plusDays(giorni.toLong())
        val giornate = perGiorno
            .filterKeys { it in finestra }
            .map { (giorno, tappe) -> Giornata(giorno, tappe) }

        // Le tappe arretrate — datate ieri o prima, mai spuntate — si mostrano
        // insieme a domani: sono comunque cose che devi ancora fare.
        val arretrate = perGiorno.filterKeys { it <= oggi }.values.flatten()

        val domani = giornate.firstOrNull { it.giorno == oggi.plusDays(1) }
        val kmDomani = domani?.let { chilometri(da, it.tappe) }

        return Briefing(
            oggi = oggi,
            giornate = giornate,
            senzaData = arretrate + senzaData,
            kmDomani = kmDomani,
            autonomia = autonomia,
            rifornire = serveRifornire(autonomia, kmDomani),
        )
    }

    /** In linea d'aria, da dove sei fino all'ultima tappa, passando per tutte. */
    private fun chilometri(da: Coordinate?, tappe: List<Tappa>): Double? {
        if (tappe.isEmpty()) return null
        val punti = listOfNotNull(da) + tappe.map { Coordinate(it.lat, it.lon) }
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
     * - **domani non ci arrivi con margine** — i chilometri stimati moltiplicati
     *   per [MARGINE], perche' due approssimazioni tirano nella stessa
     *   direzione: le distanze sono in linea d'aria, quindi piu' corte di quelle
     *   su strada, e l'autonomia non conta i chilometri fatti senza registrare
     *   niente. Il numero vero e' peggiore di quello calcolato, sempre
     *
     * L'avviso va letto come "probabilmente domani ti serve", non come una
     * misura. Meglio un pieno in piu' che restare a secco su una provinciale.
     */
    private fun serveRifornire(autonomia: Autonomia?, kmDomani: Double?): Boolean {
        if (autonomia == null) return false
        if (autonomia.residui <= RISERVA_KM) return true
        if (kmDomani == null) return false
        return autonomia.residui < kmDomani * MARGINE
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

    /** Quanto si gonfia la stima dei chilometri prima di confrontarla. */
    const val MARGINE = 1.4
}

/** Una coppia di coordinate, senza l'ora: qui l'ora non serve. */
data class Coordinate(val lat: Double, val lon: Double)
