package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Carburante
import it.myacamperlife.app.dominio.Cronaca
import it.myacamperlife.app.dominio.DiarioMd
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Voce
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Scrive `diario.md`, un file per viaggio.
 *
 * Le voci si ricavano dalle tabelle: il diario e' una **vista** degli eventi,
 * non una loro copia. Se il file venisse cancellato si rigenera per intero,
 * e per la stessa ragione rigenerare una giornata due volte da' lo stesso
 * risultato.
 */
class Diario(private val file: File) {

    private companion object {
        /**
         * La riga che marca una giornata riscritta da un modello.
         *
         * Non e' un disclaimer di cortesia: un diario e' un documento, e chi lo
         * rilegge deve poter distinguere quello che e' stato registrato da
         * quello che ci e' stato scritto sopra.
         */
        const val FIRMA = "_Riscritta in prosa da un modello, dagli eventi registrati._"
    }

    /** Riscrive la sezione di [giorno], lasciando intatto il resto del file. */
    fun aggiorna(giorno: LocalDate, voci: List<Voce>, luogo: String?, titolo: String?) {
        val esistente = if (file.exists()) file.readText(Charsets.UTF_8) else preambolo(titolo)
        val sezione = Cronaca.sezione(giorno, voci, luogo)
        scriviAtomico(DiarioMd.sostituisci(esistente, giorno, sezione))
    }

    /**
     * Sostituisce la sezione di [giorno] con la prosa di un modello.
     *
     * L'intestazione resta quella di sempre — data ISO davanti, luogo dietro —
     * perche' e' quella che permette di ritrovare e riscrivere la sezione. Sotto
     * la prosa si aggiunge una riga che dice **da dove viene**: fra sei mesi,
     * rileggendo, la differenza fra quello che e' stato registrato e quello che
     * un modello ha scritto sopra deve restare visibile.
     */
    fun scriviProsa(giorno: LocalDate, prosa: String, luogo: String?, titolo: String?) {
        val esistente = if (file.exists()) file.readText(Charsets.UTF_8) else preambolo(titolo)
        val sezione = buildString {
            appendLine(Cronaca.intestazione(giorno, luogo))
            appendLine()
            appendLine(prosa.trim())
            appendLine()
            appendLine(FIRMA)
        }
        scriviAtomico(DiarioMd.sostituisci(esistente, giorno, sezione))
    }

    fun testo(): String = if (file.exists()) file.readText(Charsets.UTF_8) else ""

    private fun preambolo(titolo: String?): String =
        if (titolo.isNullOrBlank()) "" else "# ${titolo.trim()}\n"

    /**
     * Copia e rinomina: il rinomina e' atomico, quindi non esiste l'istante in
     * cui il diario e' a meta'. E' l'unico file dell'archivio che si riscrive
     * invece di accodarsi, quindi e' l'unico che ne ha bisogno.
     */
    private fun scriviAtomico(contenuto: String) {
        file.parentFile?.mkdirs()
        val temporaneo = File(file.parentFile, "${file.name}.nuovo")
        temporaneo.writeText(contenuto, Charsets.UTF_8)
        if (!temporaneo.renameTo(file)) {
            file.delete()
            check(temporaneo.renameTo(file)) { "non riesco a sostituire ${file.name}" }
        }
    }
}

/**
 * Le voci di diario ricavate dalle tabelle di un viaggio.
 *
 * Sta fuori da [Diario] perche' e' una trasformazione fra tabelle e dominio,
 * e non ha bisogno di sapere dove finisce il testo.
 */
object VociDelGiorno {

    fun tutte(
        spostamenti: List<Riga>,
        note: List<Riga>,
        foto: List<Riga>,
        rifornimenti: List<Riga> = emptyList(),
        spese: List<Riga> = emptyList(),
    ): List<Voce> = buildList {
        spostamenti.forEach { riga ->
            val istante = istante(riga) ?: return@forEach
            val arrivo = riga.testo(SpostamentiTabella.GENERE) == SpostamentiTabella.ARRIVO
            add(
                Voce(
                    istante = istante,
                    genere = if (arrivo) Genere.ARRIVO else Genere.POSIZIONE,
                    testo = riga.testo(SpostamentiTabella.TAPPA)
                        ?: riga.testo(SpostamentiTabella.NOTA)
                        ?: "",
                    id = riga.id,
                ),
            )
        }
        note.forEach { riga ->
            val istante = istante(riga) ?: return@forEach
            val testo = riga.testo(NoteTabella.TESTO) ?: return@forEach
            add(Voce(istante, Genere.NOTA, testo, id = riga.id))
        }
        foto.forEach { riga ->
            val istante = istante(riga) ?: return@forEach
            add(
                Voce(
                    istante = istante,
                    genere = Genere.FOTO,
                    testo = riga.testo(FotoTabella.DIDASCALIA).orEmpty(),
                    allegato = riga.testo(FotoTabella.FILE),
                    id = riga.id,
                ),
            )
        }
        rifornimenti.forEach { riga ->
            val istante = istante(riga) ?: return@forEach
            add(Voce(istante, Genere.RIFORNIMENTO, descrizioneRifornimento(riga), id = riga.id))
        }
        spese.forEach { riga ->
            val istante = istante(riga) ?: return@forEach
            add(
                Voce(
                    istante = istante,
                    genere = Genere.SPESA,
                    testo = descrizioneSpesa(riga),
                    allegato = riga.testo(SpeseTabella.SCONTRINO),
                    id = riga.id,
                ),
            )
        }
    }.sortedBy { it.istante }

    /**
     * "Pieno a Orvieto: 62,3 litri, 107,16 EUR".
     *
     * Il testo si compone qui e non in Cronaca perche' formattare i numeri con
     * la virgola decimale e' una regola dell'archivio, e il dominio non deve
     * dipendere dall'archivio.
     */
    private fun descrizioneRifornimento(riga: Riga): String {
        val pieno = riga.booleano(RifornimentiTabella.PIENO)
        val luogo = riga.testo(RifornimentiTabella.LUOGO)
        val euro = riga.numero(RifornimentiTabella.EURO)
        val prezzo = riga.numero(RifornimentiTabella.PREZZO_LITRO)
        // Come nell'archivio: i litri si rifanno da importo e prezzo, e la
        // colonna vale da ripiego per le righe scritte prima del prezzo.
        val litri = Carburante.litri(euro, prezzo) ?: riga.numero(RifornimentiTabella.LITRI)

        val testa = if (pieno) "pieno" else "rifornimento"
        val dove = luogo?.let { " a $it" }.orEmpty()
        val quanto = listOfNotNull(
            euro?.let { "${Csv.numero(it)} \u20AC" },
            prezzo?.let { "${Csv.numero(it, 3)} \u20AC/l" },
            litri?.let { "${Csv.numero(it, 1)} litri" },
        ).joinToString(", ")

        return if (quanto.isEmpty()) "$testa$dove" else "$testa$dove: $quanto"
    }

    /**
     * "Sosta — area Il Cipresso: 18,00 EUR (contanti)", e in valuta estera
     * "ristorante: 45,00 CHF = 47,70 EUR (carta)".
     *
     * Una spesa estera porta nel diario **tutti e due i numeri**: quello dello
     * scontrino, che e' l'unico verificabile, e quello in euro, che e' l'unico
     * confrontabile.
     */
    private fun descrizioneSpesa(riga: Riga): String {
        val categoria = riga.testo(SpeseTabella.CATEGORIA) ?: "spesa"
        val descrizione = riga.testo(SpeseTabella.DESCRIZIONE)
        val importo = riga.numero(SpeseTabella.IMPORTO)
        val valuta = riga.testo(SpeseTabella.VALUTA) ?: "EUR"
        val euro = riga.numero(SpeseTabella.EURO)
        val modalita = riga.testo(SpeseTabella.MODALITA)

        val cosa = categoria + (descrizione?.let { " — $it" }.orEmpty())
        val quanto = when {
            importo == null -> null
            valuta.equals("EUR", ignoreCase = true) -> "${Csv.numero(importo)} €"
            euro != null -> "${Csv.numero(importo)} $valuta = ${Csv.numero(euro)} €"
            else -> "${Csv.numero(importo)} $valuta"
        }
        val come = modalita?.let { " ($it)" }.orEmpty()

        return if (quanto == null) cosa else "$cosa: $quanto$come"
    }

    /** I giorni che hanno almeno una voce. */
    fun giorni(voci: List<Voce>): List<LocalDate> =
        voci.map { it.istante.toLocalDate() }.distinct().sorted()

    fun delGiorno(voci: List<Voce>, giorno: LocalDate): List<Voce> =
        voci.filter { it.istante.toLocalDate() == giorno }

    /**
     * Quando e' accaduto il fatto, non quando la riga e' stata scritta.
     *
     * E' la differenza che permette di registrare stasera lo scontrino di ieri
     * e vederlo comparire nella giornata di ieri.
     */
    private fun istante(riga: Riga): OffsetDateTime? = riga.quando
}
