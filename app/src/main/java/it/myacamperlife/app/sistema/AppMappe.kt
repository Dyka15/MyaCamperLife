package it.myacamperlife.app.sistema

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.myacamperlife.app.dominio.Mappe

/** Un'app di mappe installata: il pacchetto per aprirla, il nome per sceglierla. */
data class AppMappa(val pacchetto: String, val nome: String)

/**
 * Quale app apre un punto sulla mappa.
 *
 * **Il problema era che non lo decideva l'utente.** L'app manda un intent `geo:`
 * e Android lo consegna all'app predefinita: se un giorno hai risposto "sempre"
 * a una schermata di scelta, da allora si apre quella e cambiarla vuol dire
 * andare a cercare le app predefinite nelle impostazioni di sistema. Una cosa
 * decisa mesi fa in due secondi, e poi immutabile da qui.
 *
 * Adesso la scelta e' una preferenza dell'app: si vede quali app di mappe ci
 * sono, se ne sceglie una, e da quel momento l'intent va **solo** a quella.
 * Senza scelta si apre il selettore di sistema **ogni volta**, scavalcando il
 * predefinito: e' il valore di riposo perche' e' l'unico che non puo' sbagliare,
 * e perche' e' la via per cambiare idea senza passare da qui.
 */
object AppMappe {

    /**
     * Le app che sanno aprire un punto sulla mappa, in ordine alfabetico.
     *
     * Serve la dichiarazione `<queries>` nel manifest: da Android 11, senza,
     * questo elenco torna vuoto anche con dieci app di mappe installate — e la
     * schermata direbbe "nessuna app di mappe" a chi ce l'ha.
     */
    fun installate(contesto: Context): List<AppMappa> {
        val gestore = contesto.packageManager
        val sonda = Intent(Intent.ACTION_VIEW, Uri.parse(Mappe.geo(45.0, 11.0, "x")))
        return gestore.queryIntentActivities(sonda, 0)
            .mapNotNull { risolta ->
                val pacchetto = risolta.activityInfo?.packageName ?: return@mapNotNull null
                AppMappa(
                    pacchetto = pacchetto,
                    nome = risolta.loadLabel(gestore).toString().ifBlank { pacchetto },
                )
            }
            .distinctBy { it.pacchetto }
            .sortedBy { it.nome.lowercase() }
    }

    /**
     * Apre un punto, con l'app scelta se c'e' ancora.
     *
     * Tre strade, e la terza e' quella che salva la funzione: se il pacchetto
     * salvato non e' piu' installato — l'app disinstallata, il telefono nuovo —
     * si riparte dalla scelta di sistema invece di non fare niente. Una
     * preferenza vecchia non deve poter rompere un pulsante.
     *
     * @return `false` se su questo telefono non c'e' nessuna app di mappe.
     */
    fun apri(
        contesto: Context,
        lat: Double,
        lon: Double,
        nome: String,
        pacchetto: String? = null,
    ): Boolean {
        val indirizzo = Uri.parse(Mappe.geo(lat, lon, nome))
        val scelta = pacchetto
            ?.takeIf { installata(contesto, it) }
            ?.let { Intent(Intent.ACTION_VIEW, indirizzo).setPackage(it) }
        if (scelta != null) {
            contesto.startActivity(scelta)
            return true
        }

        val libero = Intent(Intent.ACTION_VIEW, indirizzo)
        if (libero.resolveActivity(contesto.packageManager) == null) return false
        // **`createChooser` e non `startActivity` secco**, ed e' il punto di
        // tutta questa storia: senza, Android consegna all'app predefinita e
        // "chiedi ogni volta" sarebbe una bugia — e' proprio quel predefinito,
        // scelto una volta e dimenticato, il motivo per cui si apriva sempre la
        // stessa app. Il selettore ignora il predefinito e ripropone la scelta.
        contesto.startActivity(Intent.createChooser(libero, null))
        return true
    }

    /** Il nome di un pacchetto salvato, o `null` se quell'app non c'e' piu'. */
    fun nomeDi(contesto: Context, pacchetto: String?): String? {
        val cercato = pacchetto ?: return null
        return installate(contesto).firstOrNull { it.pacchetto == cercato }?.nome
    }

    private fun installata(contesto: Context, pacchetto: String): Boolean = runCatching {
        contesto.packageManager.getPackageInfo(pacchetto, 0)
        true
    }.getOrDefault(false)
}
