package it.myacamperlife.app.archivio

import java.util.Locale

/**
 * Il formato dei file dell'archivio.
 *
 * **Punto e virgola come separatore, virgola come decimale.** E' la sola
 * combinazione che non corrompe i dati: in italiano `1,72` e' un prezzo, e un
 * CSV separato da virgole lo spezzerebbe in due campi. La strada opposta —
 * punto decimale e separatore virgola — e' peggiore, perche' un foglio di
 * calcolo in locale italiano legge `1.72` come `172`.
 *
 * In scrittura il formato e' uno solo. In lettura si e' tolleranti: chi apre
 * il file in un foglio di calcolo e lo salva puo' averlo cambiato, e i suoi
 * dati non devono andare persi per questo.
 */
object Csv {

    const val SEPARATORE = ';'

    /** Colonne riservate, presenti in ogni tabella. */
    const val ID = "id"
    const val TS = "ts"
    const val CANCELLATO = "cancellato"

    fun componi(campi: List<String>): String =
        campi.joinToString(SEPARATORE.toString()) { recinta(it) }

    private fun recinta(campo: String): String {
        val pulito = testo(campo)
        val serve = pulito.any { it == SEPARATORE || it == '"' }
        return if (serve) "\"" + pulito.replace("\"", "\"\"") + "\"" else pulito
    }

    /**
     * Divide una riga nei suoi campi.
     *
     * Non gestisce i ritorni a capo dentro un campo, e non deve: la regola
     * dell'archivio e' che una riga fisica sia un record, cosi' leggere,
     * contare e concatenare non richiedono un parser con memoria.
     */
    fun dividi(riga: String): List<String> {
        val campi = mutableListOf<String>()
        val corrente = StringBuilder()
        var dentroApici = false
        var i = 0
        while (i < riga.length) {
            val c = riga[i]
            when {
                dentroApici && c == '"' && i + 1 < riga.length && riga[i + 1] == '"' -> {
                    corrente.append('"')
                    i++
                }
                c == '"' -> dentroApici = !dentroApici
                c == SEPARATORE && !dentroApici -> {
                    campi.add(corrente.toString())
                    corrente.setLength(0)
                }
                else -> corrente.append(c)
            }
            i++
        }
        campi.add(corrente.toString())
        return campi
    }

    /**
     * Rende un valore scrivibile su una riga: niente ritorni a capo, niente
     * spazi ai bordi. Le note lunghe vanno in un `.md` a parte, non qui.
     */
    fun testo(valore: String?): String = valore
        ?.replace(RITORNI_A_CAPO, " ")
        ?.trim()
        ?: ""

    /**
     * Una sequenza di ritorni a capo, non un carattere alla volta: un file
     * salvato su Windows ha `\r\n`, e sostituendo i due caratteri
     * separatamente si otterrebbero due spazi al posto di uno.
     */
    private val RITORNI_A_CAPO = "[\\r\\n]+".toRegex()

    fun numero(valore: Double, decimali: Int = 2): String =
        String.format(Locale.ROOT, "%.${decimali}f", valore).replace('.', ',')

    /**
     * Legge un numero accettando sia la virgola sia il punto come decimale.
     * Se ci sono entrambi, il punto e' un separatore di migliaia messo da un
     * foglio di calcolo e va buttato: `1.234,5` fa `1234.5`.
     */
    fun leggiNumero(campo: String?): Double? {
        // Lo spazio insecabile (\u00A0) arriva dai fogli di calcolo quando
        // formattano le migliaia: va tolto come quello normale.
        var pulito = campo?.trim()?.replace(" ", "")?.replace("\u00A0", "") ?: return null
        if (pulito.isEmpty()) return null
        if (pulito.contains(',') && pulito.contains('.')) pulito = pulito.replace(".", "")
        return pulito.replace(',', '.').toDoubleOrNull()
    }

    fun leggiIntero(campo: String?): Int? = leggiNumero(campo)?.toInt()

    fun booleano(valore: Boolean): String = if (valore) "si" else "no"

    fun leggiBooleano(campo: String?): Boolean =
        campo?.trim()?.lowercase() in VERI

    private val VERI = setOf("si", "sì", "s", "true", "vero", "1", "x", "yes")
}
