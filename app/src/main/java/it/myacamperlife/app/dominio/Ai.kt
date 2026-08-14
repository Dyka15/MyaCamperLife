package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 * Il corpo della richiesta e la lettura della risposta, per ogni fornitore.
 *
 * **Sta nel dominio, come i parser di Open-Meteo e di Overpass**, e per la
 * stessa ragione: comporre un JSON e leggerne un altro sono trasformazioni pure,
 * e si verificano su risposte salvate su file invece di spendere una chiamata a
 * pagamento per ogni prova. Il giorno che un fornitore cambia la forma della
 * risposta, lo scopre un test.
 *
 * **La ricerca web e' compresa nel modello.** L'app manda una domanda, il
 * modello cerca da se' e risponde con le fonti: nessun motore di ricerca da
 * integrare, ed e' il dettaglio che rende questa parte piccola invece che
 * enorme. Come si chiede pero' cambia da fornitore a fornitore — Gemini vuole
 * uno strumento dichiarato, Grok un parametro, Groq **niente**, perche' li' la
 * ricerca e' una proprieta' del modello scelto e non della richiesta.
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

    // --- Groq -----------------------------------------------------------------

    /** Anche Groq parla la lingua di OpenAI: stesso corpo, stesso posto per il testo. */
    fun indirizzoGroq(): String = "https://api.groq.com/openai/v1/chat/completions"

    /**
     * Il corpo per Groq.
     *
     * **Nessun parametro di ricerca**, al contrario di Grok: su Groq la ricerca
     * web non si chiede, ce l'hanno di natura i sistemi `compound` e non ce
     * l'hanno gli altri. Un parametro inventato verrebbe rifiutato con un 400 —
     * e peggio: sembrerebbe un problema di chiave.
     *
     * Il che rende la ricerca una **scelta di modello** e non di richiesta: per
     * Esplora un `compound`, per la prosa del diario — che non deve cercare
     * niente — va bene un modello secco. Si sceglie dalle impostazioni.
     */
    fun corpoGroq(modello: String, sistema: String, domanda: String): String = buildJsonObject {
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
    }.toString()

    /**
     * Testo in `choices[0].message.content`; le fonti **dove capita**.
     *
     * I `compound` riportano gli strumenti che hanno eseguito, e i risultati
     * della ricerca stanno dentro quel ramo — in una forma che e' cambiata piu'
     * di una volta e che non ho potuto verificare contro una risposta vera.
     * Invece di scommettere su un percorso preciso si raccolgono gli indirizzi
     * **da tutto il sottoalbero del messaggio**: se domani i risultati cambiano
     * posto, le fonti continuano a comparire.
     *
     * Il prezzo di questa scelta e' qualche indirizzo di troppo; il prezzo
     * dell'altra sarebbe una colonna vuota senza che nessuno se ne accorga.
     */
    fun leggiGroq(corpo: String): RispostaModello? {
        val radice = oggetto(corpo) ?: return null
        val scelta = (radice["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return null
        val messaggio = scelta["message"] as? JsonObject ?: return null
        val testo = stringa(messaggio, "content")?.trim()?.takeUnless { it.isEmpty() }
            ?: return null

        // **Tutta la risposta, non solo il messaggio.** La prima versione guardava
        // dentro `message` e in `citations`, e su una risposta vera non ha trovato
        // niente: i risultati stanno da qualche altra parte. Cercare in tutto
        // l'oggetto costa un attraversamento di un JSON piccolo.
        val dichiarate = indirizzi(radice).distinctBy { it.indirizzo }

        // E se il modello non dichiara niente, i link **nella prosa** valgono
        // come fonti: un `compound` scrive "[Rothsee Camping](https://…)" dentro
        // la risposta, e quel link e' esattamente cio' che serve controllare.
        // Meglio una fonte ricavata dal testo che una colonna vuota.
        val fonti = (dichiarate + fontiDalTesto(testo)).distinctBy { it.indirizzo }
        return RispostaModello(testo, fonti, Modello.GROQ)
    }

    /**
     * I collegamenti scritti dentro una risposta.
     *
     * Prima i link Markdown, che portano anche il titolo; poi gli indirizzi nudi.
     * La punteggiatura finale si toglie: "vedi https://esempio.it." non contiene
     * un indirizzo che finisce con un punto.
     */
    fun fontiDalTesto(testo: String): List<Fonte> {
        val marcati = MARKDOWN.findAll(testo).map { trovato ->
            Fonte(
                titolo = trovato.groupValues[1].trim().takeUnless { it.isEmpty() },
                indirizzo = pulisci(trovato.groupValues[2]),
            )
        }
        val nudi = NUDO.findAll(testo).map { Fonte(null, pulisci(it.value)) }
        return (marcati + nudi).distinctBy { it.indirizzo }.toList()
    }

    private fun pulisci(indirizzo: String): String =
        indirizzo.trimEnd('.', ',', ';', ':', ')', ']', '"', '\'', '»')

    private val MARKDOWN = Regex("""\[([^\]]{0,200})]\((https?://[^\s)]+)\)""")
    private val NUDO = Regex("""https?://[^\s)\]<>"']+""")

    /**
     * L'impronta di una risposta: com'e' fatta, non cosa dice.
     *
     * Serve a una domanda che si fa a distanza: **dove ha messo le fonti questo
     * fornitore?** Il formato non e' documentato in modo affidabile e cambia; io
     * ho il codice e non ho il telefono, quindi la risposta deve arrivare in una
     * riga che l'utente puo' leggere e mandarmi. Sono nomi di campi e conteggi:
     * niente della risposta, niente della chiave.
     */
    fun impronta(corpo: String): String {
        val radice = oggetto(corpo) ?: return "risposta non JSON, ${corpo.length} caratteri"
        val messaggio = ((radice["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("message") as? JsonObject
        val fuori = radice.keys.joinToString(",")
        val dentro = messaggio?.keys?.joinToString(",") ?: "nessun message"
        return "campi: [$fuori] message: [$dentro]"
    }

    /**
     * Tutti gli indirizzi web in un pezzo di JSON, col titolo se c'e'.
     *
     * Scende in oggetti e array, e **prova a leggere le stringhe come JSON**: uno
     * strumento eseguito riporta il proprio risultato come testo, e un JSON
     * dentro una stringa e' ancora un JSON.
     */
    private fun indirizzi(elemento: JsonElement?, profondita: Int = 0): List<Fonte> {
        if (elemento == null || profondita > PROFONDITA) return emptyList()
        return when (elemento) {
            is JsonObject -> {
                val mio = stringa(elemento, "url")
                    ?: stringa(elemento, "uri")
                    ?: stringa(elemento, "link")
                val qui = mio
                    ?.takeIf { it.startsWith("http") }
                    ?.let { listOf(Fonte(stringa(elemento, "title"), it)) }
                    .orEmpty()
                qui + elemento.values.flatMap { indirizzi(it, profondita + 1) }
            }
            is JsonArray -> elemento.flatMap { indirizzi(it, profondita + 1) }
            else -> {
                val testo = runCatching { elemento.jsonPrimitive.contentOrNull }.getOrNull()
                    ?: return emptyList()
                val potato = testo.trimStart()
                when {
                    testo.startsWith("http") -> listOf(Fonte(null, testo))
                    potato.startsWith("{") || potato.startsWith("[") ->
                        runCatching { json.parseToJsonElement(testo) }.getOrNull()
                            ?.let { indirizzi(it, profondita + 1) }
                            .orEmpty()
                    else -> emptyList()
                }
            }
        }
    }

    /**
     * Sedici livelli.
     *
     * Erano otto quando la ricerca partiva dal messaggio, e sono diventati pochi
     * appena e' partita dalla radice: `choices` → scelta → `message` →
     * `executed_tools` → strumento → `output` → il JSON dentro quella stringa →
     * `results` → risultato fa nove passi, e al nono le fonti sparivano **in
     * silenzio**. Un tetto serve — un JSON malformato potrebbe essere profondo
     * quanto vuole — ma va tenuto largo rispetto alla struttura vera.
     */
    private const val PROFONDITA = 16

    // --- quali modelli vede la chiave -----------------------------------------

    /**
     * L'indirizzo che elenca i modelli visibili a una chiave.
     *
     * **E' la sola risposta autorevole a "quale identificativo devo scrivere".**
     * I nomi cambiano ogni pochi mesi, le guide restano ferme, e un nome ritirato
     * si manifesta come un 404 che sembra un problema di chiave. Qui l'elenco lo
     * dice il fornitore, e lo dice alla chiave che ce l'ha davvero.
     */
    fun indirizzoModelli(modello: Modello): String = when (modello) {
        Modello.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models"
        Modello.GROK -> "https://api.x.ai/v1/models"
        Modello.GROQ -> "https://api.groq.com/openai/v1/models"
    }

    /**
     * Gli identificativi dei modelli in una risposta d'elenco.
     *
     * Due forme, perche' i fornitori ne usano due: `{"data":[{"id":…}]}` per
     * chiunque imiti OpenAI, `{"models":[{"name":"models/…"}]}` per Gemini — e li'
     * il prefisso `models/` si toglie, perche' quello che va scritto nelle
     * impostazioni e' il nome nudo.
     *
     * In ordine alfabetico: un elenco di trenta righe si legge solo se e' ordinato.
     */
    fun leggiModelli(corpo: String): List<String> {
        val radice = oggetto(corpo) ?: return emptyList()
        val elenco = (radice["data"] as? JsonArray)
            ?: (radice["models"] as? JsonArray)
            ?: return emptyList()
        return elenco
            .mapNotNull { voce ->
                val oggetto = voce as? JsonObject ?: return@mapNotNull null
                (stringa(oggetto, "id") ?: stringa(oggetto, "name"))
                    ?.removePrefix("models/")
                    ?.takeUnless { it.isBlank() }
            }
            .distinct()
            .sorted()
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
