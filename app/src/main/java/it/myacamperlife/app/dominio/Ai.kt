package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Il corpo della richiesta e la lettura della risposta, per i due modelli.
 *
 * **Sta nel dominio, come i parser di Open-Meteo e di Overpass**, e per la
 * stessa ragione: comporre un JSON e leggerne un altro sono trasformazioni pure,
 * e si verificano su risposte salvate su file invece di spendere una chiamata a
 * pagamento per ogni prova. Il giorno che un fornitore cambia la forma della
 * risposta, lo scopre un test.
 *
 * **La ricerca web e' compresa nel modello.** Sia Gemini sia Grok la eseguono
 * lato server: l'app manda una domanda, il modello cerca da se' e risponde con
 * le fonti. Non c'e' nessun motore di ricerca da integrare, ed e' il dettaglio
 * che rende questa fase piccola invece che enorme.
 */
object Ai {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- Gemini ---------------------------------------------------------------

    /**
     * La chiave va nell'intestazione e non nell'indirizzo: un Uri finisce nei
     * log di sistema e nella cronologia dei proxy, una intestazione molto meno.
     */
    fun indirizzoGemini(modello: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${modello.trim()}:generateContent"

    fun corpoGemini(sistema: String, domanda: String, conRicerca: Boolean = true): String =
        buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", domanda) }
                    }
                }
            }
            if (sistema.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", sistema) }
                    }
                }
            }
            if (conRicerca) {
                putJsonArray("tools") {
                    addJsonObject { putJsonObject("google_search") {} }
                }
            }
        }.toString()

    /**
     * Il testo sta in `candidates[0].content.parts[*].text`, e le parti possono
     * essere piu' di una: si concatenano. Le fonti stanno in
     * `groundingMetadata.groundingChunks[*].web`.
     */
    fun leggiGemini(corpo: String): RispostaModello? {
        val radice = oggetto(corpo) ?: return null
        val candidato = (radice["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return null

        val parti = ((candidato["content"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        val testo = parti
            .mapNotNull { parte -> (parte as? JsonObject)?.let { stringa(it, "text") } }
            .joinToString("")
            .trim()
        if (testo.isEmpty()) return null

        val pezzi = ((candidato["groundingMetadata"] as? JsonObject)
            ?.get("groundingChunks") as? JsonArray).orEmpty()
        val fonti = pezzi.mapNotNull { pezzo ->
            val web = (pezzo as? JsonObject)?.get("web") as? JsonObject ?: return@mapNotNull null
            val indirizzo = stringa(web, "uri") ?: return@mapNotNull null
            Fonte(titolo = stringa(web, "title"), indirizzo = indirizzo)
        }

        return RispostaModello(testo, fonti.distinctBy { it.indirizzo }, Modello.GEMINI)
    }

    // --- Grok -----------------------------------------------------------------

    /** L'API di xAI e' compatibile con quella di OpenAI: stessa forma. */
    fun indirizzoGrok(): String = "https://api.x.ai/v1/chat/completions"

    fun corpoGrok(
        modello: String,
        sistema: String,
        domanda: String,
        conRicerca: Boolean = true,
    ): String = buildJsonObject {
        put("model", modello.trim())
        putJsonArray("messages") {
            if (sistema.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", sistema)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", domanda)
            }
        }
        if (conRicerca) {
            putJsonObject("search_parameters") {
                put("mode", "auto")
            }
        }
    }.toString()

    /** Testo in `choices[0].message.content`, fonti in `citations`. */
    fun leggiGrok(corpo: String): RispostaModello? {
        val radice = oggetto(corpo) ?: return null
        val scelta = (radice["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return null
        val messaggio = scelta["message"] as? JsonObject ?: return null
        val testo = stringa(messaggio, "content")?.trim()?.takeUnless { it.isEmpty() }
            ?: return null

        val fonti = (radice["citations"] as? JsonArray).orEmpty().mapNotNull { citazione ->
            // Le citazioni possono essere stringhe o oggetti, a seconda della
            // versione: si accettano entrambe invece di scommettere su una.
            when (citazione) {
                is JsonObject -> stringa(citazione, "url")?.let {
                    Fonte(stringa(citazione, "title"), it)
                }
                else -> runCatching { citazione.jsonPrimitive.contentOrNull }
                    .getOrNull()
                    ?.takeIf { it.startsWith("http") }
                    ?.let { Fonte(null, it) }
            }
        }

        return RispostaModello(testo, fonti.distinctBy { it.indirizzo }, Modello.GROK)
    }

    // --- l'errore -------------------------------------------------------------

    /**
     * Il messaggio d'errore, dal corpo della risposta.
     *
     * **Si mostra all'utente cosi' com'e'.** Un identificativo di modello
     * ritirato, una chiave scaduta, una quota finita: sono tre situazioni con
     * tre rimedi diversi, e distinguerle richiede leggere l'originale. Un
     * generico "errore di rete" costringerebbe a indovinare.
     */
    fun errore(corpo: String?): String? {
        val radice = oggetto(corpo ?: return null) ?: return corpo.take(300)
        // Gemini: {"error": {"message": "..."}}. Grok: {"error": "..."} oppure
        // {"error": {"message": "..."}}.
        (radice["error"] as? JsonObject)?.let { errore ->
            stringa(errore, "message")?.let { return it }
        }
        stringa(radice, "error")?.let { return it }
        stringa(radice, "message")?.let { return it }
        return corpo.take(300)
    }

    private fun oggetto(corpo: String): JsonObject? =
        runCatching { json.parseToJsonElement(corpo) as? JsonObject }.getOrNull()

    private fun stringa(oggetto: JsonObject, nome: String): String? =
        runCatching { oggetto[nome]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
