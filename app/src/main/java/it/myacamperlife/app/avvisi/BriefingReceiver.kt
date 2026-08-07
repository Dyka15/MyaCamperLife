package it.myacamperlife.app.avvisi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.myacamperlife.app.MyaApplication
import it.myacamperlife.app.rete.Scorte
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
 *
 * **Poi si prova a scaricare il meteo, e poi si compone.** In quest'ordine, e
 * il primo non puo' far fallire il secondo: se non c'e' campo, o il servizio
 * non risponde, il riepilogo esce comunque con le tappe — che e' gia' meta' del
 * suo valore — usando le previsioni della sera prima, dichiarandone l'eta'.
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

                // La scorta si rinfresca adesso, che e' l'unico momento
                // prevedibile in cui l'app gira da sola.
                archivio.slugCorrente()?.let { slug ->
                    Scorte(applicazione, archivio).aggiornaMeteo(slug)
                }

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
