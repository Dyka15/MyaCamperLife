package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le risposte dei due modelli, lette da testo salvato.
 *
 * E' l'unico modo di accorgersi che un fornitore ha cambiato la forma della
 * risposta senza spendere una chiamata a pagamento per ogni prova.
 */
class AiTest {

    // --- Gemini ---------------------------------------------------------------

    private val gemini = """
        {
          "candidates": [{
            "content": {
              "role": "model",
              "parts": [
                {"text": "A Bolsena c'e' un'area di sosta sul lago, "},
                {"text": "otto chilometri da dove sei."}
              ]
            },
            "groundingMetadata": {
              "groundingChunks": [
                {"web": {"uri": "https://esempio.it/aree", "title": "Aree di sosta del lago"}},
                {"web": {"uri": "https://altro.it/bolsena", "title": "Comune di Bolsena"}}
              ]
            }
          }],
          "usageMetadata": {"totalTokenCount": 812}
        }
    """.trimIndent()

    @Test
    fun `le parti del testo si concatenano`() {
        val risposta = Ai.leggiGemini(gemini)!!
        assertEquals(
            "A Bolsena c'e' un'area di sosta sul lago, otto chilometri da dove sei.",
            risposta.testo,
        )
        assertEquals(Modello.GEMINI, risposta.modello)
    }

    @Test
    fun `le fonti arrivano dai groundingChunks`() {
        val fonti = Ai.leggiGemini(gemini)!!.fonti
        assertEquals(2, fonti.size)
        assertEquals("Aree di sosta del lago", fonti.first().titolo)
        assertEquals("https://esempio.it/aree", fonti.first().indirizzo)
    }

    @Test
    fun `una risposta senza fonti va bene comunque`() {
        val corpo = """{"candidates":[{"content":{"parts":[{"text":"Non lo so."}]}}]}"""
        val risposta = Ai.leggiGemini(corpo)!!
        assertEquals("Non lo so.", risposta.testo)
        assertTrue(risposta.fonti.isEmpty())
    }

    @Test
    fun `una risposta senza testo non e' una risposta`() {
        assertNull(Ai.leggiGemini("""{"candidates":[{"content":{"parts":[]}}]}"""))
        assertNull(Ai.leggiGemini("""{"candidates":[]}"""))
        assertNull(Ai.leggiGemini(""))
        assertNull(Ai.leggiGemini("<html>502</html>"))
    }

    @Test
    fun `il corpo per Gemini porta la domanda, il sistema e la ricerca`() {
        val corpo = Ai.corpoGemini("sei un assistente", "dove dormo?")
        assertTrue(corpo, corpo.contains("\"dove dormo?\""))
        assertTrue(corpo, corpo.contains("systemInstruction"))
        assertTrue(corpo, corpo.contains("google_search"))
    }

    @Test
    fun `senza ricerca il corpo non chiede lo strumento`() {
        val corpo = Ai.corpoGemini("prompt", "cronaca", conRicerca = false)
        assertTrue(corpo, !corpo.contains("google_search"))
    }

    @Test
    fun `l'indirizzo di Gemini non porta la chiave`() {
        val indirizzo = Ai.indirizzoGemini("gemini-flash-latest")
        assertTrue(indirizzo, indirizzo.endsWith("gemini-flash-latest:generateContent"))
        assertTrue(indirizzo, !indirizzo.contains("key="))
    }

    // --- Grok -----------------------------------------------------------------

    private val grok = """
        {
          "id": "abc",
          "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": "Prova l'area di Bolsena."},
            "finish_reason": "stop"
          }],
          "citations": ["https://esempio.it/uno", "https://esempio.it/due"]
        }
    """.trimIndent()

    @Test
    fun `il testo di Grok sta in choices message content`() {
        val risposta = Ai.leggiGrok(grok)!!
        assertEquals("Prova l'area di Bolsena.", risposta.testo)
        assertEquals(Modello.GROK, risposta.modello)
    }

    @Test
    fun `le citazioni di Grok si leggono come stringhe o come oggetti`() {
        assertEquals(2, Ai.leggiGrok(grok)!!.fonti.size)

        val conOggetti = """
            {"choices":[{"message":{"content":"Testo"}}],
             "citations":[{"url":"https://esempio.it/tre","title":"Terzo"}]}
        """.trimIndent()
        val fonte = Ai.leggiGrok(conOggetti)!!.fonti.single()
        assertEquals("Terzo", fonte.titolo)
        assertEquals("https://esempio.it/tre", fonte.indirizzo)
    }

    @Test
    fun `una risposta di Grok rotta non fa cadere niente`() {
        assertNull(Ai.leggiGrok(""))
        assertNull(Ai.leggiGrok("""{"choices":[]}"""))
        assertNull(Ai.leggiGrok("""{"choices":[{"message":{"content":""}}]}"""))
    }

    @Test
    fun `il corpo per Grok e' compatibile con OpenAI`() {
        val corpo = Ai.corpoGrok("grok-4-fast", "sistema", "domanda")
        assertTrue(corpo, corpo.contains("\"model\":\"grok-4-fast\""))
        assertTrue(corpo, corpo.contains("\"role\":\"system\""))
        assertTrue(corpo, corpo.contains("\"role\":\"user\""))
        assertTrue(corpo, corpo.contains("search_parameters"))
    }

    // --- gli errori -----------------------------------------------------------

    @Test
    fun `il messaggio d'errore di Gemini si estrae`() {
        val corpo = """{"error":{"code":404,"message":"models/boh is not found","status":"NOT_FOUND"}}"""
        assertEquals("models/boh is not found", Ai.errore(corpo))
    }

    @Test
    fun `il messaggio d'errore di Grok si estrae in tutte le forme`() {
        assertEquals("Incorrect API key", Ai.errore("""{"error":"Incorrect API key"}"""))
        assertEquals("Quota", Ai.errore("""{"error":{"message":"Quota"}}"""))
    }

    @Test
    fun `un errore che non e' JSON si mostra com'e'`() {
        assertEquals("<html>429 Too Many Requests</html>", Ai.errore("<html>429 Too Many Requests</html>"))
        assertNull(Ai.errore(null))
    }

    // --- le fonti -------------------------------------------------------------

    @Test
    fun `una fonte senza titolo si presenta col dominio`() {
        assertEquals("esempio.it", Fonte(null, "https://www.esempio.it/pagina").etichetta())
        assertEquals("Titolo", Fonte("Titolo", "https://esempio.it").etichetta())
    }

    @Test
    fun `le fonti ripetute contano una volta`() {
        val corpo = """
            {"candidates":[{"content":{"parts":[{"text":"x"}]},
             "groundingMetadata":{"groundingChunks":[
               {"web":{"uri":"https://a.it","title":"A"}},
               {"web":{"uri":"https://a.it","title":"A"}}]}}]}
        """.trimIndent()
        assertEquals(1, Ai.leggiGemini(corpo)!!.fonti.size)
    }

    // --- il modello di riposo -------------------------------------------------

    @Test
    fun `un codice di modello sconosciuto non da un modello`() {
        assertEquals(Modello.GEMINI, Modello.da("gemini"))
        assertEquals(Modello.GROK, Modello.da("GROK"))
        assertNull(Modello.da("llama"))
        assertNull(Modello.da(null))
    }
}
