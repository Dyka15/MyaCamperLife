package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Un posto trovato cercando un indirizzo.
 *
 * [nome] e' quello corto da mostrare in elenco, [descrizione] quello lungo che
 * distingue fra due omonimi: in Italia ci sono quattro Castelnuovo e senza la
 * provincia non si sa quale sia quello giusto.
 */
data class Indirizzo(
    val nome: String,
    val descrizione: String?,
    val lat: Double,
    val lon: Double,
) {
    val coordinate: Coordinate get() = Coordinate(lat, lon)
}

/**
 * Costruisce e legge la ricerca di un indirizzo su Nominatim.
 *
 * **E' il secondo strato**: si arriva qui solo quando fra i toponimi scaricati
 * non c'e' niente che corrisponda. Un indirizzo civico, il nome di un
 * campeggio, una via: cose che nessun elenco di paesi puo' sapere.
 *
 * Nominatim e' il geocodificatore di OpenStreetMap, gratuito e senza chiave, e
 * come OSRM e Overpass e' **un servizio di cortesia**: la sua politica d'uso
 * chiede al massimo una richiesta al secondo e un `User-Agent` che dica chi
 * chiama. Qui si interroga solo quando l'utente tocca "Cerca", che e' un ritmo
 * ampiamente dentro quel limite.
 *
 * Funzioni pure: l'indirizzo da chiamare e la lettura della risposta.
 */
object RispostaIndirizzo {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun indirizzo(cercato: String, quanti: Int = QUANTI): String {
        require(cercato.isNotBlank()) { "senza testo non c'e' niente da cercare" }
        return "$SERVIZIO/search" +
            "?q=${codifica(cercato.trim())}" +
            "&format=jsonv2" +
            "&addressdetails=0" +
            "&limit=${quanti.coerceIn(1, 20)}"
    }

    /**
     * `lat` e `lon` arrivano **come stringhe**, non come numeri: e' una
     * particolarita' di Nominatim, e leggerle come numeri darebbe zero
     * risultati senza errori.
     */
    fun leggi(corpo: String): List<Indirizzo> {
        val radice = runCatching { json.parseToJsonElement(corpo) as? JsonArray }.getOrNull()
            ?: return emptyList()

        return radice.mapNotNull { elemento ->
            val oggetto = elemento as? JsonObject ?: return@mapNotNull null
            val lat = numero(oggetto, "lat") ?: return@mapNotNull null
            val lon = numero(oggetto, "lon") ?: return@mapNotNull null
            val completo = testo(oggetto, "display_name")

            Indirizzo(
                nome = testo(oggetto, "name")?.takeUnless { it.isBlank() }
                    ?: completo?.substringBefore(',')?.trim()
                    ?: return@mapNotNull null,
                // Il nome completo senza la prima parte, che e' gia' il nome:
                // resta la provincia, la regione, il paese.
                descrizione = completo?.substringAfter(',', "")?.trim()?.takeUnless { it.isEmpty() },
                lat = lat,
                lon = lon,
            )
        }.filter { it.coordinate.valide }
    }

    private fun testo(oggetto: JsonObject, nome: String): String? =
        runCatching { oggetto[nome]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun numero(oggetto: JsonObject, nome: String): Double? =
        testo(oggetto, nome)?.trim()?.toDoubleOrNull()

    /** Percent-encoding di quello che l'utente ha scritto. */
    private fun codifica(testo: String): String =
        java.net.URLEncoder.encode(testo, "UTF-8")

    private const val SERVIZIO = "https://nominatim.openstreetmap.org"

    /** Cinque risultati: una tendina piu' lunga non si legge in viaggio. */
    const val QUANTI = 5
}
