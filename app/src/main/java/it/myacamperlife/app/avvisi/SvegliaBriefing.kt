package it.myacamperlife.app.avvisi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.myacamperlife.app.dominio.Briefings
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Fa scattare il riepilogo alle 19:00, anche a schermo spento e con l'app
 * chiusa da giorni.
 *
 * **Niente allarme esatto.** `setAndAllowWhileIdle` attraversa il Doze e
 * tollera qualche minuto di scarto, che per un riepilogo serale va benissimo.
 * L'alternativa, `setExactAndAllowWhileIdle`, vorrebbe `SCHEDULE_EXACT_ALARM` —
 * un permesso che le regole del Play Store riservano alle sveglie, e un'app per
 * camper non e' titolata a chiederlo.
 *
 * **Una sveglia per volta, che si riarma da sola.** Non un allarme ripetuto:
 * quelli il sistema li allunga a piacere. Ogni scatto programma il successivo,
 * e chi riarma controlla sempre se ce n'e' gia' uno in coda.
 */
object SvegliaBriefing {

    /**
     * Programma il prossimo riepilogo, se e' acceso.
     *
     * Si chiama a ogni avvio dell'app, dopo un riavvio del telefono, quando si
     * cambia l'impostazione, e dal guardiano ogni sei ore. Chiamarla dieci
     * volte di fila non fa danno: sostituisce la sveglia con la stessa.
     */
    /**
     * @return quando scattera', o `null` se il riepilogo e' spento (o se il
     *   sistema non ha un `AlarmManager`, che non succede ma si gestisce).
     *   **Il valore di ritorno non e' un ornamento**: e' quello che si scrive
     *   nelle impostazioni, ed e' l'unico modo di sapere, guardando il telefono
     *   il giorno dopo, se una sveglia era davvero in coda — su HyperOS vengono
     *   portate via senza dire niente.
     */
    fun programma(
        context: Context,
        attivo: Boolean,
        ora: Int,
        adesso: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime? {
        val gestore = context.getSystemService(AlarmManager::class.java) ?: return null
        val intento = pendente(context, PendingIntent.FLAG_UPDATE_CURRENT)

        if (!attivo) {
            gestore.cancel(intento)
            intento.cancel()
            return null
        }

        val quando = Briefings.prossimoScatto(ora, adesso)
        gestore.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            intento,
        )
        return quando
    }

    fun annulla(context: Context) {
        programma(context, attivo = false, ora = 0)
    }

    /** Vero se una sveglia e' gia' in coda: e' il controllo del guardiano. */
    fun programmata(context: Context): Boolean =
        pendenteSeEsiste(context) != null

    private fun pendente(context: Context, bandiere: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RICHIESTA,
            Intent(context, BriefingReceiver::class.java),
            bandiere or PendingIntent.FLAG_IMMUTABLE,
        )

    /** `FLAG_NO_CREATE` restituisce `null` invece di crearne uno nuovo. */
    private fun pendenteSeEsiste(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            RICHIESTA,
            Intent(context, BriefingReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val RICHIESTA = 19
}
