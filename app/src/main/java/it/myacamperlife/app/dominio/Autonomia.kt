package it.myacamperlife.app.dominio

import java.time.OffsetDateTime

/**
 * L'autonomia residua stimata.
 *
 * @param kmConUnPieno il parametro impostato a mano: quanto fa il mezzo con
 *   un serbatoio pieno.
 * @param kmStimati quanto si e' percorso dall'ultimo pieno, dedotto dai punti
 *   registrati.
 */
data class Autonomia(
    val kmConUnPieno: Int,
    val kmStimati: Double,
    val ultimoPieno: OffsetDateTime,
    /** Quanti punti registrati hanno contribuito alla stima. */
    val puntiUsati: Int,
) {
    val residui: Double get() = (kmConUnPieno - kmStimati).coerceAtLeast(0.0)

    val frazione: Float get() = (residui / kmConUnPieno).toFloat().coerceIn(0f, 1f)

    /**
     * Vera quando la stima non ha nulla su cui appoggiarsi: dall'ultimo pieno
     * non e' stato registrato niente. In quel caso l'autonomia mostrata e'
     * quella piena, e va detto che e' un valore di partenza, non una misura.
     */
    val senzaDati: Boolean get() = puntiUsati == 0
}

/**
 * Stima quanto manca al prossimo rifornimento.
 *
 * **Come nasce il numero.** L'app non legge il contachilometri in continuo, lo
 * conosce solo quando lo digiti a un rifornimento. Per sapere quanto hai fatto
 * da allora mette in fila tutto quello che hai registrato dopo l'ultimo pieno
 * — check-in, posizioni, foto, note — e somma le distanze fra punti
 * consecutivi.
 *
 * Usare anche foto e posizioni, e non solo i check-in sulle tappe, cambia
 * molto: una gita di quaranta chilometri fuori itinerario viene contata,
 * purche' lassu' sia stata scattata una foto o salvata la posizione. Che e'
 * quello che si fa comunque quando si va da qualche parte.
 *
 * **Resta una stima, e per costruzione ottimista**, per due ragioni che si
 * sommano: se si guida senza registrare niente quei chilometri sono
 * invisibili, e le distanze sono in linea d'aria, quindi piu' corte di quelle
 * su strada. L'interfaccia deve dirlo accanto al numero.
 *
 * Funzione pura.
 */
object StimaAutonomia {

    fun calcola(
        kmConUnPieno: Int?,
        rifornimenti: List<Rifornimento>,
        punti: List<Punto>,
        soglia: Double = Distanza.SOGLIA_KM,
    ): Autonomia? {
        if (kmConUnPieno == null || kmConUnPieno <= 0) return null

        val ultimoPieno = rifornimenti
            .filter { it.pieno }
            .maxByOrNull { it.istante }
            ?: return null

        val dopo = punti.filter { it.istante > ultimoPieno.istante }.sortedBy { it.istante }

        // Il punto di partenza e' il distributore, quando ne conosciamo la
        // posizione: cosi' il primo tratto conta dal pieno e non dal primo
        // posto in cui si e' registrato qualcosa.
        val catena = listOfNotNull(ultimoPieno.punto) + dopo

        return Autonomia(
            kmConUnPieno = kmConUnPieno,
            kmStimati = Distanza.percorsi(catena, soglia),
            ultimoPieno = ultimoPieno.istante,
            puntiUsati = dopo.size,
        )
    }
}
