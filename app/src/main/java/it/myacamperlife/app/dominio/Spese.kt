package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Una fetta del conto: quanto, in quante voci, sotto una certa chiave. */
data class Quota<T>(val chiave: T, val euro: Double, val voci: Int) {
    /** La parte sul totale, fra 0 e 1. Serve alle barrette, non ai conti. */
    fun frazione(su: Double): Float = if (su > 0) (euro / su).toFloat().coerceIn(0f, 1f) else 0f
}

/** Quanto e' stato speso in una valuta, nella valuta e in euro. */
data class Cambiato(val valuta: String, val importo: Double, val euro: Double, val voci: Int)

/**
 * Il conto del viaggio.
 *
 * [spese] e [carburante] restano due numeri distinti perche' vengono da due
 * tabelle diverse: sommarli e basta nasconderebbe che il pieno lo registri
 * con i litri e la sosta con la spesa. [totale] li mette insieme, ed e' quello
 * che si guarda alla fine.
 */
data class Conto(
    val spese: Double,
    val carburante: Double,
    val voci: Int,
    val perCategoria: List<Quota<Categoria>>,
    val perModalita: List<Quota<Modalita>>,
    val perGiorno: List<Quota<LocalDate>>,
    val valute: List<Cambiato>,
    /** I giorni coperti, dal primo all'ultimo compresi: anche quelli a zero. */
    val giorni: Int,
) {
    val totale: Double get() = spese + carburante

    val vuoto: Boolean get() = voci == 0 && carburante == 0.0

    /**
     * La spesa media al giorno, carburante compreso.
     *
     * Si divide per i giorni **dal primo all'ultimo compresi**, non per i
     * giorni in cui hai speso qualcosa: due giorni in un'area gratis fanno
     * parte del viaggio, e diluiscono la media come devono.
     */
    val alGiorno: Double? get() = if (giorni > 0) totale / giorni else null
}

/**
 * Fa i conti delle spese. Funzione pura: non legge file e non guarda
 * l'orologio.
 */
object Spese {

    /**
     * [giorniDelCarburante] sono le date dei rifornimenti.
     *
     * Servono solo a contare i giorni: se il carburante entra nel totale, i
     * giorni in cui l'hai comprato devono entrare nella media, altrimenti un
     * pieno da cento euro fatto in un giorno senza altre spese gonfia la media
     * di un giorno che non viene contato.
     */
    fun conta(
        spese: List<Spesa>,
        carburante: Double = 0.0,
        giorniDelCarburante: List<LocalDate> = emptyList(),
    ): Conto {
        val euro = spese.sumOf { it.euro }

        val perCategoria = spese
            .groupBy { it.categoria }
            .map { (categoria, gruppo) -> Quota(categoria, gruppo.sumOf { it.euro }, gruppo.size) }
            // A pari importo decide l'ordine dichiarato nell'enum, cosi' due
            // categorie da dieci euro non si scambiano di posto a ogni lettura.
            .sortedWith(compareByDescending<Quota<Categoria>> { it.euro }.thenBy { it.chiave.ordinal })

        val perModalita = spese
            .groupBy { it.modalita }
            .map { (modalita, gruppo) -> Quota(modalita, gruppo.sumOf { it.euro }, gruppo.size) }
            .sortedWith(compareByDescending<Quota<Modalita>> { it.euro }.thenBy { it.chiave.ordinal })

        val perGiorno = spese
            .groupBy { it.istante.toLocalDate() }
            .map { (giorno, gruppo) -> Quota(giorno, gruppo.sumOf { it.euro }, gruppo.size) }
            .sortedBy { it.chiave }

        val valute = spese
            .filter { it.estera }
            .groupBy { it.valuta.uppercase() }
            .map { (valuta, gruppo) ->
                Cambiato(valuta, gruppo.sumOf { it.importo }, gruppo.sumOf { it.euro }, gruppo.size)
            }
            .sortedByDescending { it.euro }

        return Conto(
            spese = euro,
            carburante = carburante,
            voci = spese.size,
            perCategoria = perCategoria,
            perModalita = perModalita,
            perGiorno = perGiorno,
            valute = valute,
            giorni = giorniCoperti(perGiorno.map { it.chiave } + giorniDelCarburante),
        )
    }

    private fun giorniCoperti(giorni: List<LocalDate>): Int {
        val primo = giorni.minOrNull() ?: return 0
        val ultimo = giorni.maxOrNull() ?: return 0
        return ChronoUnit.DAYS.between(primo, ultimo).toInt() + 1
    }

    /**
     * L'ultimo cambio usato per una valuta.
     *
     * Serve a precompilare la form: il cambio del momento arriva dalla rete
     * quando c'e', ma in viaggio quasi sempre non c'e', e riscrivere 1,05 a
     * ogni caffe' e' il genere di attrito che fa smettere di registrare.
     */
    fun ultimoCambio(spese: List<Spesa>, valuta: String): Double? = spese
        .filter { it.valuta.equals(valuta, ignoreCase = true) && it.cambio != null }
        .maxByOrNull { it.istante }
        ?.cambio

    /** Le valute gia' usate nel viaggio, dalla piu' recente. */
    fun valuteUsate(spese: List<Spesa>): List<String> = spese
        .filter { it.estera }
        .sortedByDescending { it.istante }
        .map { it.valuta.uppercase() }
        .distinct()
}
