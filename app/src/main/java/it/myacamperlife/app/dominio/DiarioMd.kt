package it.myacamperlife.app.dominio

import java.time.LocalDate

/**
 * Il diario e' **un unico file per viaggio**, con una sezione per giorno.
 *
 * E' l'unico file dell'archivio che non si accoda soltanto: una giornata
 * nuova va in fondo, ma rigenerarne una vecchia significa riscrivere una
 * sezione **in mezzo** al file. Queste funzioni fanno quel lavoro sul testo;
 * la scrittura atomica sul disco sta in `archivio/Diario.kt`.
 *
 * Tutto puro, quindi verificabile: sono le operazioni in cui e' facile
 * mangiarsi una riga senza accorgersene.
 */
object DiarioMd {

    /** Una parte del documento: il preambolo, oppure la sezione di un giorno. */
    private data class Pezzo(val giorno: LocalDate?, val testo: String)

    /**
     * Sostituisce la sezione di [giorno] con [sezione], o la inserisce al
     * posto giusto se non c'era.
     *
     * L'inserimento e' **in ordine di data**, non in fondo: se si rigenera il
     * diario di ieri dopo aver registrato oggi, la giornata deve finire dove
     * le tocca. Tutto quello che non e' una sezione di giorno — il titolo del
     * viaggio, un testo scritto a mano — resta dov'e'.
     */
    fun sostituisci(contenuto: String, giorno: LocalDate, sezione: String): String {
        val pezzi = dividi(contenuto).toMutableList()
        val nuovo = Pezzo(giorno, sezione.trimEnd() + "\n")

        val esistente = pezzi.indexOfFirst { it.giorno == giorno }
        if (esistente >= 0) {
            pezzi[esistente] = nuovo
        } else {
            val dopo = pezzi.indexOfFirst { it.giorno != null && it.giorno > giorno }
            if (dopo >= 0) pezzi.add(dopo, nuovo) else pezzi.add(nuovo)
        }

        return pezzi
            .map { it.testo.trimEnd() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n") + "\n"
    }

    /** I giorni che il documento contiene, in ordine di apparizione. */
    fun giorni(contenuto: String): List<LocalDate> = dividi(contenuto).mapNotNull { it.giorno }

    /** La sezione di un giorno, se c'e'. */
    fun sezione(contenuto: String, giorno: LocalDate): String? =
        dividi(contenuto).firstOrNull { it.giorno == giorno }?.testo?.trimEnd()

    private fun dividi(contenuto: String): List<Pezzo> {
        if (contenuto.isBlank()) return emptyList()
        val pezzi = mutableListOf<Pezzo>()
        val corrente = StringBuilder()
        var giornoCorrente: LocalDate? = null

        fun chiudi() {
            if (corrente.isNotBlank()) pezzi.add(Pezzo(giornoCorrente, corrente.toString()))
            corrente.setLength(0)
        }

        contenuto.lineSequence().forEach { linea ->
            val giorno = giornoDiIntestazione(linea)
            if (giorno != null) {
                chiudi()
                giornoCorrente = giorno
            }
            corrente.append(linea).append('\n')
        }
        chiudi()
        return pezzi
    }

    /**
     * La data di un'intestazione `## 2026-08-06 — ...`, oppure `null` se la
     * riga non e' un'intestazione di giornata. Una sezione scritta a mano con
     * un titolo qualsiasi non viene interpretata: resta un pezzo di testo.
     */
    private fun giornoDiIntestazione(linea: String): LocalDate? {
        if (!linea.startsWith("## ")) return null
        val inizio = linea.removePrefix("## ").trimStart().take(10)
        return runCatching { LocalDate.parse(inizio) }.getOrNull()
    }
}
