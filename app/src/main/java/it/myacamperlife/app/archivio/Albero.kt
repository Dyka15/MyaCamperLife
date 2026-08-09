package it.myacamperlife.app.archivio

import java.io.File

/**
 * Un albero di file da cui si puo' **leggere**, qualunque cosa ci sia sotto.
 *
 * Esiste per una ragione sola, e vale scriverla: la fusione di due archivi e'
 * la parte piu' delicata di tutta l'app — tocca dati che l'utente non puo'
 * ricostruire — e sopra un albero SAF non si puo' verificare, perche' SAF non
 * esiste fuori da un telefono. Con questa interfaccia la logica di fusione
 * lavora su una cartella normale nei test e su un albero SAF sul telefono,
 * ed e' **la stessa logica**.
 *
 * Solo lettura di proposito: la fusione legge da fuori e scrive dentro l'area
 * privata, che resta l'autorita'. Il ritorno verso la cartella lo fa lo
 * specchio, dopo, come sempre.
 */
interface Albero {

    /** I percorsi relativi di tutti i file, cartelle escluse. */
    fun elenca(): List<String>

    /** Il contenuto testuale di un file, o `null` se non si riesce a leggerlo. */
    fun testo(percorso: String): String?

    /**
     * Copia un file dell'albero su [destinazione].
     *
     * Serve a quello che non e' testo — foto, scontrini — dove leggere tutto in
     * memoria per riscriverlo sarebbe uno spreco e su uno scatto da dodici
     * megapixel un rischio.
     */
    fun copia(percorso: String, destinazione: File): Boolean

    companion object {
        /** Il separatore dei percorsi relativi. Sempre `/`, anche su SAF. */
        const val SEPARATORE = "/"
    }
}

/**
 * Un albero che e' una cartella vera.
 *
 * Lo usano i test, e sarebbe utilizzabile anche per importare da una cartella
 * raggiungibile senza SAF, se un domani ce ne fosse una.
 */
class AlberoDiFile(private val radice: File) : Albero {

    override fun elenca(): List<String> {
        if (!radice.isDirectory) return emptyList()
        return radice.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(radice).path.replace(File.separator, Albero.SEPARATORE) }
            .sorted()
            .toList()
    }

    override fun testo(percorso: String): String? {
        val file = dentro(percorso) ?: return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    override fun copia(percorso: String, destinazione: File): Boolean {
        val file = dentro(percorso) ?: return false
        return runCatching {
            destinazione.parentFile?.mkdirs()
            file.copyTo(destinazione, overwrite = true)
            true
        }.getOrDefault(false)
    }

    /**
     * Il file di un percorso relativo, **solo se sta davvero sotto la radice**.
     *
     * Un percorso che contiene `..` risalirebbe fuori dall'albero, e questa
     * classe legge percorsi che in produzione arrivano da un albero scelto
     * dall'utente: e' il genere di controllo che non costa niente e la cui
     * assenza si scopre male.
     */
    private fun dentro(percorso: String): File? {
        val file = File(radice, percorso.replace(Albero.SEPARATORE, File.separator))
        val atteso = radice.canonicalPath + File.separator
        val vero = runCatching { file.canonicalPath }.getOrNull() ?: return null
        if (!vero.startsWith(atteso)) return null
        return file.takeIf { it.isFile }
    }
}
