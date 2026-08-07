package it.myacamperlife.app.avvisi

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.myacamperlife.app.MyaApplication
import java.util.concurrent.TimeUnit

/**
 * Ogni sei ore controlla che la sveglia delle 19:00 sia ancora in coda, e la
 * rimette se non c'e'.
 *
 * **Perche' serve.** HyperOS — e le ROM Xiaomi in genere — congela le app che
 * considera inattive e nel farlo si porta via le sveglie, senza avvisare
 * nessuno. Il sintomo e' il peggiore possibile: il riepilogo smette di arrivare
 * e non c'e' niente da guardare per capire perche'.
 *
 * `WorkManager` sopravvive dove `AlarmManager` viene potato, perche' il sistema
 * lo tratta come lavoro differito e non come una sveglia. Un controllo ogni sei
 * ore costa niente e chiude il buco.
 *
 * Non e' una garanzia: se l'utente non toglie l'app dall'ottimizzazione della
 * batteria, prima o poi anche questo viene fermato. Per quello ci sono i
 * pulsanti nelle impostazioni.
 */
class GuardianoBriefing(
    context: Context,
    parametri: WorkerParameters,
) : CoroutineWorker(context, parametri) {

    override suspend fun doWork(): Result {
        val applicazione = applicationContext
        val archivio = (applicazione as? MyaApplication)?.archivio ?: return Result.success()
        val impostazioni = archivio.impostazioni()

        if (!impostazioni.briefingAttivo) {
            SvegliaBriefing.annulla(applicazione)
            return Result.success()
        }

        if (!SvegliaBriefing.programmata(applicazione)) {
            SvegliaBriefing.programma(applicazione, attivo = true, ora = impostazioni.ora)
        }
        return Result.success()
    }

    companion object {
        private const val NOME = "guardiano-briefing"

        /**
         * `KEEP` e non `UPDATE`: se il controllo e' gia' programmato lo si
         * lascia dov'e', invece di far ripartire il conteggio delle sei ore a
         * ogni avvio dell'app — che equivarrebbe a non controllare mai.
         */
        fun programma(context: Context) {
            val lavoro = PeriodicWorkRequestBuilder<GuardianoBriefing>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOME,
                ExistingPeriodicWorkPolicy.KEEP,
                lavoro,
            )
        }
    }
}
