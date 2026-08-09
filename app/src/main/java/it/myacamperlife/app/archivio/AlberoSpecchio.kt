package it.myacamperlife.app.archivio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * La cartella scelta dall'utente, letta come [Albero].
 *
 * E' l'unico punto dell'app in cui si **legge** da SAF, e serve a una cosa sola:
 * la fusione, quando si assegna una cartella che contiene gia' un archivio.
 * Tutto il resto continua ad andare nell'altro verso.
 *
 * **L'inventario si fa una volta.** Su SAF ogni `listFiles()` e' una
 * interrogazione al provider, e risolvere un percorso scendendo dalla radice
 * ogni volta significherebbe una interrogazione per livello per ogni file. Qui
 * si percorre l'albero una volta e si tiene una mappa da percorso a documento:
 * l'archivio e' fatto di poche cartelle, e il costo si paga una volta sola.
 */
class AlberoSpecchio(
    private val context: Context,
    private val radice: Uri,
) : Albero {

    private val documenti: Map<String, DocumentFile> by lazy { inventario() }

    override fun elenca(): List<String> = documenti.keys.sorted()

    override fun testo(percorso: String): String? {
        val documento = documenti[percorso] ?: return null
        return runCatching {
            context.contentResolver.openInputStream(documento.uri)?.use { flusso ->
                flusso.reader(Charsets.UTF_8).readText()
            }
        }.getOrNull()
    }

    override fun copia(percorso: String, destinazione: File): Boolean {
        val documento = documenti[percorso] ?: return false
        return runCatching {
            destinazione.parentFile?.mkdirs()
            context.contentResolver.openInputStream(documento.uri)?.use { ingresso ->
                destinazione.outputStream().use { uscita -> ingresso.copyTo(uscita) }
            } != null
        }.getOrDefault(false)
    }

    /**
     * Percorre l'albero e mappa ogni file al suo percorso relativo.
     *
     * Il tetto sulla profondita' non e' pedanteria: un provider SAF puo'
     * restituire strutture che l'app non ha creato — una cartella condivisa,
     * un cloud con collegamenti — e un albero che si ripiega su se stesso
     * manderebbe questa funzione a girare per sempre.
     */
    private fun inventario(): Map<String, DocumentFile> {
        val partenza = runCatching { DocumentFile.fromTreeUri(context, radice) }.getOrNull()
            ?: return emptyMap()
        if (!partenza.isDirectory) return emptyMap()

        val trovati = LinkedHashMap<String, DocumentFile>()

        fun scendi(cartella: DocumentFile, prefisso: String, profondita: Int) {
            if (profondita > PROFONDITA_MASSIMA) return
            val figli = runCatching { cartella.listFiles() }.getOrNull() ?: return
            figli.forEach { figlio ->
                val nome = figlio.name ?: return@forEach
                val percorso = if (prefisso.isEmpty()) nome else "$prefisso${Albero.SEPARATORE}$nome"
                if (figlio.isDirectory) {
                    scendi(figlio, percorso, profondita + 1)
                } else {
                    trovati[percorso] = figlio
                }
            }
        }

        scendi(partenza, "", 0)
        return trovati
    }

    private companion object {
        /** L'archivio e' profondo tre livelli: `viaggi/<slug>/scorta/`. */
        const val PROFONDITA_MASSIMA = 5
    }
}
