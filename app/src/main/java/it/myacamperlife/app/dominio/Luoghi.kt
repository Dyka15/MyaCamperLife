package it.myacamperlife.app.dominio

import kotlin.math.roundToInt

/**
 * Un toponimo: il nome di un posto abitato, con quanta gente ci vive.
 *
 * La popolazione non e' un dettaglio da enciclopedia: serve a scegliere fra due
 * nomi ugualmente vicini. "3 km da Orvieto" dice qualcosa; "3 km da Sugano",
 * frazione di duecento anime, non dice niente a nessuno.
 */
data class Luogo(
    val nome: String,
    val lat: Double,
    val lon: Double,
    val abitanti: Int? = null,
)

/**
 * Il nome del posto piu' vicino a una coppia di coordinate, **senza rete**.
 *
 * E' il geocoding inverso che il `Geocoder` di Android fa solo online. Qui si
 * risolve con un elenco di toponimi messo da parte in anticipo: il risultato e'
 * "3 km da Orvieto" invece dell'indirizzo civico esatto, che per un diario di
 * viaggio e' quello che serve davvero.
 *
 * Funzione pura.
 */
class Luoghi(val tutti: List<Luogo> = emptyList()) {

    val vuoto: Boolean get() = tutti.isEmpty()

    /**
     * Il posto piu' vicino, entro [entro] chilometri.
     *
     * **Non e' solo il piu' vicino in linea d'aria.** Fra due toponimi a
     * distanza simile vince quello piu' grande: la frazione a due chilometri e
     * il paese a tre non valgono lo stesso, e chi legge il diario riconosce il
     * secondo. La preferenza vale solo entro [PARI_MERITO_KM]; oltre, la
     * vicinanza torna a comandare.
     */
    fun piuVicino(lat: Double, lon: Double, entro: Double = RAGGIO_KM): Luogo? {
        val candidati = tutti
            .map { it to Distanza.km(lat, lon, it.lat, it.lon) }
            .filter { it.second <= entro }
        if (candidati.isEmpty()) return null

        val minima = candidati.minOf { it.second }
        return candidati
            .filter { it.second <= minima + PARI_MERITO_KM }
            .maxByOrNull { it.first.abitanti ?: 0 }
            ?.first
    }

    /**
     * Come si dice dove sei: "Orvieto" se ci sei dentro, "3 km da Orvieto" se
     * sei nei paraggi, `null` se non si sa.
     *
     * Il `null` e' importante: chi chiama ripiega sul nome della tappa, che e'
     * comunque meglio di un nome sbagliato.
     */
    fun descrizione(lat: Double, lon: Double, entro: Double = RAGGIO_KM): String? {
        val luogo = piuVicino(lat, lon, entro) ?: return null
        val km = Distanza.km(lat, lon, luogo.lat, luogo.lon)
        return if (km <= DENTRO_KM) luogo.nome else "${km.roundToInt()} km da ${luogo.nome}"
    }

    /**
     * Il solo nome, senza la distanza: serve a battezzare un file, dove
     * "3-km-da-Orvieto" sarebbe rumore.
     */
    fun nome(lat: Double, lon: Double, entro: Double = RAGGIO_KM): String? =
        piuVicino(lat, lon, entro)?.nome

    companion object {
        /** Entro due chilometri dal centro si dice di essere nel paese. */
        const val DENTRO_KM = 2.0

        /**
         * Oltre venticinque chilometri dal centro abitato piu' vicino, dirne il
         * nome sarebbe fuorviante: sei altrove.
         */
        const val RAGGIO_KM = 25.0

        /**
         * Due toponimi entro questo scarto contano come ugualmente vicini, e
         * decide la popolazione.
         */
        const val PARI_MERITO_KM = 3.0
    }
}
