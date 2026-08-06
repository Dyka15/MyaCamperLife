package it.myacamperlife.app.dominio

/**
 * Un tratto fra due pieni consecutivi: l'unico intervallo su cui un consumo
 * ha senso.
 */
data class Segmento(
    val daKm: Int,
    val aKm: Int,
    val litri: Double,
    /** Nullo se anche un solo rifornimento del tratto non aveva l'importo. */
    val euro: Double?,
) {
    val km: Int get() = aKm - daKm
    val kmPerLitro: Double get() = km / litri
    val litriPer100: Double get() = litri / km * 100
    val euroPer100: Double? get() = euro?.let { it / km * 100 }
    val euroPerKm: Double? get() = euro?.let { it / km }
}

/** Il consumo di un viaggio, tratto per tratto e in media. */
data class Consumo(val segmenti: List<Segmento>) {

    val kmTotali: Int = segmenti.sumOf { it.km }
    val litriTotali: Double = segmenti.sumOf { it.litri }

    /** Nullo se anche un solo tratto non ha gli importi al completo. */
    val euroTotali: Double? =
        if (segmenti.isEmpty() || segmenti.any { it.euro == null }) null
        else segmenti.sumOf { it.euro ?: 0.0 }

    val presente: Boolean get() = segmenti.isNotEmpty() && kmTotali > 0 && litriTotali > 0

    /**
     * La media e' **pesata sui chilometri**, non la media delle medie: un
     * tratto di 800 km non deve contare come uno di 50.
     */
    val kmPerLitro: Double? get() = if (presente) kmTotali / litriTotali else null
    val litriPer100: Double? get() = if (presente) litriTotali / kmTotali * 100 else null
    val euroPer100: Double? get() = euroTotali?.takeIf { presente }?.let { it / kmTotali * 100 }
    val euroPerKm: Double? get() = euroTotali?.takeIf { presente }?.let { it / kmTotali }
}

/**
 * Il consumo pieno-a-pieno.
 *
 * Il metodo, che e' quello standard e l'unico corretto: si prendono due pieni
 * consecutivi; i litri del tratto sono **tutti quelli messi dopo il primo
 * pieno, compreso il secondo**; i chilometri sono la differenza fra i due
 * contachilometri. I riempimenti parziali in mezzo entrano nei litri, dove
 * appartengono, invece di produrre numeri fantasiosi da soli.
 *
 * Cosa resta fuori, e perche':
 *
 * - i rifornimenti **prima del primo pieno**: non si sa quanto carburante ci
 *   fosse nel serbatoio all'inizio, quindi nessun conto e' possibile
 *   ` `
 * - i rifornimenti **dopo l'ultimo pieno**: il tratto non e' finito
 * - un tratto con chilometri o litri non positivi: e' un dato sbagliato, e
 *   viene scartato invece di inquinare la media
 *
 * Funzione pura.
 */
object Consumi {

    fun calcola(rifornimenti: List<Rifornimento>): Consumo {
        // In ordine di contachilometri: e' quello che definisce i tratti.
        // A pari chilometraggio decide l'ora, cosi' l'ordine e' sempre uno.
        val ordinati = rifornimenti.sortedWith(compareBy({ it.km }, { it.istante }))

        val segmenti = mutableListOf<Segmento>()
        var inizio: Rifornimento? = null
        var litri = 0.0
        var euro = 0.0
        var importiCompleti = true

        ordinati.forEach { rifornimento ->
            if (inizio == null) {
                // Si comincia a contare dal primo pieno, non prima.
                if (rifornimento.pieno) inizio = rifornimento
                return@forEach
            }

            litri += rifornimento.litri
            if (rifornimento.euro == null) importiCompleti = false else euro += rifornimento.euro

            if (!rifornimento.pieno) return@forEach

            val da = inizio ?: return@forEach
            val km = rifornimento.km - da.km
            if (km > 0 && litri > 0) {
                segmenti.add(
                    Segmento(
                        daKm = da.km,
                        aKm = rifornimento.km,
                        litri = litri,
                        euro = if (importiCompleti) euro else null,
                    ),
                )
            }
            inizio = rifornimento
            litri = 0.0
            euro = 0.0
            importiCompleti = true
        }

        return Consumo(segmenti)
    }

    /** Il chilometraggio piu' alto registrato: serve a precompilare la form. */
    fun ultimoChilometraggio(rifornimenti: List<Rifornimento>): Int? =
        rifornimenti.maxOfOrNull { it.km }
}
