package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Un punto dell'itinerario come lo scrive il file `.md`, prima di diventare
 * una [Tappa]: senza identita' e senza stato.
 */
data class Waypoint(
    val nome: String,
    val lat: Double,
    val lon: Double,
    val tipo: String? = null,
    val giorno: String? = null,
    val descrizione: String? = null,
)

/**
 * Legge un itinerario dal formato che il sistema attuale produce gia': un
 * documento Markdown con dentro un blocco JSON che contiene `waypoints`.
 *
 * Funzione pura, nessuna dipendenza da Android: verificabile con test unitari
 * normali. Le scelte di tolleranza non sono pigrizia, sono il punto — il file
 * arriva da fuori e non e' sotto il nostro controllo:
 *
 * - il blocco JSON si cerca ovunque nel documento, dentro o fuori da un
 *   recinto ```, perche' non e' detto che sia recintato
 * - `waypoints` si cerca anche annidato, non solo alla radice
 * - le coordinate si accettano come numero o come stringa
 * - `lng`, `lon`, `long` e `longitude` sono lo stesso campo
 * - un punto senza coordinate valide viene saltato, non fa fallire tutto
 */
object Itinerario {

    sealed interface Esito {
        data class Riuscito(
            /** Il primo titolo `#` del Markdown, se c'e'. */
            val nome: String?,
            val tappe: List<Waypoint>,
            /** Punti scartati perche' senza coordinate utilizzabili. */
            val scartati: Int,
        ) : Esito

        data class Fallito(val motivo: Motivo) : Esito
    }

    enum class Motivo {
        /** Nel documento non c'e' niente che assomigli a un blocco JSON. */
        NESSUN_JSON,

        /** C'e' del JSON, ma nessun `waypoints` da nessuna parte. */
        NESSUN_WAYPOINTS,

        /** C'e' `waypoints`, ma nessun punto ha coordinate valide. */
        NESSUNA_TAPPA,
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun leggi(documento: String): Esito {
        val candidati = oggettiJson(documento)
        if (candidati.isEmpty()) return Esito.Fallito(Motivo.NESSUN_JSON)

        val elenco = candidati.asSequence()
            .mapNotNull { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            .mapNotNull { cercaWaypoints(it) }
            .firstOrNull()
            ?: return Esito.Fallito(Motivo.NESSUN_WAYPOINTS)

        val punti = elenco.mapNotNull { waypoint(it) }
        if (punti.isEmpty()) return Esito.Fallito(Motivo.NESSUNA_TAPPA)

        return Esito.Riuscito(
            nome = titolo(documento),
            tappe = punti,
            scartati = elenco.size - punti.size,
        )
    }

    /** Il primo titolo di primo livello del Markdown. */
    private fun titolo(documento: String): String? =
        documento.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeUnless { it.isEmpty() }

    /**
     * Cerca un array `waypoints` a qualsiasi profondita'. Un itinerario
     * scritto come `{"itinerario": {"waypoints": [...]}}` deve funzionare
     * come uno che lo mette alla radice.
     */
    private fun cercaWaypoints(elemento: JsonElement): JsonArray? = when (elemento) {
        is JsonObject -> {
            val diretto = elemento["waypoints"] as? JsonArray
            diretto?.takeIf { it.isNotEmpty() }
                ?: elemento.values.asSequence().mapNotNull { cercaWaypoints(it) }.firstOrNull()
        }
        is JsonArray -> elemento.asSequence().mapNotNull { cercaWaypoints(it) }.firstOrNull()
        else -> null
    }

    private fun waypoint(elemento: JsonElement): Waypoint? {
        val oggetto = elemento as? JsonObject ?: return null

        val lat = numero(oggetto, "lat", "latitude") ?: return null
        val lon = numero(oggetto, "lng", "lon", "long", "longitude") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        val nome = testo(oggetto, "name", "nome")
            ?: testo(oggetto, "title", "titolo")
            ?: "Senza nome"

        return Waypoint(
            nome = nome,
            lat = lat,
            lon = lon,
            tipo = testo(oggetto, "type", "tipo"),
            giorno = testo(oggetto, "giorno", "day", "date", "data"),
            descrizione = testo(oggetto, "description", "descrizione", "note"),
        )
    }

    private fun testo(oggetto: JsonObject, vararg chiavi: String): String? = chiavi
        .asSequence()
        .mapNotNull { (oggetto[it] as? JsonPrimitive)?.contentOrNull }
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it != "null" }

    private fun numero(oggetto: JsonObject, vararg chiavi: String): Double? = chiavi
        .asSequence()
        .mapNotNull { oggetto[it] as? JsonPrimitive }
        .mapNotNull { primitivo ->
            primitivo.doubleOrNull
                ?: primitivo.contentOrNull?.trim()?.replace(',', '.')?.toDoubleOrNull()
        }
        .firstOrNull()

    /**
     * Tutti i testi che sembrano un oggetto JSON, in ordine di apparizione.
     * Si scandisce il documento intero: cercare i recinti ``` sarebbe piu'
     * elegante e piu' fragile.
     */
    private fun oggettiJson(documento: String): List<String> {
        val trovati = mutableListOf<String>()
        var i = 0
        while (i < documento.length) {
            if (documento[i] == '{') {
                val fine = fineOggetto(documento, i)
                if (fine > i) {
                    trovati.add(documento.substring(i, fine + 1))
                    i = fine + 1
                    continue
                }
            }
            i++
        }
        return trovati
    }

    /**
     * Indice della graffa che chiude quella aperta in [inizio], oppure -1.
     * Le graffe dentro una stringa non contano, e la sequenza di escape
     * salta il carattere successivo: senza questo, una descrizione che
     * contiene `{` spezzerebbe il conteggio.
     */
    private fun fineOggetto(documento: String, inizio: Int): Int {
        var profondita = 0
        var dentroStringa = false
        var i = inizio
        while (i < documento.length) {
            val c = documento[i]
            if (dentroStringa) {
                when (c) {
                    '\\' -> i++
                    '"' -> dentroStringa = false
                }
            } else {
                when (c) {
                    '"' -> dentroStringa = true
                    '{' -> profondita++
                    '}' -> {
                        profondita--
                        if (profondita == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }
}
