package it.myacamperlife.app.rete

import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Modello

/**
 * Quali modelli vede una chiave.
 *
 * **Si riassume in una riga**, e non e' un dettaglio di presentazione: questa
 * risposta finisce in `impostazioni.json` e in una schermata, e in entrambi i
 * posti serve una frase che si legge e da cui si copia un identificativo. Il
 * fallimento si riassume allo stesso modo, col codice e la frase del servizio:
 * «rifiutata con 401» e «niente rete» hanno due rimedi diversi.
 */
sealed interface EsitoModelli {

    fun riassunto(): String

    data class Riuscito(val modello: Modello, val modelli: List<String>) : EsitoModelli {
        override fun riassunto(): String = when {
            modelli.isEmpty() -> "${modello.nome}: nessun modello elencato"
            else -> "${modello.nome}: ${modelli.size} visibili — " +
                modelli.take(QUANTI).joinToString(", ") +
                if (modelli.size > QUANTI) ", …" else ""
        }
    }

    data class Guaio(val modello: Modello, val guaio: GuaioAi) : EsitoModelli {
        override fun riassunto(): String = "${modello.nome}: " + when (guaio) {
            GuaioAi.SenzaChiave -> "nessuna chiave configurata"
            GuaioAi.SenzaRete -> "niente rete"
            is GuaioAi.Rifiutata -> "rifiutata con ${guaio.codice}: ${guaio.messaggio ?: "?"}"
            is GuaioAi.Vuota -> "ha risposto, ma senza elenco"
        }
    }

    companion object {
        /**
         * Quanti nomi entrano nella riga. Groq ne elenca una trentina: metterli
         * tutti la renderebbe illeggibile, e i primi in ordine alfabetico bastano
         * a capire se la chiave vede quello che cerchi.
         */
        const val QUANTI = 20
    }
}
