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

    // --- Groq -----------------------------------------------------------------

    /**
     * Una risposta di un `compound`, con lo strumento eseguito che riporta i
     * risultati **come stringa JSON**: e' la forma che rende inutile cercare le
     * fonti in un punto preciso.
     */
    private val groq = """
        {
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "A Rothenburg il museo del Natale apre alle 10.",
              "executed_tools": [{
                "type": "search",
                "output": "{\"results\":[{\"url\":\"https://rothenburg.de/museo\",\"title\":\"Museo\"},{\"url\":\"https://esempio.de/orari\"}]}"
              }]
            }
          }],
          "model": "groq/compound-mini"
        }
    """.trimIndent()

    @Test
    fun `il testo di Groq sta dove lo mette OpenAI`() {
        val risposta = Ai.leggiGroq(groq)!!
        assertEquals("A Rothenburg il museo del Natale apre alle 10.", risposta.testo)
        assertEquals(Modello.GROQ, risposta.modello)
    }

    @Test
    fun `le fonti di Groq si trovano anche dentro un JSON messo in una stringa`() {
        val fonti = Ai.leggiGroq(groq)!!.fonti
        assertEquals(2, fonti.size)
        assertEquals("Museo", fonti.first().titolo)
        assertEquals("https://rothenburg.de/museo", fonti.first().indirizzo)
    }

    @Test
    fun `le fonti di Groq si cercano in tutta la risposta, non solo nel messaggio`() {
        // La prima versione guardava dentro `message` e in `citations`, e su una
        // risposta vera non ha trovato niente: i risultati stavano altrove.
        val fuori = """
            {"choices":[{"message":{"content":"Testo"}}],
             "search_results":{"results":[{"url":"https://esempio.de/uno","title":"Uno"}]}}
        """.trimIndent()
        val fonte = Ai.leggiGroq(fuori)!!.fonti.single()
        assertEquals("https://esempio.de/uno", fonte.indirizzo)
        assertEquals("Uno", fonte.titolo)
    }

    @Test
    fun `senza fonti dichiarate valgono i link scritti nella risposta`() {
        // Un `compound` cita dentro la prosa. Un link ricavato dal testo e'
        // meno preciso di uno dichiarato, ed e' infinitamente meglio di niente:
        // e' l'indirizzo che si va a controllare.
        val conProsa = """
            {"choices":[{"message":{"content":
              "Prova [Rothsee Camping](https://rothsee-camping.de/info) oppure https://roth.de/sosta."}}]}
        """.trimIndent()
        val fonti = Ai.leggiGroq(conProsa)!!.fonti
        assertEquals(2, fonti.size)
        assertEquals("Rothsee Camping", fonti.first().titolo)
        assertEquals("https://rothsee-camping.de/info", fonti.first().indirizzo)
        // Il punto finale della frase non fa parte dell'indirizzo.
        assertEquals("https://roth.de/sosta", fonti.last().indirizzo)
    }

    @Test
    fun `un link dichiarato e lo stesso link nel testo contano una volta`() {
        val doppio = """
            {"choices":[{"message":{"content":"Vedi [Qui](https://a.de/x)",
              "executed_tools":[{"output":"{\"results\":[{\"url\":\"https://a.de/x\"}]}"}]}}]}
        """.trimIndent()
        assertEquals(1, Ai.leggiGroq(doppio)!!.fonti.size)
    }

    @Test
    fun `l'impronta dice i campi e non il contenuto`() {
        val impronta = Ai.impronta(groq)
        assertTrue(impronta, impronta.contains("choices"))
        assertTrue(impronta, impronta.contains("executed_tools"))
        // Il contenuto no: quella riga finisce in un file rispecchiato su un cloud.
        assertTrue(impronta, !impronta.contains("Rothenburg"))
    }

    @Test
    fun `l'impronta di una risposta che non e' JSON lo dice`() {
        assertTrue(Ai.impronta("<html>502</html>").startsWith("risposta non JSON"))
    }

    @Test
    fun `una risposta di Groq senza ricerca non ha fonti ma ha il testo`() {
        // E' il caso di un modello secco tipo openai/gpt-oss-120b: risponde a
        // memoria. Deve funzionare, e deve essere evidente che fonti non ce ne
        // sono — non un errore.
        val secco = """{"choices":[{"message":{"content":"Non lo so con certezza."}}]}"""
        val risposta = Ai.leggiGroq(secco)!!
        assertEquals("Non lo so con certezza.", risposta.testo)
        assertTrue(risposta.fonti.isEmpty())
    }

    @Test
    fun `una risposta di Groq rotta non fa cadere niente`() {
        assertNull(Ai.leggiGroq(""))
        assertNull(Ai.leggiGroq("""{"choices":[]}"""))
        assertNull(Ai.leggiGroq("""{"choices":[{"message":{"content":"   "}}]}"""))
    }

    @Test
    fun `il corpo per Groq non chiede la ricerca`() {
        // Su Groq la ricerca e' una proprieta' del modello, non della richiesta:
        // un parametro inventato tornerebbe come 400, che sembra un problema di
        // chiave.
        val corpo = Ai.corpoGroq("groq/compound-mini", "sistema", "domanda")
        assertTrue(corpo, corpo.contains("\"model\":\"groq/compound-mini\""))
        assertTrue(corpo, corpo.contains("\"role\":\"system\""))
        assertTrue(corpo, !corpo.contains("search_parameters"))
        assertTrue(corpo, !corpo.contains("tools"))
    }

    // --- quali modelli vede la chiave -----------------------------------------

    @Test
    fun `l'elenco compatibile con OpenAI si legge da data id`() {
        val corpo = """
            {"object":"list","data":[
              {"id":"openai/gpt-oss-120b","object":"model"},
              {"id":"groq/compound-mini","object":"model"},
              {"id":"groq/compound","object":"model"}]}
        """.trimIndent()
        assertEquals(
            listOf("groq/compound", "groq/compound-mini", "openai/gpt-oss-120b"),
            Ai.leggiModelli(corpo),
        )
    }

    @Test
    fun `l'elenco di Gemini si legge da models name senza il prefisso`() {
        val corpo = """
            {"models":[
              {"name":"models/gemini-flash-latest","displayName":"Flash"},
              {"name":"models/gemini-pro-latest"}]}
        """.trimIndent()
        assertEquals(
            listOf("gemini-flash-latest", "gemini-pro-latest"),
            Ai.leggiModelli(corpo),
        )
    }

    @Test
    fun `un elenco che non si capisce e' vuoto, non un guasto`() {
        assertTrue(Ai.leggiModelli("").isEmpty())
        assertTrue(Ai.leggiModelli("""{"data":[]}""").isEmpty())
        assertTrue(Ai.leggiModelli("""{"error":{"message":"Invalid API Key"}}""").isEmpty())
    }

    @Test
    fun `ogni fornitore ha il suo indirizzo per l'elenco dei modelli`() {
        assertEquals("https://api.groq.com/openai/v1/models", Ai.indirizzoModelli(Modello.GROQ))
        assertEquals("https://api.x.ai/v1/models", Ai.indirizzoModelli(Modello.GROK))
        assertTrue(Ai.indirizzoModelli(Modello.GEMINI).endsWith("/v1beta/models"))
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
        assertEquals(Modello.GROQ, Modello.da("groq"))
        assertNull(Modello.da("llama"))
        assertNull(Modello.da(null))
    }

    @Test
    fun `Groq e Grok restano due fornitori distinti`() {
        // Una lettera di differenza, due servizi che non c'entrano niente: se un
        // giorno qualcuno unificasse i due codici, le chiavi finirebbero
        // scambiate e il guasto sarebbe incomprensibile.
        assertTrue(Modello.GROQ != Modello.GROK)
        assertTrue(Modello.GROQ.codice != Modello.GROK.codice)
        assertEquals("groq/compound-mini", Modello.GROQ.modelloDiRiposo)
    }
}
