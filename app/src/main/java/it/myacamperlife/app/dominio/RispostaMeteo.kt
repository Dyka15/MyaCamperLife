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
        val perGiorno = fasce(oggetto["hourly"] as? JsonObject)

        return giorni.mapIndexed { i, giorno ->
            Previsione(
                giorno = giorno,
                codice = codici.getOrNull(i),
                minima = minime.getOrNull(i),
                massima = massime.getOrNull(i),
                pioggiaMm = pioggia.getOrNull(i),
                probabilitaPioggia = probabilita.getOrNull(i),
                ventoKmh = vento.getOrNull(i),
                fasce = perGiorno[giorno].orEmpty(),
            )
        }
    }

    /**
     * Il blocco `hourly`, ridotto a tre fasce per giorno.
     *
     * **Si aggrega qui e si salva aggregato.** Le ore grezze sarebbero centosessanta
     * righe per punto per giorno in un file che va letto su un telefono senza
     * rete: quello che serve a decidere se camminare la mattina o il pomeriggio
     * sono tre righe, e le tre righe si calcolano una volta.
     *
     * Le regole, una per campo, ognuna scelta per come si usa il dato:
     *
     * - **temperature**: minima e massima *dentro la fascia*, non la media — "17–22°"
     *   dice come vestirsi, "19°" no.
     * - **probabilita' di pioggia**: la piu' alta. Una fascia con un'ora al 70%
     *   e' una fascia in cui puoi bagnarti, anche se la media dice 20%.
     * - **millimetri**: la somma. Sono una quantita', non uno stato.
     * - **vento**: il massimo. Con un camper conta la raffica, non la media.
     * - **cielo**: il piu' grave fra le ore (→ [CieloMeteo.gravita]). Un
     *   temporale di un'ora e' la cosa da sapere di quel pomeriggio; la media
     *   di sole e temporale sarebbe "nuvoloso", cioe' una previsione falsa.
     *
     * Le ore sono locali perche' la richiesta chiede `timezone=auto`: la fascia
     * si ricava dai caratteri dell'ora nel timestamp, senza fusi da convertire.
     */
    private fun fasce(orario: JsonObject?): Map<String, List<Fascia>> {
        val ore = testi(orario ?: return emptyMap(), "time")
        if (ore.isEmpty()) return emptyMap()

        val temperature = numeri(orario, "temperature_2m")
        val probabilita = interi(orario, "precipitation_probability")
        val pioggia = numeri(orario, "precipitation")
        val codici = interi(orario, "weather_code")
        val vento = numeri(orario, "wind_speed_10m")

        // Chiave: il giorno e la fascia. Le ore fuori dalle tre fasce — la notte —
        // si scartano qui, e non vengono contate da nessuna parte.
        val gruppi = LinkedHashMap<Pair<String, FasciaGiorno>, MutableList<Int>>()
        ore.forEachIndexed { i, istante ->
            val giorno = istante.substringBefore('T').takeIf { it.length == 10 } ?: return@forEachIndexed
            val ora = istante.substringAfter('T', "").take(2).toIntOrNull() ?: return@forEachIndexed
            val fascia = FasciaGiorno.di(ora) ?: return@forEachIndexed
            gruppi.getOrPut(giorno to fascia) { mutableListOf() }.add(i)
        }

        return gruppi.entries
            .groupBy({ it.key.first }) { (chiave, indici) ->
                val temperatureDentro = indici.mapNotNull { temperature.getOrNull(it) }
                Fascia(
                    quale = chiave.second,
                    codice = indici.mapNotNull { codici.getOrNull(it) }
                        .maxByOrNull { CieloMeteo.da(it).gravita },
                    minima = temperatureDentro.minOrNull(),
                    massima = temperatureDentro.maxOrNull(),
                    pioggiaMm = indici.mapNotNull { pioggia.getOrNull(it) }
                        .takeIf { it.isNotEmpty() }?.sum(),
                    probabilitaPioggia = indici.mapNotNull { probabilita.getOrNull(it) }.maxOrNull(),
                    ventoKmh = indici.mapNotNull { vento.getOrNull(it) }.maxOrNull(),
                )
            }
            .mapValues { (_, fasce) -> fasce.sortedBy { it.quale.ordinal } }
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
            "&hourly=$CAMPI_ORARI" +
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

    /**
     * I campi orari, da cui si ricavano le tre fasce.
     *
     * Cinque e non piu': ogni campo in piu' sono centosessantotto numeri per
     * punto, e con dieci tappe la risposta cresce in fretta. Questi cinque sono
     * quelli che finiscono nella riga di una fascia.
     */
    private const val CAMPI_ORARI =
        "weather_code,temperature_2m,precipitation,precipitation_probability,wind_speed_10m"

    /** Sette giorni: piu' in la' la previsione non dice niente di utile. */
    const val GIORNI = 7
}

/** Un punto di cui si vuole il meteo, con il nome della tappa che lo ha chiesto. */
data class PuntoMeteo(val nome: String?, val lat: Double, val lon: Double)
