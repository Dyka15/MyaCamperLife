package it.myacamperlife.app.archivio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Una riga letta da una tabella. I campi si prendono per nome, mai per
 * posizione: e' questo che permette di aggiungere una colonna senza rompere
 * i file scritti prima.
 */
class Riga(private val valori: Map<String, String>) {

    fun testo(colonna: String): String? = valori[colonna]?.takeUnless { it.isEmpty() }
    fun numero(colonna: String): Double? = Csv.leggiNumero(valori[colonna])
    fun intero(colonna: String): Int? = Csv.leggiIntero(valori[colonna])
    fun booleano(colonna: String): Boolean = Csv.leggiBooleano(valori[colonna])

    val id: String? get() = testo(Csv.ID)
    val ts: String get() = valori[Csv.TS].orEmpty()
    val cancellata: Boolean get() = booleano(Csv.CANCELLATO)

    /**
     * L'istante assoluto della riga, quando `ts` e' leggibile.
     *
     * Confrontare i `ts` come testo sarebbe sbagliato appena si cambia fuso:
     * `13:30+01:00` viene dopo `14:00+02:00`, ma come stringa sembra prima.
     * Per un'app da viaggio non e' un caso di scuola.
     */
    val istante: Instant?
        get() = runCatching { OffsetDateTime.parse(ts).toInstant() }.getOrNull()

    fun mappa(): Map<String, String> = valori
}

/**
 * Un file CSV dell'archivio: si accodano righe, non si riscrive.
 *
 * Le tre proprieta' che questo disegno regala:
 *
 * - **a prova di crash** — una scrittura e' `append` piu' `fsync`, quindi non
 *   esiste l'istante in cui il file e' a meta'. In camper il telefono si
 *   spegne per mille motivi
 * - **correggere non distrugge** — una modifica e' una riga nuova con lo stesso
 *   `id`; vince quella con `ts` piu' recente. La cancellazione e' una riga
 *   lapide
 * - **fondibile** — due copie si uniscono concatenandole e riapplicando la
 *   stessa regola, quindi sincronizzare fra due dispositivi un domani non
 *   richiede codice di fusione
 *
 * L'intestazione scritta nel file e' l'autorita' per la lettura, non l'elenco
 * dichiarato dal codice: un file piu' vecchio ha meno colonne e va letto
 * comunque.
 */
class Tabella(val file: File, val colonne: List<String>) {

    init {
        require(colonne.take(3) == listOf(Csv.ID, Csv.TS, Csv.CANCELLATO)) {
            "ogni tabella comincia con id, ts, cancellato: $colonne"
        }
    }

    /** Aggiunge una riga in fondo. I valori assenti restano vuoti. */
    fun accoda(riga: Map<String, String>) = accodaTutte(listOf(riga))

    fun accodaTutte(righe: List<Map<String, String>>) {
        if (righe.isEmpty()) return
        val intestazione = preparaPerScrivere()
        val testo = buildString {
            righe.forEach { riga ->
                append(Csv.componi(intestazione.map { riga[it].orEmpty() }))
                append('\n')
            }
        }
        FileOutputStream(file, true).use { flusso ->
            flusso.write(testo.toByteArray(Charsets.UTF_8))
            flusso.flush()
            flusso.fd.sync()
        }
    }

    /** Tutte le righe come stanno nel file, comprese quelle superate. */
    fun righe(): List<Riga> {
        val linee = linee()
        if (linee.isEmpty()) return emptyList()
        val intestazione = Csv.dividi(linee.first()).map { it.trim() }
        return linee.drop(1).map { linea ->
            val campi = Csv.dividi(linea)
            Riga(intestazione.mapIndexed { i, nome -> nome to campi.getOrElse(i) { "" } }.toMap())
        }
    }

    /** Le righe che contano: l'ultima versione di ciascun `id`, senza lapidi. */
    fun vive(): List<Riga> = risolvi(righe())

    /**
     * Riscrive il file tenendo solo le righe vive. Serve quando le correzioni
     * si accumulano e la vista da foglio di calcolo si fa confusa; non e'
     * necessario al funzionamento.
     */
    fun compatta() {
        if (!file.exists()) return
        val intestazione = intestazioneEffettiva()
        val vive = vive()
        scriviAtomico { scrittore ->
            scrittore.append(Csv.componi(intestazione)).append('\n')
            vive.forEach { riga ->
                scrittore.append(Csv.componi(intestazione.map { riga.mappa()[it].orEmpty() })).append('\n')
            }
        }
    }

    private fun linee(): List<String> =
        if (!file.exists()) emptyList()
        else file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }

    private fun intestazioneDelFile(): List<String> =
        linee().firstOrNull()
            ?.let { Csv.dividi(it) }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /** L'intestazione del file, piu' le colonne che il codice ha imparato dopo. */
    private fun intestazioneEffettiva(): List<String> {
        val presenti = intestazioneDelFile()
        if (presenti.isEmpty()) return colonne
        return presenti + colonne.filterNot { it in presenti }
    }

    /**
     * Prepara il file per un `append` e restituisce l'intestazione da seguire.
     *
     * Fa tre cose, tutte necessarie:
     *
     * 1. crea il file con l'intestazione, se non c'e'
     * 2. **allarga l'intestazione** se il codice ha colonne nuove: riscrive solo
     *    la prima riga e ricopia il resto tale e quale, cosi' le righe vecchie
     *    restano piu' corte e la lettura per nome le gestisce da sola. Nessuna
     *    migrazione dei dati, mai
     * 3. chiude una riga tronca: se l'ultimo byte non e' un ritorno a capo, un
     *    `append` si attaccherebbe in coda alla riga precedente. Succede se il
     *    telefono si spegne durante una scrittura, che e' esattamente il caso
     *    per cui questo formato e' stato scelto
     */
    private fun preparaPerScrivere(): List<String> {
        file.parentFile?.mkdirs()
        if (!file.exists() || file.length() == 0L) {
            file.writeText(Csv.componi(colonne) + "\n", Charsets.UTF_8)
            return colonne
        }

        chiudiRigaTronca()

        val presenti = intestazioneDelFile()
        val mancanti = colonne.filterNot { it in presenti }
        if (mancanti.isEmpty()) return presenti

        val allargata = presenti + mancanti
        val resto = linee().drop(1)
        scriviAtomico { scrittore ->
            scrittore.append(Csv.componi(allargata)).append('\n')
            resto.forEach { scrittore.append(it).append('\n') }
        }
        return allargata
    }

    private fun chiudiRigaTronca() {
        // Con RandomAccessFile e non con skip(): skip() puo' saltare meno
        // byte di quelli chiesti, e qui leggeremmo il carattere sbagliato.
        val ultimo = RandomAccessFile(file, "r").use { accesso ->
            accesso.seek(file.length() - 1)
            accesso.read()
        }
        if (ultimo != '\n'.code) {
            FileOutputStream(file, true).use { flusso ->
                flusso.write('\n'.code)
                flusso.fd.sync()
            }
        }
    }

    /**
     * Scrive su un file d'appoggio e lo rinomina sopra l'originale: un
     * rinomina e' atomico, quindi non esiste il momento in cui il file
     * dell'archivio e' a meta'.
     */
    private fun scriviAtomico(corpo: (Appendable) -> Unit) {
        val temporaneo = File(file.parentFile, "${file.name}.nuovo")
        temporaneo.bufferedWriter(Charsets.UTF_8).use { scrittore -> corpo(scrittore) }
        if (!temporaneo.renameTo(file)) {
            // Alcuni filesystem rifiutano di sovrascrivere: si cancella prima.
            file.delete()
            check(temporaneo.renameTo(file)) { "non riesco a sostituire ${file.name}" }
        }
    }

    companion object {
        /**
         * La regola "vince l'ultima": per ogni `id` si tiene la riga con `ts`
         * piu' recente, a pari merito l'ultima incontrata; le lapidi
         * spariscono.
         *
         * L'ordine di uscita e' quello di prima apparizione, non quello della
         * correzione: modificare una riga non deve spostarla in fondo
         * all'elenco.
         *
         * Funzione pura: e' il cuore del formato e va verificata da sola.
         */
        fun risolvi(righe: List<Riga>): List<Riga> {
            val ultime = LinkedHashMap<String, Riga>()
            righe.forEach { riga ->
                val chiave = riga.id ?: return@forEach
                val precedente = ultime[chiave]
                if (precedente == null || !precedente.piuRecenteDi(riga)) {
                    ultime[chiave] = riga
                }
            }
            return ultime.values.filterNot { it.cancellata }
        }

        /**
         * Vero se questa riga e' piu' recente dell'[altra]. A pari istante e'
         * falso, cosi' chi arriva dopo nel file vince: e' l'ordine in cui le
         * correzioni sono state scritte.
         */
        private fun Riga.piuRecenteDi(altra: Riga): Boolean {
            val mio = istante
            val suo = altra.istante
            return if (mio != null && suo != null) mio > suo else ts > altra.ts
        }
    }
}
