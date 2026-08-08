package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una cosa accaduta, come finisce nel diario.
 *
 * @param id l'identificativo della riga da cui viene, quando si sa. Il diario
 *   non lo stampa — una cronaca non porta identificativi — ma senza di esso una
 *   voce e' un pezzo di testo su cui non si puo' tornare: e' l'`id` che permette
 *   di correggerla o di cancellarla, accodando una riga con lo stesso.
 */
data class Voce(
    val istante: OffsetDateTime,
    val genere: Genere,
    val testo: String,
    /** Il nome del file, per le foto. */
    val allegato: String? = null,
    val id: String? = null,
) {
    /**
     * Su cosa si puo' tornare.
     *
     * Arrivi e posizioni si cancellano ma non si correggono: il loro contenuto
     * **e' il fatto stesso** — sei arrivato, eri li' — e riscriverlo non vorrebbe
     * dire niente. Lo stato di una tappa si cambia dalla sua scheda, che e' il
     * posto dove quel gesto ha un senso.
     */
    val correggibile: Boolean
        get() = id != null && genere in CORREGGIBILI

    val cancellabile: Boolean get() = id != null

    private companion object {
        val CORREGGIBILI = setOf(Genere.NOTA, Genere.FOTO, Genere.RIFORNIMENTO, Genere.SPESA)
    }
}

enum class Genere { ARRIVO, POSIZIONE, NOTA, FOTO, RIFORNIMENTO, SPESA }

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
        // Il testo del rifornimento arriva gia' composto: formattare litri
        // ed euro richiede le regole del CSV, che stanno nell'archivio, e il
        // dominio non deve dipendere da quello.
        Genere.RIFORNIMENTO -> voce.testo.ifBlank { "rifornimento" }
        Genere.SPESA -> voce.testo.ifBlank { "spesa" }
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
