package it.myacamperlife.app.avvisi

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * I pulsanti che portano dove serve, quando il riepilogo non arriva.
 *
 * Su HyperOS — e sulle ROM Xiaomi in genere — un'app che non viene aperta per
 * qualche giorno viene congelata, e con lei sparisce la sveglia delle 19:00.
 * Non c'e' un modo programmatico per impedirlo: **si puo' solo accompagnare
 * l'utente nelle due o tre schermate di sistema che lo disattivano**, ed e' per
 * questo che esiste questo file.
 *
 * Ogni azione controlla di poter essere aperta prima di provarci: le schermate
 * proprietarie di Xiaomi cambiano nome fra una versione e l'altra, e un intent
 * che non risolve farebbe cadere l'app invece di aiutarla.
 */
object Sistema {

    /** Vero se il sistema ha gia' promesso di non congelare l'app. */
    fun batteriaSenzaLimiti(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName)
            ?: false

    /**
     * L'elenco delle app e la loro ottimizzazione della batteria.
     *
     * Si apre l'elenco e non la richiesta diretta: quest'ultima vorrebbe
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, un permesso che le regole del
     * Play Store concedono a poche categorie di app, e un'app per camper non e'
     * fra quelle.
     */
    fun apriBatteria(context: Context): Boolean =
        apri(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /** Le notifiche dell'app: da qui si riaccende il canale del riepilogo. */
    fun apriNotifiche(context: Context): Boolean = apri(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    ) || apriDettagliApp(context)

    /**
     * L'avvio automatico di MIUI/HyperOS: la schermata che decide se l'app puo'
     * ripartire dopo un riavvio. Non esiste su Android puro, e li' il pulsante
     * semplicemente non si mostra.
     */
    fun apriAvvioAutomatico(context: Context): Boolean = AVVIO_AUTOMATICO.any { componente ->
        apri(context, Intent().setComponent(componente))
    }

    fun avvioAutomaticoDisponibile(context: Context): Boolean =
        AVVIO_AUTOMATICO.any { componente ->
            Intent().setComponent(componente).resolveActivity(context.packageManager) != null
        }

    fun apriDettagliApp(context: Context): Boolean = apri(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )

    private fun apri(context: Context, intento: Intent): Boolean {
        intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intento.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(intento)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            // Alcune ROM dichiarano la schermata ma non la lasciano aprire da
            // fuori. Meglio nessun effetto che un crash.
            false
        }
    }

    /** I nomi che la schermata ha avuto fra una versione di MIUI e l'altra. */
    private val AVVIO_AUTOMATICO = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.powercenter.PowerSettings",
        ),
    )
}
