package it.myacamperlife.app.archivio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Com'e' andata una passata di specchio. */
data class EsitoSpecchio(val copiati: Int, val gia: Int, val falliti: Int) {
    val riuscito: Boolean get() = falliti == 0
    val toccati: Int get() = copiati + gia
}

/**
 * Copia l'archivio nella cartella scelta dall'utente.
 *
 * **Perche' serve.** La copia di lavoro sta nell'area privata dell'app, dove
 * funziona sempre e non chiede permessi — ma dove nessun gestore file arriva.
 * Finche' resta solo li', il terzo principio del progetto ("i file sono il
 * prodotto") e' vero nel codice e falso in pratica: si scrivono CSV che nessuno
 * puo' aprire.
 *
 * **Lo specchio e' una copia, non l'originale.** L'app continua a leggere e
 * scrivere nell'area privata; questa classe ricopia fuori quello che e'
 * cambiato. E' il secondo invariante dell'architettura: *la scrittura non
 * aspetta niente*. Se la cartella scelta e' su una chiavetta staccata, o su un
 * cloud senza rete, o l'utente ha revocato il permesso, la registrazione
 * riesce comunque e lo specchio si rifara' dopo.
 *
 * **Perche' non usare direttamente la cartella scelta.** Su un albero SAF non
 * esiste `append`: ogni scrittura riapre, rilegge e riscrive il documento
 * intero, senza `fsync` garantito e senza atomicita'. Tutte le proprieta' del
 * formato — a prova di crash, correggere non distrugge — verrebbero meno. La
 * copia di lavoro locale resta l'autorita'.
 */
class Specchio(private val context: Context, private val radiceSpecchio: Uri) {

    /**
     * Ricopia [radice] nella cartella scelta.
     *
     * Si copia un file quando **manca o ha una dimensione diversa**. Non si
     * confrontano le date: un provider SAF puo' non riportarle, e su Drive la
     * data del documento e' quella del caricamento, non del contenuto. La
     * dimensione e' grezza ma qui basta, perche' i file dell'archivio crescono
     * a ogni riga scritta.
     *
     * Le foto e gli scontrini si copiano solo se mancano: non cambiano mai, e
     * ricopiarli a ogni passata vorrebbe dire ricaricare megabyte su un cloud
     * per nulla.
     */
    fun rispecchia(radice: File): EsitoSpecchio {
        val destinazione = DocumentFile.fromTreeUri(context, radiceSpecchio)
            ?: return EsitoSpecchio(0, 0, 1)
        if (!destinazione.canWrite()) return EsitoSpecchio(0, 0, 1)
        if (!radice.isDirectory) return EsitoSpecchio(0, 0, 0)

        var copiati = 0
        var gia = 0
        var falliti = 0

        fun scendi(cartella: File, dentro: DocumentFile) {
            val figli = cartella.listFiles() ?: return
            figli.sortedBy { it.name }.forEach { figlio ->
                if (figlio.isDirectory) {
                    val sotto = cartellaDentro(dentro, figlio.name)
                    if (sotto == null) falliti++ else scendi(figlio, sotto)
                    return@forEach
                }
                // I file d'appoggio della scrittura atomica non si rispecchiano:
                // esistono per una frazione di secondo e non sono archivio.
                if (figlio.name.endsWith(".nuovo")) return@forEach

                when (copia(figlio, dentro)) {
                    Esito.COPIATO -> copiati++
                    Esito.GIA_UGUALE -> gia++
                    Esito.FALLITO -> falliti++
                }
            }
        }

        scendi(radice, destinazione)
        return EsitoSpecchio(copiati, gia, falliti)
    }

    private enum class Esito { COPIATO, GIA_UGUALE, FALLITO }

    private fun copia(sorgente: File, dentro: DocumentFile): Esito {
        val esistente = dentro.findFile(sorgente.name)
        if (esistente != null && esistente.length() == sorgente.length()) return Esito.GIA_UGUALE

        val documento = esistente
            ?: dentro.createFile(tipo(sorgente.name), sorgente.name)
            ?: return Esito.FALLITO

        return try {
            context.contentResolver.openOutputStream(documento.uri, "wt").use { uscita ->
                if (uscita == null) return Esito.FALLITO
                sorgente.inputStream().use { ingresso -> ingresso.copyTo(uscita) }
            }
            Esito.COPIATO
        } catch (e: java.io.IOException) {
            // Chiavetta staccata, cloud senza rete, spazio finito: si riprova
            // alla passata dopo. La copia di lavoro e' intatta.
            Esito.FALLITO
        } catch (e: SecurityException) {
            // Permesso revocato dall'utente o dopo una reinstallazione.
            Esito.FALLITO
        }
    }

    private fun cartellaDentro(dentro: DocumentFile, nome: String): DocumentFile? {
        val esistente = dentro.findFile(nome)
        if (esistente != null && esistente.isDirectory) return esistente
        return runCatching { dentro.createDirectory(nome) }.getOrNull()
    }

    /**
     * Il tipo MIME che si dichiara al provider.
     *
     * `text/csv` e `text/markdown` perche' un gestore file li apra con
     * qualcosa di sensato invece di chiedere ogni volta.
     */
    private fun tipo(nome: String): String = when {
        nome.endsWith(".csv") -> "text/csv"
        nome.endsWith(".md") -> "text/markdown"
        nome.endsWith(".json") -> "application/json"
        nome.endsWith(".jpg") || nome.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }

    companion object {

        /**
         * L'intent che apre il selettore di cartelle di sistema.
         *
         * Nessun permesso di archiviazione: l'utente indica una cartella e da
         * quel momento l'app puo' scrivere **solo li' dentro**. E' il motivo per
         * cui l'elenco dei permessi dell'app resta corto.
         */
        fun selettore(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )

        /**
         * Tiene il permesso oltre il riavvio dell'app.
         *
         * Senza questo, l'Uri salvato nelle impostazioni sarebbe carta straccia
         * al prossimo avvio. Sopravvive anche a un riavvio del telefono, ma
         * **non a una reinstallazione**: in quel caso la cartella si riscegli.
         */
        fun ricorda(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)

        fun dimentica(context: Context, uri: Uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }

        /** Vero se il permesso su quella cartella e' ancora nostro. */
        fun accessibile(context: Context, uri: Uri): Boolean =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }

        /**
         * Come si chiama la cartella scelta, per mostrarlo nelle impostazioni.
         *
         * Un Uri SAF e' illeggibile — `content://com.android.externalstorage.
         * documents/tree/primary%3ADocumenti%2FMya` — e mostrarlo cosi' non
         * aiuterebbe nessuno a capire dove stanno finendo i suoi file.
         */
        fun nome(context: Context, uri: Uri): String? =
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
    }
}
