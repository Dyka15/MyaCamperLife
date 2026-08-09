package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * I campi in piu' di una tappa, avanti e indietro fra memoria e una cella di
 * CSV.
 *
 * **Perche' JSON dentro una cella.** Una tabella con una colonna per ogni campo
 * che un itinerario potrebbe portare non si puo' scrivere: i campi non li
 * decidiamo noi. L'alternativa a una cella strutturata sarebbe buttarli, che e'
 * quello che l'app faceva. JSON compatto e' brutto da guardare in un foglio di
 * calcolo ma ha due proprieta' che qui contano piu' della bellezza: e' senza
 * ambiguita' — nessun separatore da indovinare, nessun valore che contiene il
 * separatore — e **sta su una riga sola**, perche' i ritorni a capo sono gia'
 * escapati. La regola del formato resta intatta.
 *
 * L'ordine si conserva: `JsonObject` mantiene quello di inserimento, e l'ordine
 * in cui chi ha scritto l'itinerario ha messo le cose e' un'informazione.
 *
 * Funzioni pure.
 */
object CampiExtra {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Vuoto diventa stringa vuota, non `{}`: una cella vuota si legge meglio. */
    fun scrivi(campi: List<Pair<String, String>>): String {
        if (campi.isEmpty()) return ""
        val oggetto = JsonObject(campi.associate { (chiave, valore) -> chiave to JsonPrimitive(valore) })
        return oggetto.toString()
    }

    /**
     * Rilegge i campi. Una cella vuota, o rovinata da una modifica a mano, da'
     * un elenco vuoto: un campo in piu' illeggibile non deve poter impedire di
     * aprire un itinerario.
     */
    fun leggi(cella: String?): List<Pair<String, String>> {
        val testo = cella?.trim()?.takeUnless { it.isEmpty() } ?: return emptyList()
        val oggetto = runCatching { json.parseToJsonElement(testo) as? JsonObject }.getOrNull()
            ?: return emptyList()
        return oggetto.mapNotNull { (chiave, valore) ->
            val contenuto = (valore as? JsonPrimitive)?.contentOrNull ?: valore.toString()
            contenuto.takeUnless { it.isEmpty() }?.let { chiave to it }
        }
    }

    /**
     * Come si mostra un campo in piu': `orari: 9-18`.
     *
     * Il nome resta quello del file, senza tentativi di tradurlo: chi ha scritto
     * l'itinerario ha scelto quella parola, e sostituirla vorrebbe dire far
     * finta di sapere cosa intendeva.
     */
    fun riga(campo: Pair<String, String>): String = "${campo.first}: ${campo.second}"
}
