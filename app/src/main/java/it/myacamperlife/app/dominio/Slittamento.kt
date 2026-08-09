package it.myacamperlife.app.dominio

import java.time.LocalDate

/**
 * Di quanto un viaggio e' fuori programma, e cosa farne.
 *
 * @param giorni positivo se in ritardo, negativo se in anticipo, `0` se in
 *   orario. E' la differenza fra il giorno in cui sei arrivato e quello in cui
 *   l'itinerario diceva che ci sarebbe stato.
 * @param daFare quante tappe verrebbero spostate. Zero significa che non c'e'
 *   niente da proporre: il ritardo e' un fatto, ma non ha conseguenze.
 */
data class Slittamento(val giorni: Long, val daFare: Int) {
    val ritardo: Boolean get() = giorni > 0
    val anticipo: Boolean get() = giorni < 0

    /** Quanti giorni in valore assoluto: e' quello che si dice all'utente. */
    val quanti: Long get() = if (giorni < 0) -giorni else giorni

    /**
     * Se valga la pena chiedere se spostare l'itinerario.
     *
     * Un giorno intero e' la soglia: mezza giornata di ritardo si recupera
     * guidando, e proporre di riscrivere l'itinerario per quello sarebbe
     * fastidioso. Sopra il giorno, invece, tutte le date successive sono
     * sbagliate e con esse il riepilogo della sera e il meteo delle tappe.
     */
    val daChiedere: Boolean get() = quanti >= 1 && daFare > 0
}

/**
 * Cosa fare quando il viaggio non va come previsto.
 *
 * **Il ritardo non si indovina, si misura a un check-in.** Arrivi a Bolsena il
 * dieci quando l'itinerario diceva l'otto: da quel momento tutte le date
 * successive sono sbagliate, e sono le date che il riepilogo serale usa per dire
 * cosa c'e' domani e per prendere la previsione giusta. Spostarle e' un gesto,
 * non un calcolo continuo: l'app se ne accorge e chiede.
 *
 * Funzioni pure: nessuna data di sistema, nessun file.
 */
object Slittamenti {

    /**
     * Il ritardo o l'anticipo di un arrivo, e quante tappe seguono.
     *
     * @param quando il giorno in cui sei arrivato davvero.
     * @param oggi il riferimento per leggere le date parziali dell'itinerario
     *   — `6 agosto`, `mer 6` — che senza un anno non si risolvono.
     *
     * Restituisce `null` quando non c'e' niente da misurare: la tappa non ha una
     * data leggibile, e allora non c'e' un programma da cui essere in ritardo.
     */
    fun misura(
        tappa: Tappa,
        tappe: List<Tappa>,
        quando: LocalDate,
        oggi: LocalDate,
    ): Slittamento? {
        val previsto = GiornoTappa.leggi(tappa.giorno, oggi) ?: return null
        val giorni = quando.toEpochDay() - previsto.toEpochDay()
        return Slittamento(giorni = giorni, daFare = daSpostare(tappe, tappa).size)
    }

    /**
     * Sposta di [giorni] le tappe che restano, e restituisce **solo quelle
     * cambiate**.
     *
     * Tre scelte, tutte deliberate:
     *
     * - **si spostano solo le tappe da fare.** Quelle spuntate sono storia, e
     *   riscriverne la data significherebbe falsificare quello che e' successo.
     *   Anche le saltate restano: se le ripristini, la loro data va rivista a
     *   mano, che e' meno peggio di una data cambiata a tua insaputa
     * - **si riscrive in forma ISO.** L'itinerario puo' aver scritto `mer 6` o
     *   `06/08/2026`; spostando si perde quella forma. E' una perdita
     *   accettabile — la data diventa piu' precisa, non meno — e l'alternativa
     *   sarebbe indovinare come riscrivere ogni formato
     * - **una tappa senza data leggibile non si tocca.** Non si sa da dove
     *   partire, e inventare un punto di partenza e' peggio che lasciarla dov'e'
     */
    fun slitta(
        tappe: List<Tappa>,
        da: Tappa,
        giorni: Long,
        oggi: LocalDate,
    ): List<Tappa> {
        if (giorni == 0L) return emptyList()
        return daSpostare(tappe, da).mapNotNull { tappa ->
            val previsto = GiornoTappa.leggi(tappa.giorno, oggi) ?: return@mapNotNull null
            tappa.copy(giorno = previsto.plusDays(giorni).toString())
        }
    }

    /**
     * Le tappe che vengono dopo [da] nell'itinerario e sono ancora da fare.
     *
     * L'ordine dell'itinerario e non la data: e' l'itinerario che dice cosa
     * viene dopo, e una tappa con una data sbagliata e' esattamente il caso che
     * si sta rimediando.
     */
    private fun daSpostare(tappe: List<Tappa>, da: Tappa): List<Tappa> = tappe
        .filter { it.ordine > da.ordine && it.stato == StatoTappa.DA_FARE }
        .sortedBy { it.ordine }
}

/**
 * I giorni di un viaggio, **senza buchi**.
 *
 * Un itinerario e' fatto di giorni, non di spostamenti: se il dieci agosto si
 * resta dove si era il nove, il dieci agosto esiste comunque e va detto — «si
 * resta a Bolsena» e' un'informazione, un giorno che manca dall'elenco e' una
 * lacuna che sembra un difetto. Vale per il riepilogo della sera come per
 * qualunque vista che elenchi giorni.
 *
 * Funzione pura.
 */
object GiorniDelViaggio {

    /**
     * @param tappe le tappe con la loro data, quando ce l'hanno.
     * @param da primo giorno compreso.
     * @param a ultimo giorno compreso.
     * @param dove dove ti trovi **davvero**, di solito la tappa dell'ultimo
     *   check-in. Serve a dire dove si resta in un giorno senza tappe, e **vince
     *   sull'itinerario**: il check-in e' un fatto misurato, l'ultima tappa in
     *   programma prima della finestra e' un'ipotesi su cosa e' successo. Se sei
     *   arrivato a Viterbo, domani senza spostamenti sei a Viterbo, qualunque cosa
     *   l'itinerario avesse previsto per ieri.
     */
    fun giorni(
        tappe: List<Tappa>,
        da: LocalDate,
        a: LocalDate,
        oggi: LocalDate,
        dove: String? = null,
    ): List<GiornoDiViaggio> {
        if (a.isBefore(da)) return emptyList()

        val perGiorno = tappe
            .mapNotNull { tappa -> GiornoTappa.leggi(tappa.giorno, oggi)?.let { it to tappa } }
            .groupBy({ it.first }, { it.second })

        // Da dove si parte. Prima dove ti trovi davvero; solo se non lo si sa —
        // il viaggio non e' ancora cominciato — si ripiega sull'ultima tappa in
        // programma prima della finestra.
        var ultimo = dove ?: perGiorno
            .filterKeys { it.isBefore(da) }
            .toSortedMap()
            .values
            .lastOrNull()
            ?.lastOrNull()
            ?.nome

        val elenco = mutableListOf<GiornoDiViaggio>()
        var giorno = da
        while (!giorno.isAfter(a)) {
            val sue = perGiorno[giorno].orEmpty().sortedBy { it.ordine }
            elenco.add(GiornoDiViaggio(giorno, sue, restaA = if (sue.isEmpty()) ultimo else null))
            sue.lastOrNull()?.let { ultimo = it.nome }
            giorno = giorno.plusDays(1)
        }
        return elenco
    }

    /**
     * I giorni che l'itinerario salta: hanno una tappa prima e una dopo, ma
     * nessuna loro.
     *
     * Serve a dirlo all'import. Non e' un errore — il file non e' nostro, e un
     * giorno senza tappa e' legittimo se si resta fermi — ma se e' una
     * dimenticanza vale piu' scoprirlo a casa che la sera del giorno che manca.
     */
    fun buchi(tappe: List<Tappa>, oggi: LocalDate): List<LocalDate> {
        val date = tappe
            .mapNotNull { GiornoTappa.leggi(it.giorno, oggi) }
            .distinct()
            .sorted()
        if (date.size < 2) return emptyList()

        val presenti = date.toSet()
        val mancanti = mutableListOf<LocalDate>()
        var giorno = date.first().plusDays(1)
        while (giorno.isBefore(date.last())) {
            if (giorno !in presenti) mancanti.add(giorno)
            giorno = giorno.plusDays(1)
        }
        return mancanti
    }
}

/**
 * Un giorno di viaggio: le sue tappe, oppure dove si resta.
 *
 * I due campi si escludono: se ci sono tappe si va da qualche parte, se non ce
 * ne sono si resta — e [restaA] dice dove, quando si sa.
 */
data class GiornoDiViaggio(
    val giorno: LocalDate,
    val tappe: List<Tappa>,
    val restaA: String? = null,
) {
    val fermo: Boolean get() = tappe.isEmpty()
    val nomi: List<String> get() = tappe.map { it.nome }
}
