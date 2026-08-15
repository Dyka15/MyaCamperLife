package it.myacamperlife.app.avvisi

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.myacamperlife.app.MainActivity
import it.myacamperlife.app.R
import it.myacamperlife.app.dominio.Briefing
import it.myacamperlife.app.dominio.TestoBriefing

/**
 * La notifica del riepilogo serale.
 *
 * Una sola notifica, un solo canale: quello che si spegne dalle impostazioni di
 * sistema e' esattamente il riepilogo, senza portarsi via altro.
 */
class Avvisi(private val context: Context) {

    fun preparaCanale() {
        val canale = NotificationChannel(
            CANALE,
            context.getString(R.string.canale_briefing),
            // Default e non High: un riepilogo serale non e' urgente e non
            // deve comparire a tutto schermo sopra quello che stai facendo.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.canale_briefing_descrizione)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(canale)
    }

    fun permessoConcesso(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Mostra il riepilogo. **Torna `false` se non c'e' il permesso**, invece di
     * non fare niente: da Android 13 la notifica verrebbe scartata in silenzio,
     * e il silenzio e' precisamente il guaio — chi chiama scrive l'esito, cosi'
     * la sera dopo si legge «manca il permesso» invece di indovinare.
     */
    fun mostra(briefing: Briefing): Boolean {
        if (!permessoConcesso()) return false

        val corpo = TestoBriefing.corpo(briefing)
        val notifica = NotificationCompat.Builder(context, CANALE)
            .setSmallIcon(R.drawable.ic_avviso)
            .setContentTitle(TestoBriefing.titolo(briefing))
            .setContentText(corpo.lineSequence().firstOrNull().orEmpty())
            // Il testo lungo si legge tutto aprendo la notifica: il riepilogo
            // ha piu' righe, e troncarlo lo renderebbe inutile.
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setContentIntent(apriApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(ID_BRIEFING, notifica)
        return true
    }

    private fun apriApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CANALE = "briefing"
        const val ID_BRIEFING = 1

        val PERMESSI = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    }
}
