package it.myacamperlife.app.archivio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Un documento scelto dall'utente o arrivato da un'altra app. */
data class DocumentoLetto(val nome: String?, val testo: String)

/**
 * Legge il contenuto di un documento indicato da un `Uri`.
 *
 * Sta a parte dal resto dell'archivio perche' e' l'unico punto che ha bisogno
 * di Android: cosi' il formato dei file resta verificabile con test normali.
 */
class Documenti(private val context: Context) {

    fun leggi(uri: Uri): DocumentoLetto? = runCatching {
        val testo = context.contentResolver.openInputStream(uri)?.use { flusso ->
            // Un tetto sulla dimensione: se l'utente sceglie per sbaglio un
            // video, meglio un messaggio d'errore che un OutOfMemory.
            flusso.bufferedReader(Charsets.UTF_8).use { lettore ->
                val buffer = CharArray(MASSIMO_CARATTERI)
                val letti = lettore.read(buffer, 0, MASSIMO_CARATTERI)
                if (letti <= 0) "" else String(buffer, 0, letti)
            }
        } ?: return null
        DocumentoLetto(nome = nome(uri), testo = testo)
    }.getOrNull()

    private fun nome(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursore ->
                if (cursore.moveToFirst()) cursore.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment

    private companion object {
        /** Circa due megabyte di testo: un itinerario non ci arriva neanche vicino. */
        const val MASSIMO_CARATTERI = 2_000_000
    }
}
