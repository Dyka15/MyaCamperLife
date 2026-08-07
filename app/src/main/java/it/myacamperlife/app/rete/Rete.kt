package it.myacamperlife.app.rete

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Una GET e basta.
 *
 * **Nessuna libreria HTTP.** `HttpURLConnection` sta dentro Android, e le due
 * richieste che questa app fa — il meteo e le tratte, entrambe una volta al
 * giorno o meno — non giustificano un paio di megabyte di dipendenza. Sarebbe
 * anche in contraddizione con l'aver tolto il riconoscimento del testo per
 * tenere l'APK leggero.
 *
 * **Tutto ha un tetto**: connessione, lettura, e dimensione della risposta.
 * Una richiesta che non finisce e' peggio di una richiesta fallita, perche' la
 * seconda lascia comunque uscire il briefing.
 */
object Rete {

    /** Il corpo della risposta, o `null` per qualunque intoppo. */
    suspend fun prendi(indirizzo: String): String? = withContext(Dispatchers.IO) {
        var connessione: HttpURLConnection? = null
        try {
            connessione = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = ATTESA_CONNESSIONE
                readTimeout = ATTESA_LETTURA
                instanceFollowRedirects = true
                // I servizi pubblici gradiscono sapere chi chiama, e alcuni
                // rifiutano le richieste senza.
                setRequestProperty("User-Agent", AGENTE)
                setRequestProperty("Accept", "application/json")
            }
            if (connessione.responseCode !in 200..299) return@withContext null

            connessione.inputStream.bufferedReader(Charsets.UTF_8).use { lettore ->
                val buffer = CharArray(MASSIMO_CARATTERI)
                val letti = lettore.read(buffer, 0, MASSIMO_CARATTERI)
                if (letti <= 0) null else String(buffer, 0, letti)
            }
        } catch (e: IOException) {
            // Niente rete, DNS muto, timeout, certificato strano: da qui in su
            // sono tutti lo stesso caso, e il caso e' "si fa senza".
            null
        } catch (e: SecurityException) {
            null
        } finally {
            connessione?.disconnect()
        }
    }

    /**
     * Se vale la pena provarci.
     *
     * Non e' una garanzia — fra il controllo e la richiesta il campo puo'
     * sparire, ed e' proprio quello che succede in viaggio — ma evita di
     * spendere venti secondi di timeout quando la risposta si sa gia'.
     */
    fun disponibile(context: Context): Boolean {
        val gestore = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val rete = gestore.activeNetwork ?: return false
        val capacita = gestore.getNetworkCapabilities(rete) ?: return false
        return capacita.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacita.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private const val AGENTE = "MyaCamperLife/0.1 (app personale offline-first)"

    /** Dieci secondi per connettersi: sotto un ripetitore stanco ne servono tre. */
    private const val ATTESA_CONNESSIONE = 10_000

    /** Quindici per leggere: oltre, il briefing delle 19:00 aspetterebbe troppo. */
    private const val ATTESA_LETTURA = 15_000

    /** Circa due megabyte: una risposta piu' grande di cosi' non e' la nostra. */
    private const val MASSIMO_CARATTERI = 2_000_000
}
