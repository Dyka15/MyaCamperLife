package it.myacamperlife.app.rete

import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Modello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La riga che finisce in `impostazioni.json` e sullo schermo.
 *
 * **E' l'unica cosa che l'utente vedra' di questa verifica**, e l'utente ha un
 * telefono e nient'altro: se la riga non dice l'identificativo da copiare, o non
 * dice perche' l'elenco manca, la funzione non serve a niente.
 */
class EsitoModelliTest {

    @Test
    fun `l'elenco dice quanti sono e come si chiamano`() {
        val riga = EsitoModelli.Riuscito(
            Modello.GROQ,
            listOf("groq/compound", "groq/compound-mini", "openai/gpt-oss-120b"),
        ).riassunto()
        assertEquals(
            "Groq: 3 visibili — groq/compound, groq/compound-mini, openai/gpt-oss-120b",
            riga,
        )
    }

    @Test
    fun `un elenco lungo si taglia, e si vede che e' tagliato`() {
        val molti = (1..30).map { "modello-$it" }
        val riga = EsitoModelli.Riuscito(Modello.GROQ, molti).riassunto()
        assertTrue(riga, riga.startsWith("Groq: 30 visibili — "))
        // I puntini sono la parte importante: senza, sembrerebbe che i modelli
        // visibili siano venti e il conteggio sarebbe una contraddizione.
        assertTrue(riga, riga.endsWith(", …"))
        assertEquals(EsitoModelli.QUANTI, riga.split(" — ").last().split(", ").size - 1)
    }

    @Test
    fun `un elenco vuoto lo dice invece di sembrare un successo`() {
        assertEquals(
            "Grok: nessun modello elencato",
            EsitoModelli.Riuscito(Modello.GROK, emptyList()).riassunto(),
        )
    }

    @Test
    fun `un rifiuto porta il codice e la frase del servizio`() {
        val riga = EsitoModelli.Guaio(
            Modello.GROQ,
            GuaioAi.Rifiutata(Modello.GROQ, 401, "Invalid API Key"),
        ).riassunto()
        // Il 401 e il 429 hanno due rimedi diversi — chiave sbagliata, quota
        // finita — e distinguerli richiede il codice, non una parafrasi.
        assertEquals("Groq: rifiutata con 401: Invalid API Key", riga)
    }

    @Test
    fun `i guai senza messaggio restano leggibili`() {
        assertEquals(
            "Groq: nessuna chiave configurata",
            EsitoModelli.Guaio(Modello.GROQ, GuaioAi.SenzaChiave).riassunto(),
        )
        assertEquals(
            "Gemini: niente rete",
            EsitoModelli.Guaio(Modello.GEMINI, GuaioAi.SenzaRete).riassunto(),
        )
        assertEquals(
            "Groq: rifiutata con 500: ?",
            EsitoModelli.Guaio(Modello.GROQ, GuaioAi.Rifiutata(Modello.GROQ, 500, null))
                .riassunto(),
        )
    }
}
