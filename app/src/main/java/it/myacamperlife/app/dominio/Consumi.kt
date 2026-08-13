package it.myacamperlife.app.dominio

/**
 * Un tratto fra due pieni consecutivi: l'unico intervallo su cui un consumo
 * ha senso.
 */
data class Segmento(
    val km: Int,
    val litri: Double,
    /** Nullo se anche un solo rifornimento del tratto non aveva l'importo. */
    val euro: Double?,
    /**
     * Da quale a quale contachilometri, **quando si sa**.
     *
     * Lo sanno solo i tratti misurati col totale: chi registra il parziale
     * azzerato a ogni colonnina conosce la lunghezza del tratto e non la sua
     * posizione nella vita del mezzo. Il consumo non ne ha bisogno — servono
     * chilometri e litri — e mostrarlo quando c'e' e' solo un in piu'.
     */
    val daKm: Int? = null,
    val aKm: Int? = null,
) {
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
        // **In ordine di tempo**, non di contachilometri. Era il contachilometri
        // finche' era l'unica misura possibile; adesso un rifornimento puo'
        // portare solo i chilometri fatti dall'ultima colonnina, e su quelli non
        // si ordina niente. L'ora del rifornimento c'e' sempre, ed e' la stessa
        // che mette la voce nel giorno giusto del diario.
        val ordinati = rifornimenti.sortedWith(compareBy({ it.istante }, { it.km ?: 0 }))

        val segmenti = mutableListOf<Segmento>()
        var inizio: Rifornimento? = null
        var litri = 0.0
        var euro = 0.0
        var parziali = 0
        var importiCompleti = true
        var parzialiCompleti = true

        ordinati.forEach { rifornimento ->
            if (inizio == null) {
                // Si comincia a contare dal primo pieno, non prima.
                if (rifornimento.pieno) inizio = rifornimento
                return@forEach
            }

            litri += rifornimento.litri
            if (rifornimento.euro == null) importiCompleti = false else euro += rifornimento.euro
            // I parziali si sommano come i litri: un rabbocco in mezzo porta i
            // suoi chilometri nel tratto in cui li ha fatti.
            val fatti = rifornimento.kmDaPieno
            if (fatti == null) parzialiCompleti = false else parziali += fatti

            if (!rifornimento.pieno) return@forEach

            val da = inizio ?: return@forEach
            val chilometri = tratto(da, rifornimento, parziali, parzialiCompleti)
            if (chilometri != null && chilometri > 0 && litri > 0) {
                segmenti.add(
                    Segmento(
                        km = chilometri,
                        litri = litri,
                        euro = if (importiCompleti) euro else null,
                        daKm = da.km,
                        aKm = rifornimento.km,
                    ),
                )
            }
            inizio = rifornimento
            litri = 0.0
            euro = 0.0
            parziali = 0
            importiCompleti = true
            parzialiCompleti = true
        }

        return Consumo(segmenti)
    }

    /**
     * Quanti chilometri ha fatto un tratto, con la misura che c'e'.
     *
     * **Prima i parziali, poi la differenza dei contachilometri.** Non e' una
     * preferenza di gusto: il parziale azzerato alla colonnina misura
     * esattamente l'intervallo che interessa, mentre la differenza fra due
     * contachilometri lo misura solo se sono entrambi registrati e entrambi
     * giusti. Se non c'e' ne' l'una ne' l'altra misura il tratto si scarta:
     * inventare i chilometri di un consumo vorrebbe dire inventare il consumo.
     */
    private fun tratto(da: Rifornimento, a: Rifornimento, parziali: Int, completi: Boolean): Int? {
        if (completi && parziali > 0) return parziali
        val primo = da.km ?: return null
        val secondo = a.km ?: return null
        return secondo - primo
    }

}
