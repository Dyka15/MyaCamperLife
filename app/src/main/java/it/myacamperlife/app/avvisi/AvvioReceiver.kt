package it.myacamperlife.app.avvisi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.myacamperlife.app.MyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Riarma la sveglia dopo un riavvio del telefono.
 *
 * **Un riavvio cancella tutte le sveglie**, e senza questo pezzo il riepilogo
 * smetterebbe di arrivare senza dire niente — il modo peggiore in cui una
 * funzione puo' rompersi. E' anche il momento in cui si rimette in piedi il
 * guardiano, che il riavvio non tocca ma che conviene verificare.
 *
 * I tre eventi ascoltati: l'avvio normale, il "quick boot" delle ROM Xiaomi che
 * non mandano sempre `BOOT_COMPLETED`, e l'aggiornamento dell'app — che ferma
 * il processo e porta via le sveglie come un riavvio.
 */
class AvvioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in AZIONI) return

        val applicazione = context.applicationContext
        val archivio = (applicazione as? MyaApplication)?.archivio ?: return
        val risultato = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val impostazioni = archivio.impostazioni()
                SvegliaBriefing.programma(
                    context = applicazione,
                    attivo = impostazioni.briefingAttivo,
                    ora = impostazioni.ora,
                )
                GuardianoBriefing.programma(applicazione)
            } finally {
                risultato.finish()
            }
        }
    }

    private companion object {
        val AZIONI = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
