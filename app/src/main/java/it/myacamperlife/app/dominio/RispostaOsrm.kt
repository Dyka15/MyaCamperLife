package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Legge la risposta di OSRM e ne ricava le tratte fra tappe consecutive.
 *
 * **Una chiamata sola per tutto l'itinerario.** Il servizio `route` accetta
 * tutti i punti in fila e risponde con un `legs` per ogni tratto consecutivo:
 * cinque tappe sono una richiesta, non quattro. In una finestra di
 * connettivita' incerta e' la differenza fra avere le distanze e non averle.
 *
 * Come tutto il resto del dominio: se la risposta e' rotta si ottiene una lista
 * vuota, non un'eccezione. Senza tratte l'app ripiega sulla linea d'aria, che
 * e' peggio ma funziona.
 *
 * Funzione pura.
 */
object RispostaOsrm {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param punti gli stessi punti mandati nella richiesta, nello stesso
     *   ordine: la risposta non li ripete, dice solo quanto e' lungo il tratto
     *   fra l'uno e l'altro.
     */
    fun leggi(corpo: String, punti: List<PuntoTratta>): List<Tratta> {
        if (punti.size < 2) return emptyList()

        val radice = runCatching { json.parseToJsonElement(corpo) as? JsonObject }.getOrNull()
            ?: return emptyList()
        if (radice["code"]?.jsonPrimitive?.contentOrNull != "Ok") return emptyList()

        val percorso = (radice["routes"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return emptyList()
        val tratti = (percorso["legs"] as? JsonArray).orEmpty()

        return tratti.mapIndexedNotNull { indice, tratto ->
            val oggetto = tratto as? JsonObject ?: return@mapIndexedNotNull null
            val da = punti.getOrNull(indice) ?: return@mapIndexedNotNull null
            val a = punti.getOrNull(indice + 1) ?: return@mapIndexedNotNull null

            // OSRM risponde in metri e secondi.
            val metri = numero(oggetto, "distance") ?: return@mapIndexedNotNull null
            val secondi = numero(oggetto, "duration") ?: return@mapIndexedNotNull null
            if (metri <= 0) return@mapIndexedNotNull null

            Tratta(
                daLat = da.lat,
                daLon = da.lon,
                aLat = a.lat,
                aLon = a.lon,
                km = metri / 1000.0,
                minuti = (secondi / 60.0).roundToInt(),
                da = da.nome,
                a = a.nome,
            )
        }
    }

    private fun numero(oggetto: JsonObject, nome: String): Double? =
        runCatching { oggetto[nome]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    /**
     * L'indirizzo da chiamare.
     *
     * `overview=false` e nessuna annotazione: della geometria del percorso non
     * ce ne facciamo niente — la navigazione la fa Organic Maps — e chiederla
     * significherebbe scaricare centinaia di kilobyte di poligonale per usarne
     * due numeri.
     *
     * **Longitudine prima della latitudine**: e' la convenzione di OSRM, ed e'
     * l'opposto di quella di tutto il resto dell'app. Invertirle non da'
     * errore, da' distanze assurde.
     */
    fun indirizzo(punti: List<PuntoTratta>): String {
        require(punti.size >= 2) { "una tratta ha bisogno di due punti" }
        val coordinate = punti.joinToString(";") { "${gradi(it.lon)},${gradi(it.lat)}" }
        return "$SERVIZIO/route/v1/driving/$coordinate?overview=false&alternatives=false&steps=false"
    }

    private fun gradi(valore: Double): String =
        String.format(java.util.Locale.ROOT, "%.6f", valore)

    /**
     * Il server pubblico del progetto OSRM.
     *
     * E' un servizio di cortesia, non un'infrastruttura su cui appoggiarsi: lo
     * si interroga **una volta per itinerario**, non a ogni schermata. Se un
     * giorno smettesse di rispondere l'app ripiegherebbe sulla linea d'aria
     * senza accorgersene.
     */
    private const val SERVIZIO = "https://router.project-osrm.org"

    /**
     * Oltre questo numero di punti la richiesta si spezza: il server pubblico
     * ha un tetto, e un itinerario lunghissimo non deve far fallire tutto.
     */
    const val PUNTI_PER_RICHIESTA = 25
}

/** Un punto di una richiesta di tratte, col nome della tappa che lo ha portato. */
data class PuntoTratta(val nome: String?, val lat: Double, val lon: Double)
