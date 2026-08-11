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
 * Una GET, una POST, e basta.
 *
 * **Nessuna libreria HTTP.** `HttpURLConnection` sta dentro Android, e le tre
 * richieste che questa app fa — meteo una volta al giorno, tratte e dintorni una
 * volta per itinerario — non giustificano un paio di megabyte di dipendenza.
 * Sarebbe anche in contraddizione con l'aver tolto il riconoscimento del testo
 * per tenere l'APK leggero.
 *
 * **Tutto ha un tetto**: connessione, lettura, e dimensione della risposta.
 * Una richiesta che non finisce e' peggio di una richiesta fallita, perche' la
 * seconda lascia comunque uscire il briefing.
 */
object Rete {

    /** Il corpo della risposta, o `null` per qualunque intoppo. */
    suspend fun prendi(
        indirizzo: String,
        massimoCaratteri: Int = MASSIMO_CARATTERI,
    ): String? = chiama(indirizzo, corpo = null, massimoCaratteri = massimoCaratteri)

    /**
     * Una POST con un corpo di testo. La vuole Overpass, la cui query e' troppo
     * lunga per stare in un indirizzo.
     */
    suspend fun posta(
        indirizzo: String,
        corpo: String,
        massimoCaratteri: Int = MASSIMO_CARATTERI,
    ): String? = chiama(indirizzo, corpo, massimoCaratteri)

    /**
     * Una POST che riporta **anche l'errore**.
     *
     * Le chiamate mute dell'app possono ridurre ogni guaio a `null`: senza meteo
     * il briefing esce comunque. Qui no. Una chiave scaduta, un identificativo di
     * modello ritirato e una quota finita sono tre situazioni con tre rimedi
     * diversi; su Overpass un 429 vuol dire "riprova fra un minuto" e un 504
     * vuol dire "hai chiesto troppo". Distinguerle richiede leggere quello che il
     * servizio ha risposto, e mostrarlo.
     *
     * @param tipo il `Content-Type` del corpo. I modelli vogliono JSON, Overpass
     *   vuole la sua query come testo.
     */
    suspend fun postaConEsito(
        indirizzo: String,
        corpo: String,
        intestazioni: Map<String, String> = emptyMap(),
        tipo: String = JSON,
        massimoCaratteri: Int = MASSIMO_CARATTERI,
    ): EsitoHttp = withContext(Dispatchers.IO) {
        var connessione: HttpURLConnection? = null
        try {
            connessione = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = ATTESA_CONNESSIONE
                readTimeout = ATTESA_LETTURA_LUNGA
                instanceFollowRedirects = true
                doOutput = true
                setRequestProperty("User-Agent", AGENTE)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", tipo)
                intestazioni.forEach { (nome, valore) -> setRequestProperty(nome, valore) }
            }
            connessione.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }

            val codice = connessione.responseCode
            if (codice in 200..299) {
                val letto = leggi(connessione, massimoCaratteri)
                if (letto == null) EsitoHttp.Muto else EsitoHttp.Riuscito(letto)
            } else {
                // Il corpo dell'errore e' dove sta il messaggio utile.
                val errore = runCatching {
                    connessione.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                EsitoHttp.Rifiutato(codice, errore?.take(2_000))
            }
        } catch (e: IOException) {
            EsitoHttp.Muto
        } catch (e: SecurityException) {
            EsitoHttp.Muto
        } finally {
            connessione?.disconnect()
        }
    }

    private suspend fun chiama(
        indirizzo: String,
        corpo: String?,
        massimoCaratteri: Int,
    ): String? = withContext(Dispatchers.IO) {
        var connessione: HttpURLConnection? = null
        try {
            connessione = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                requestMethod = if (corpo == null) "GET" else "POST"
                connectTimeout = ATTESA_CONNESSIONE
                readTimeout = if (corpo == null) ATTESA_LETTURA else ATTESA_LETTURA_LUNGA
                instanceFollowRedirects = true
                // I servizi pubblici gradiscono sapere chi chiama, e alcuni
                // rifiutano le richieste senza.
                setRequestProperty("User-Agent", AGENTE)
                setRequestProperty("Accept", "application/json")
                if (corpo != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                }
            }

            corpo?.let { testo ->
                connessione.outputStream.use { flusso ->
                    flusso.write(testo.toByteArray(Charsets.UTF_8))
                }
            }

            if (connessione.responseCode !in 200..299) return@withContext null
            leggi(connessione, massimoCaratteri)
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
     * Legge fino al tetto, e **una risposta troncata vale come nessuna
     * risposta**: mezzo JSON non si analizza, e restituirlo produrrebbe zero
     * risultati facendo credere che il servizio non abbia trovato niente.
     */
    private fun leggi(connessione: HttpURLConnection, massimo: Int): String? =
        connessione.inputStream.bufferedReader(Charsets.UTF_8).use { lettore ->
            val testo = StringBuilder()
            val buffer = CharArray(BLOCCO)
            while (true) {
                val letti = lettore.read(buffer)
                if (letti <= 0) break
                if (testo.length + letti > massimo) return null
                testo.appendRange(buffer, 0, letti)
            }
            testo.toString().takeUnless { it.isEmpty() }
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

    const val JSON = "application/json; charset=utf-8"

    /** Un corpo di testo semplice. */
    const val TESTO = "text/plain; charset=utf-8"

    /**
     * La forma **documentata** di una POST a Overpass: `data=<query>` codificata
     * come un modulo. Il perche' sta in
     * [it.myacamperlife.app.dominio.Overpass.corpoModulo].
     */
    const val MODULO = "application/x-www-form-urlencoded; charset=utf-8"

    /** Dieci secondi per connettersi: sotto un ripetitore stanco ne servono tre. */
    private const val ATTESA_CONNESSIONE = 10_000

    /** Quindici per leggere: oltre, il briefing delle 19:00 aspetterebbe troppo. */
    private const val ATTESA_LETTURA = 15_000

    /**
     * Overpass elabora la query prima di rispondere, e una query larga ci mette
     * un minuto; una risposta ragionata di un modello anche. Non e' un'attesa
     * che blocca qualcosa: succede in sottofondo mentre l'app resta usabile.
     */
    private const val ATTESA_LETTURA_LUNGA = 120_000

    private const val BLOCCO = 16 * 1024

    /** Circa due megabyte: una risposta piu' grande di cosi' non e' la nostra. */
    const val MASSIMO_CARATTERI = 2_000_000

    /**
     * I dintorni sono la risposta piu' grande: sette categorie e i toponimi in
     * un cerchio di dieci chilometri, e in citta' sono migliaia di oggetti. Sei
     * megabyte di testo sono un tetto generoso e comunque limitato.
     */
    const val MASSIMO_DINTORNI = 6_000_000
}

/** L'esito di una chiamata che deve poter riferire l'errore. */
sealed interface EsitoHttp {
    data class Riuscito(val corpo: String) : EsitoHttp
    data class Rifiutato(val codice: Int, val corpo: String?) : EsitoHttp

    /** Niente rete, timeout, risposta vuota o troncata dal tetto. */
    data object Muto : EsitoHttp
}
