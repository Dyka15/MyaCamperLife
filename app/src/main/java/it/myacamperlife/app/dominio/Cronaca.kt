package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una cosa accaduta, come finisce nel diario. */
data class Voce(
    val istante: OffsetDateTime,
    val genere: Genere,
    val testo: String,
    /** Il nome del file, per le foto. */
    val allegato: String? = null,
)

enum class Genere { ARRIVO, POSIZIONE, NOTA, FOTO }

/**
 * Compone la sezione di diario di una giornata a partire dalle sue voci.
 *
 * **Non e' prosa, e' una cronaca ordinata.** La prosa la scrive un modello,
 * quando c'e' rete (fase 8): questa cronaca e' sia quello che si legge nel
 * frattempo, sia l'ingresso che si da' al modello quando si vuole di meglio.
 *
 * Funzione pura: prende le voci e la data, non legge l'orologio e non tocca
 * file.
 */
object Cronaca {

    /**
     * L'intestazione porta la data in forma ISO davanti.
     *
     * Non e' un vezzo da programmatore: e' cio' che permette di ritrovare la
     * sezione di un giorno per riscriverla, e di ordinare le giornate senza
     * interpretare "giovedi 6 agosto". Resta comunque leggibile, e la parte
     * in italiano viene subito dopo.
     */
    fun intestazione(giorno: LocalDate, luogo: String? = null): String {
        val leggibile = giorno.format(GIORNO_LEGGIBILE)
        return if (luogo.isNullOrBlank()) "## $giorno — $leggibile"
        else "## $giorno — $leggibile, ${luogo.trim()}"
    }

    fun sezione(giorno: LocalDate, voci: List<Voce>, luogo: String? = null): String {
        val righe = voci.sortedBy { it.istante }.map { voce ->
            val ora = voce.istante.format(ORA)
            "- $ora · ${corpo(voce)}"
        }
        return buildString {
            appendLine(intestazione(giorno, luogo))
            appendLine()
            if (righe.isEmpty()) {
                appendLine("_Nessun evento registrato._")
            } else {
                righe.forEach { appendLine(it) }
            }
        }
    }

    private fun corpo(voce: Voce): String = when (voce.genere) {
        Genere.ARRIVO -> "arrivo a ${voce.testo}"
        Genere.POSIZIONE -> if (voce.testo.isBlank()) "posizione registrata" else voce.testo
        Genere.NOTA -> voce.testo
        Genere.FOTO -> {
            val didascalia = voce.testo.takeUnless { it.isBlank() }
            val file = voce.allegato
            when {
                didascalia != null && file != null -> "foto: $didascalia (`$file`)"
                didascalia != null -> "foto: $didascalia"
                file != null -> "foto `$file`"
                else -> "foto"
            }
        }
    }

    private val ORA = DateTimeFormatter.ofPattern("HH:mm")
    private val GIORNO_LEGGIBILE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
}
