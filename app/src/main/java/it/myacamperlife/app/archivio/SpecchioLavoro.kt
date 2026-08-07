package it.myacamperlife.app.archivio

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.myacamperlife.app.MyaApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La passata di specchio, fuori dal momento della scrittura.
 *
 * **Differita di proposito.** Registrare una foto e' un `append` locale che
 * riesce sempre; ricopiarla su una cartella che potrebbe stare su un cloud non
 * ha nessuna delle due proprieta'. Mettere la copia dentro la registrazione
 * vorrebbe dire far aspettare l'utente su una rete che in camper non c'e'.
 *
 * `WorkManager` da' anche la cosa giusta gratis: il lavoro sopravvive alla
 * chiusura dell'app e riprende al riavvio del telefono, quindi una passata
 * saltata perche' la chiavetta era staccata si rifa' da sola.
 */
class SpecchioLavoro(
    context: Context,
    parametri: WorkerParameters,
) : CoroutineWorker(context, parametri) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val applicazione = applicationContext
        val archivio = (applicazione as? MyaApplication)?.archivio ?: return@withContext Result.success()

        val salvata = archivio.impostazioni().cartellaSpecchio ?: return@withContext Result.success()
        val uri = runCatching { Uri.parse(salvata) }.getOrNull() ?: return@withContext Result.success()

        // Permesso revocato o perso in una reinstallazione: non e' un errore da
        // ritentare all'infinito, e' una cartella da riscegliere. Lo dira'
        // l'interfaccia, che il permesso lo controlla ogni volta che si apre.
        if (!Specchio.accessibile(applicazione, uri)) return@withContext Result.success()

        val esito = Specchio(applicazione, uri).rispecchia(archivio.radiceArchivio())
        // Si ritenta solo se qualcosa e' fallito: `retry` fa riprovare
        // WorkManager con un intervallo che cresce da solo.
        if (esito.riuscito) Result.success() else Result.retry()
    }

    companion object {
        private const val NOME = "specchio"

        /**
         * Mette in coda una passata.
         *
         * `REPLACE` su un lavoro unico: dieci registrazioni di fila non fanno
         * dieci passate ma una, quella dopo l'ultima. E' il comportamento che si
         * vuole — la passata copia comunque tutto quello che e' cambiato.
         */
        fun programma(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SpecchioLavoro>().build(),
            )
        }
    }
}
