package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Il testo del riepilogo serale.
 *
 * Sta nel dominio e non nelle risorse di Android per la stessa ragione di
 * [Cronaca]: e' logica — cosa dire, in che ordine, e cosa tacere quando non
 * c'e' — e la si vuole verificare senza un telefono acceso alle 19:00.
 *
 * L'app parla una lingua sola, quindi il testo e' in italiano qui dentro. Se
 * un giorno ne parlasse due, questa e' la funzione da spostare.
 */
object TestoBriefing {

    /** La riga che si legge sulla schermata di blocco. */
    fun titolo(briefing: Briefing): String {
        val domani = briefing.domani
        return when {
            domani == null && briefing.rifornire -> "Domani conviene rifornire"
            domani == null -> "Domani nessuna tappa in programma"
            // Un giorno fermo si dice per quello che e', non si tace: "si resta a
            // Bolsena" e' quello che uno vuole sapere la sera prima.
            domani.fermo -> domani.restaA
                ?.let { "Domani si resta a $it" }
                ?: "Domani nessuno spostamento in programma"
            else -> "Domani, ${quando(domani.giorno)}: ${elenco(domani.nomi)}"
        }
    }

    /**
     * Il corpo: i chilometri, l'avviso di rifornimento, i giorni dopo.
     *
     * Ogni riga c'e' solo se ha qualcosa da dire. Una notifica con tre righe
     * vuote e' peggio di una notifica con una riga sola.
     */
    fun corpo(briefing: Briefing): String = buildList {
        strada(briefing)?.let { add(it) }
        meteo(briefing)?.let { add(it) }

        if (briefing.rifornire) add(avviso(briefing.autonomia))

        briefing.poi.forEach { giornata ->
            val cosa = when {
                !giornata.fermo -> elenco(giornata.nomi)
                giornata.restaA != null -> "si resta a ${giornata.restaA}"
                else -> "nessuno spostamento"
            }
            add("${quando(giornata.giorno)}: $cosa")
        }

        if (briefing.senzaData.isNotEmpty()) {
            add("Ancora da fare: ${elenco(briefing.senzaData.map { it.nome })}")
        }
    }.joinToString("\n")

    /**
     * I chilometri di domani, e **da dove viene il numero**.
     *
     * Con le tratte precalcolate si dicono i chilometri veri e il tempo di
     * guida; senza, si dice la linea d'aria dichiarandola per quello che e'.
     * Non e' una cautela di stile: sul secondo numero non si decide se un
     * serbatoio basta.
     */
    private fun strada(briefing: Briefing): String? {
        val km = briefing.kmDomani ?: return null
        if (!briefing.suStrada) {
            return "Circa ${arrotonda(km)} km in linea d'aria, quindi qualcuno in piu' su strada."
        }
        val minuti = briefing.minutiDomani
        return if (minuti == null) "${arrotonda(km)} km su strada."
        else "${arrotonda(km)} km, ${Percorso(km, minuti).durata} di guida."
    }

    /** Il meteo di domani, con l'eta' del dato appiccicata dietro. */
    private fun meteo(briefing: Briefing): String? {
        val previsione = briefing.meteoDomani ?: return null
        val riga = TestoMeteo.riga(previsione)
        val eta = TestoMeteo.eta(briefing.meteoOreFa)
        return if (eta == null) riga else "$riga · $eta"
    }

    private fun avviso(autonomia: Autonomia?): String {
        val residui = autonomia?.residui ?: return "Conviene rifornire."
        return "Rifornisci: l'autonomia stimata e' di circa ${arrotonda(residui)} km."
    }

    /**
     * "domenica 9 agosto". Il giorno della settimana c'e' perche' e' come si
     * pensa a un viaggio; il numero perche' e' come si legge un itinerario.
     */
    private fun quando(giorno: LocalDate): String = giorno.format(GIORNO)

    /** "Viterbo e Roma", non "Viterbo, Roma": e' una frase, non una tabella. */
    private fun elenco(nomi: List<String>): String = when (nomi.size) {
        0 -> "nessuna tappa"
        1 -> nomi.first()
        else -> nomi.dropLast(1).joinToString(", ") + " e " + nomi.last()
    }

    private fun arrotonda(km: Double): Int = km.roundToInt()

    private val GIORNO = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
}
