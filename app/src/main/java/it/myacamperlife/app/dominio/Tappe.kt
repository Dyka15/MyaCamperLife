package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Le operazioni sull'itinerario, come funzioni pure su liste.
 *
 * Nessuna dipendenza da Android e nessuna scrittura su file: chi chiama
 * riceve le tappe cambiate e le rende persistenti. Cosi' le regole — qual e'
 * la tappa corrente, cosa succede ai numeri d'ordine quando si inserisce in
 * mezzo — si verificano senza emulatore.
 */
object Tappe {

    /**
     * La tappa dove sei: l'ultima di cui hai fatto check-in.
     *
     * "L'ultima" per ordine nell'itinerario e non per orario del check-in:
     * se torni indietro a una tappa gia' fatta, resti dove l'itinerario dice
     * che sei arrivato piu' avanti.
     */
    fun corrente(tappe: List<Tappa>): Tappa? =
        tappe.filter { it.stato == StatoTappa.FATTA }.maxByOrNull { it.ordine }

    /** La prossima da fare, salti esclusi. */
    fun prossima(tappe: List<Tappa>): Tappa? {
        val da = corrente(tappe)?.ordine ?: 0
        return tappe.filter { it.stato == StatoTappa.DA_FARE && it.ordine > da }.minByOrNull { it.ordine }
            ?: tappe.filter { it.stato == StatoTappa.DA_FARE }.minByOrNull { it.ordine }
    }

    fun checkin(tappa: Tappa, istante: OffsetDateTime): Tappa = tappa.copy(
        stato = StatoTappa.FATTA,
        checkinIl = istante.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

    /**
     * Disfa un check-in: la tappa torna da fare e perde l'ora d'arrivo.
     *
     * **Esiste per il tocco sbagliato**, che era l'unico gesto dell'app senza
     * ritorno. [alterna] non fa niente su una tappa fatta, e la ragione era
     * buona — saltare un posto in cui sei stato non vuol dire nulla — ma la
     * conseguenza non era voluta: un check-in dato per errore restava per
     * sempre, e con lui dove sei, la prossima tappa, il riepilogo della sera e
     * il nome delle foto.
     *
     * Su una tappa che non e' fatta non fa niente: non c'e' niente da disfare.
     */
    fun annullaCheckin(tappa: Tappa): Tappa =
        if (tappa.stato == StatoTappa.FATTA) {
            tappa.copy(stato = StatoTappa.DA_FARE, checkinIl = null)
        } else {
            tappa
        }

    /**
     * Il comando a doppio stato: una tappa da fare diventa saltata, una
     * saltata torna da fare. Su una tappa gia' fatta non fa niente — saltare
     * un posto in cui sei stato non vuol dire nulla.
     */
    fun alterna(tappa: Tappa): Tappa = when (tappa.stato) {
        StatoTappa.DA_FARE -> tappa.copy(stato = StatoTappa.SALTATA)
        StatoTappa.SALTATA -> tappa.copy(stato = StatoTappa.DA_FARE)
        StatoTappa.FATTA -> tappa
    }

    /**
     * Inserisce una tappa prima di quella con id [primaDi], o in fondo se e'
     * `null`, e rinumera tutto.
     *
     * Torna **l'elenco intero**, non solo la tappa nuova: inserire in mezzo
     * cambia il numero d'ordine di tutte quelle successive, e chi chiama deve
     * scriverle tutte. Rinumerare da 1 a ogni inserimento tiene i numeri
     * densi e senza buchi, che e' quello che si vede nell'elenco.
     */
    fun inserisci(tappe: List<Tappa>, nuova: Tappa, primaDi: String? = null): List<Tappa> {
        val ordinate = tappe.sortedBy { it.ordine }
        val posizione = primaDi
            ?.let { id -> ordinate.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: ordinate.size
        val conNuova = ordinate.toMutableList().apply { add(posizione, nuova) }
        return conNuova.mapIndexed { indice, tappa -> tappa.copy(ordine = indice + 1) }
    }

    /**
     * Le tappe il cui numero d'ordine o stato e' cambiato rispetto a [prima].
     * Serve per accodare solo le righe che servono invece di riscrivere tutto.
     */
    fun cambiate(prima: List<Tappa>, dopo: List<Tappa>): List<Tappa> {
        val vecchie = prima.associateBy { it.id }
        return dopo.filter { vecchie[it.id] != it }
    }
}
