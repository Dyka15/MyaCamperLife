package it.myacamperlife.app.avvisi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.myacamperlife.app.MyaApplication
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Le 19:00: compone il riepilogo, lo notifica, riarma la sveglia per domani.
 *
 * **La sveglia si riarma per prima cosa.** Se comporre il riepilogo andasse
 * storto — un file rovinato, un archivio vuoto — riarmandola in fondo si
 * perderebbe anche quello di domani, e quello di dopodomani, per sempre. Una
 * notifica saltata e' un guaio di una sera; una catena spezzata e' una funzione
 * che smette di esistere in silenzio.
 */
class BriefingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val applicazione = context.applicationContext
        val archivio = (applicazione as? MyaApplication)?.archivio ?: return

        // `goAsync` tiene vivo il processo mentre si leggono i file: senza,
        // il sistema puo' chiuderlo appena `onReceive` ritorna.
        val risultato = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val impostazioni = archivio.impostazioni()
                SvegliaBriefing.programma(
                    context = applicazione,
                    attivo = impostazioni.briefingAttivo,
                    ora = impostazioni.ora,
                    // Da un minuto dopo: l'ora di adesso e' proprio quella del
                    // riepilogo, e senza lo scarto si riprogrammerebbe fra un
                    // istante invece che domani.
                    adesso = LocalDateTime.now().plusMinutes(1),
                )
                if (!impostazioni.briefingAttivo) return@launch

                val briefing = archivio.briefingCorrente() ?: return@launch
                // Un riepilogo che non ha niente da dire non si manda: una
                // notifica vuota insegna a ignorare le notifiche.
                if (briefing.vuoto) return@launch

                Avvisi(applicazione).apply {
                    preparaCanale()
                    mostra(briefing)
                }
            } finally {
                risultato.finish()
            }
        }
    }
}
