package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Legge la risposta di Open-Meteo.
 *
 * Sta nel dominio, non accanto al codice di rete: e' una trasformazione da
 * testo a dati, e la si verifica su una risposta salvata su file invece che
 * chiamando un servizio. Le rotture di un'API si scoprono con un test, non in
 * un'area di sosta.
 *
 * **Tollerante come tutto il resto.** Open-Meteo risponde con un oggetto per
 * una coordinata e con un array per piu' d'una; un campo puo' mancare, un
 * valore puo' essere `null` per i giorni piu' lontani. Niente di tutto questo
 * deve produrre un'eccezione: al peggio si ottiene una previsione con meno
 * campi, o una lista vuota.
 *
 * Funzione pura.
 */
object RispostaMeteo {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param richiesti le coordinate chieste, nello stesso ordine della
     *   richiesta. Servono a dare a ogni luogo il **nome della tappa** e le
     *   coordinate vere: quelle che tornano sono del nodo di griglia, e
     *   cercarci dentro per nome non funzionerebbe.
     */
    fun leggi(corpo: String, richiesti: List<PuntoMeteo> = emptyList()): List<MeteoLuogo> {
        val radice = runCatching { json.parseToJsonElement(corpo) }.getOrNull() ?: return emptyList()

        val oggetti = when (radice) {
            is JsonArray -> radice.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(radice)
            else -> return emptyList()
        }

        return oggetti.mapIndexedNotNull { indice, oggetto ->
            val chiesto = richiesti.getOrNull(indice)
            val previsioni = previsioni(oggetto)
            if (previsioni.isEmpty()) return@mapIndexedNotNull null
            MeteoLuogo(
                nome = chiesto?.nome,
                lat = chiesto?.lat ?: numero(oggetto, "latitude") ?: return@mapIndexedNotNull null,
                lon = chiesto?.lon ?: numero(oggetto, "longitude") ?: return@mapIndexedNotNull null,
                previsioni = previsioni,
            )
        }
    }

    /**
     * Il blocco `daily` e' **colonnare**: un array per campo, tutti allineati
     * sull'array `time`. Si traspone in una previsione per giorno.
     */
    private fun previsioni(oggetto: JsonObject): List<Previsione> {
        val giornaliero = oggetto["daily"] as? JsonObject ?: return emptyList()
        val giorni = testi(giornaliero, "time")
        if (giorni.isEmpty()) return emptyList()

        val codici = interi(giornaliero, "weather_code")
        val massime = numeri(giornaliero, "temperature_2m_max")
        val minime = numeri(giornaliero, "temperature_2m_min")
        val pioggia = numeri(giornaliero, "precipitation_sum")
        val probabilita = interi(giornaliero, "precipitation_probability_max")
        val vento = numeri(giornaliero, "wind_speed_10m_max")

        return giorni.mapIndexed { i, giorno ->
            Previsione(
                giorno = giorno,
                codice = codici.getOrNull(i),
                minima = minime.getOrNull(i),
                massima = massime.getOrNull(i),
                pioggiaMm = pioggia.getOrNull(i),
                probabilitaPioggia = probabilita.getOrNull(i),
                ventoKmh = vento.getOrNull(i),
            )
        }
    }

    private fun colonna(oggetto: JsonObject, nome: String) =
        (oggetto[nome] as? JsonArray)?.toList().orEmpty()

    private fun testi(oggetto: JsonObject, nome: String): List<String> =
        colonna(oggetto, nome).map { runCatching { it.jsonPrimitive.content }.getOrDefault("") }
            .filter { it.isNotEmpty() }

    private fun numeri(oggetto: JsonObject, nome: String): List<Double?> =
        colonna(oggetto, nome).map { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }

    private fun interi(oggetto: JsonObject, nome: String): List<Int?> =
        colonna(oggetto, nome).map { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

    private fun numero(oggetto: JsonObject, nome: String): Double? =
        runCatching { oggetto[nome]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    /**
     * L'indirizzo da chiamare.
     *
     * Una sola richiesta per tutte le tappe: Open-Meteo accetta le coordinate
     * separate da virgola e risponde con un array. Cinque tappe sono una
     * chiamata, non cinque — e in una finestra di connettivita' scarsa e' la
     * differenza fra avere il meteo e non averlo.
     */
    fun indirizzo(punti: List<PuntoMeteo>, giorni: Int = GIORNI): String {
        require(punti.isNotEmpty()) { "senza coordinate non c'e' niente da chiedere" }
        val lat = punti.joinToString(",") { coordinata(it.lat) }
        val lon = punti.joinToString(",") { coordinata(it.lon) }
        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&daily=$CAMPI" +
            "&timezone=auto" +
            "&forecast_days=${giorni.coerceIn(1, 16)}"
    }

    /**
     * Quattro decimali, punto decimale: e' un indirizzo web, non un file
     * dell'archivio, e qui la virgola separa le coordinate.
     */
    private fun coordinata(valore: Double): String =
        String.format(java.util.Locale.ROOT, "%.4f", valore)

    private const val CAMPI =
        "weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_sum,precipitation_probability_max,wind_speed_10m_max"

    /** Sette giorni: piu' in la' la previsione non dice niente di utile. */
    const val GIORNI = 7
}

/** Un punto di cui si vuole il meteo, con il nome della tappa che lo ha chiesto. */
data class PuntoMeteo(val nome: String?, val lat: Double, val lon: Double)
