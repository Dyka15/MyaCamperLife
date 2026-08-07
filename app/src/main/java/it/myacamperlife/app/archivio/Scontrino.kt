package it.myacamperlife.app.archivio

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.myacamperlife.app.dominio.LetturaScontrino
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Legge l'importo dalla foto di uno scontrino, **sul telefono**.
 *
 * Il modello di riconoscimento e' dentro l'APK: nessuna immagine esce dal
 * dispositivo e la lettura funziona in mezzo al nulla, che e' dove serve. Costa
 * qualche megabyte di applicazione, e li vale.
 *
 * Il modello e' quello dell'alfabeto latino: sugli scontrini europei e' quello
 * giusto, e le varianti per altri alfabeti peserebbero senza servire.
 *
 * Qui c'e' solo il ponte con ML Kit. La parte che decide *quale* numero sia il
 * totale sta in [LetturaScontrino], nel dominio, dove si puo' verificare su
 * scontrini veri senza una fotocamera.
 */
class Scontrino(private val context: Context) {

    /**
     * L'importo proposto, o `null` se la foto non ne contiene uno leggibile.
     *
     * Non solleva eccezioni: una lettura che va storta e' un campo da compilare
     * a mano, non un errore da mostrare. Registrare la spesa deve restare
     * possibile comunque — e' il primo principio dell'app.
     */
    suspend fun importo(uri: Uri): Double? = testo(uri)?.let { LetturaScontrino.importo(it) }

    private suspend fun testo(uri: Uri): String? {
        // Solo l'apertura dell'immagine puo' sollevare: una foto illeggibile,
        // un permesso scaduto sull'Uri. Il riconoscimento riferisce i suoi
        // guai al listener, e li' diventano un `null`.
        val immagine = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            ?: return null

        val riconoscitore = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine<String?> { continuazione ->
                riconoscitore.process(immagine)
                    .addOnSuccessListener { risultato -> continuazione.resume(risultato.text) }
                    .addOnFailureListener { continuazione.resume(null) }
            }
        } finally {
            riconoscitore.close()
        }
    }
}
