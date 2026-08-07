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
        briefing.kmDomani?.let { km ->
            // "Circa" e "in linea d'aria" non sono cautele di stile: quel
            // numero e' una sottostima, e chi guida deve saperlo.
            add("Circa ${arrotonda(km)} km in linea d'aria, quindi qualcuno in piu' su strada.")
        }

        if (briefing.rifornire) add(avviso(briefing.autonomia))

        briefing.poi.forEach { giornata ->
            add("${quando(giornata.giorno)}: ${elenco(giornata.nomi)}")
        }

        if (briefing.senzaData.isNotEmpty()) {
            add("Ancora da fare: ${elenco(briefing.senzaData.map { it.nome })}")
        }
    }.joinToString("\n")

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
