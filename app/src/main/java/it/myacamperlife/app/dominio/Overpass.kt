package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Il risultato di una chiamata a Overpass: i dintorni del viaggio.
 *
 * @param elementi quanti oggetti conteneva la risposta, prima di scartare
 *   quelli inutilizzabili. Serve a distinguere **due fallimenti diversi**: una
 *   zona dove non c'e' niente risponde con zero elementi, una risposta che non
 *   sappiamo leggere ne porta trecento e ne tiene zero. Il primo caso e' una
 *   risposta, il secondo e' un difetto, e per mesi si sono somigliati.
 */
data class Dintorno(
    val poi: List<Poi>,
    val luoghi: List<Luogo>,
    val elementi: Int = 0,
) {
    val vuoto: Boolean get() = poi.isEmpty() && luoghi.isEmpty()

    /** Ha risposto, ma di quello che ha risposto non abbiamo salvato niente. */
    val illeggibile: Boolean get() = vuoto && elementi > 0
}

/**
 * Costruisce e legge la richiesta dei dintorni.
 *
 * **Un punto per volta, quando lo si chiede.** Prima l'app provava a fare
 * scorta di tutto l'itinerario in una richiesta: un `around` di quindici
 * chilometri lungo una polilinea di venti tappe, cioe' un corridoio di
 * migliaia di chilometri quadrati, su un server pubblico che e' una cortesia.
 * Quella richiesta non tornava con un errore — tornava con un `remark` e zero
 * elementi, e dal lato dell'app somigliava a "in quella zona non c'e' niente".
 * Un cerchio di dieci chilometri intorno a **una** tappa e' una query che
 * Overpass serve in un secondo, e quello che torna si salva subito: la scorta
 * si riempie una tappa per volta, con le ricerche che uno fa davvero.
 *
 * **Una richiesta per tutte le categorie**: le sette categorie di punti di
 * interesse e i toponimi arrivano insieme, e si separano leggendo i tag. Le
 * righe sono raggruppate per chiave OSM, non per categoria, perche' ogni
 * statement rivaluta il filtro geografico da zero.
 *
 * Funzioni pure: costruire il testo della query e leggere la risposta.
 */
object Overpass {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Il corpo della richiesta, in Overpass QL.
     *
     * **`out center;` e nient'altro.** Tre parole che vanno spiegate, perche'
     * sbagliarle rende la richiesta muta senza che nessuno protesti:
     *
     * - non `out geom`: un campeggio in OSM e' un poligono, e a noi serve il suo
     *   centro, non il suo perimetro. La geometria la disegna l'app di mappe
     * - non `out center tags`, che e' quello che c'era prima ed era **il motivo
     *   per cui i dintorni non arrivavano mai**. In Overpass `tags` non e' un
     *   aggiunta ma un livello di verbosita', e significa "identificativi e tag,
     *   **senza geometria**": i nodi tornavano senza `lat`/`lon`, `center` non
     *   glieli restituiva, e [leggi] li scartava tutti. Zero punti di interesse
     *   e zero toponimi, con la sola faccia di un "non aggiornato"
     * - `center` da solo basta: la verbosita' di riposo e' `body`, che porta le
     *   coordinate dei nodi **e** i tag di tutto, e `center` aggiunge il centro a
     *   vie e relazioni. E' esattamente quello che [leggi] va a cercare
     */
    fun query(punto: Coordinate, raggioMetri: Int = RAGGIO_METRI, timeout: Int = TIMEOUT): String =
        query(listOf(punto), raggioMetri, timeout)

    fun query(punti: List<Coordinate>, raggioMetri: Int = RAGGIO_METRI, timeout: Int = TIMEOUT): String {
        require(punti.isNotEmpty()) { "senza punti non c'e' un intorno" }
        val intorno = "around:$raggioMetri," + punti.joinToString(",") {
            "${gradi(it.lat)},${gradi(it.lon)}"
        }

        // **Una riga per chiave OSM, non una per categoria.** Prima erano dieci
        // statement, ognuno dei quali rivalutava da zero il filtro `around` su
        // una polilinea di venti vertici: e' il lavoro piu' caro di tutta la
        // query, e farlo dieci volte invece di quattro e' il genere di richiesta
        // che il server pubblico chiude per timeout — restituendo un `remark` e
        // zero elementi, che per mesi abbiamo letto come "qui non c'e' niente".
        val perChiave = CategoriaPoi.entries
            .flatMap { it.filtri }
            .mapNotNull { chiaveEValore(it) }
            .groupBy({ it.first }, { it.second })

        val righe = buildList {
            perChiave.forEach { (chiave, valori) ->
                val filtro = if (valori.size == 1) {
                    "[\"$chiave\"=\"${valori.single()}\"]"
                } else {
                    "[\"$chiave\"~\"^(${valori.joinToString("|")})$\"]"
                }
                add("  nwr($intorno)$filtro;")
            }
            // I toponimi: solo nodi, e solo i posti abitati. `place=locality`
            // e i suoi parenti descrivono localita' senza case, e nel diario
            // darebbero nomi che nessuno riconosce.
            add("  node($intorno)[\"place\"~\"^(city|town|village|hamlet)$\"];")
        }

        return buildString {
            appendLine("[out:json][timeout:$timeout];")
            appendLine("(")
            righe.forEach { appendLine(it) }
            appendLine(");")
            appendLine("out center;")
        }
    }

    /**
     * La query impacchettata come la vuole Overpass in una POST.
     *
     * `data=<query codificata>` con `Content-Type: application/x-www-form-urlencoded`
     * e' la forma **documentata** dell'API, quella che usano overpass-turbo e le
     * librerie. Prima l'app mandava la query come corpo grezzo `text/plain`: e'
     * una forma che alcune installazioni accettano e altre no, e quando non
     * l'accettano non lo dicono con un errore — rispondono a una query vuota, che
     * dal lato dell'app somiglia a "qui non c'e' niente". Fra due forme di cui
     * una sola e' documentata non c'e' motivo di preferire l'altra.
     */
    fun corpoModulo(query: String): String =
        "data=" + java.net.URLEncoder.encode(query, "UTF-8")

    /**
     * Da `["tourism"="camp_site"]` a `tourism` e `camp_site`.
     *
     * I filtri sono scritti a mano dentro [CategoriaPoi] e hanno tutti questa
     * forma; se un giorno ne comparisse uno diverso, questa funzione restituisce
     * `null` e quel filtro esce dal raggruppamento invece di produrre una query
     * sbagliata in silenzio.
     */
    private fun chiaveEValore(filtro: String): Pair<String, String>? {
        val trovato = CHIAVE_VALORE.matchEntire(filtro.trim()) ?: return null
        return trovato.groupValues[1] to trovato.groupValues[2]
    }

    private val CHIAVE_VALORE = "\\[\"([a-z_:]+)\"=\"([A-Za-z0-9_:-]+)\"]".toRegex()

    /**
     * Il messaggio con cui Overpass segnala un guasto **dentro una risposta
     * riuscita**.
     *
     * E' la cosa che mancava, ed e' costata mesi di dintorni vuoti: quando una
     * query esaurisce il tempo o la memoria, il server non risponde con un
     * codice d'errore — risponde **200** con `elements` vuoto e un campo
     * `remark` che dice cosa e' andato storto. Senza leggerlo, "la query era
     * troppo cara" e "in quella zona non c'e' niente" sono indistinguibili, e la
     * seconda e' la spiegazione che si finisce per credere.
     */
    fun avvertimento(corpo: String): String? {
        val radice = runCatching { json.parseToJsonElement(corpo) as? JsonObject }.getOrNull()
            ?: return null
        return runCatching { radice["remark"]?.jsonPrimitive?.contentOrNull }
            .getOrNull()
            ?.trim()
            ?.takeUnless { it.isEmpty() }
    }

    /**
     * Separa punti di interesse e toponimi dalla risposta.
     *
     * Un elemento senza coordinate — una relazione senza `center` — si scarta:
     * un risultato senza posizione non si puo' ne' ordinare per distanza ne'
     * aprire in una mappa, quindi non e' un risultato.
     */
    fun leggi(corpo: String): Dintorno {
        val radice = runCatching { json.parseToJsonElement(corpo) as? JsonObject }.getOrNull()
            ?: return Dintorno(emptyList(), emptyList())
        val elementi = (radice["elements"] as? JsonArray).orEmpty()
        // Il tetto e' la dimensione della risposta, non il numero di risultati:
        // e' quello che permette di dire "ha risposto ma non ho saputo leggerlo"
        // invece di "non ho trovato niente".
        val quanti = elementi.size

        val poi = mutableListOf<Poi>()
        val luoghi = mutableListOf<Luogo>()

        elementi.forEach { elemento ->
            val oggetto = elemento as? JsonObject ?: return@forEach
            val tag = tag(oggetto)
            val (lat, lon) = posizione(oggetto) ?: return@forEach
            val nome = tag["name"]?.takeUnless { it.isBlank() }

            val posto = tag["place"]
            if (posto != null && nome != null) {
                luoghi.add(Luogo(nome, lat, lon, abitanti = abitanti(tag)))
                return@forEach
            }

            val categoria = CategoriaPoi.daTag(tag) ?: return@forEach
            poi.add(
                Poi(
                    id = identificativo(oggetto),
                    nome = nome,
                    categoria = categoria,
                    lat = lat,
                    lon = lon,
                    dettaglio = dettaglio(tag, categoria),
                ),
            )
        }

        return Dintorno(
            // Overpass puo' restituire lo stesso posto due volte, come nodo e
            // come poligono: l'identificativo li tiene distinti, la posizione
            // arrotondata li riconosce come uno.
            poi = poi.distinctBy { "${arrotonda(it.lat)},${arrotonda(it.lon)},${it.categoria}" },
            luoghi = luoghi.distinctBy { "${it.nome},${arrotonda(it.lat)}" },
            elementi = quanti,
        )
    }

    // --- lettura dei campi ----------------------------------------------------

    private fun tag(oggetto: JsonObject): Map<String, String> {
        val tag = oggetto["tags"] as? JsonObject ?: return emptyMap()
        return tag.mapNotNull { (chiave, valore) ->
            val testo = runCatching { valore.jsonPrimitive.contentOrNull }.getOrNull()
            if (testo == null) null else chiave to testo
        }.toMap()
    }

    /** Un nodo ha `lat`/`lon`; una via o una relazione hanno `center`. */
    private fun posizione(oggetto: JsonObject): Pair<Double, Double>? {
        numero(oggetto, "lat")?.let { lat ->
            numero(oggetto, "lon")?.let { lon -> return lat to lon }
        }
        val centro = oggetto["center"] as? JsonObject ?: return null
        val lat = numero(centro, "lat") ?: return null
        val lon = numero(centro, "lon") ?: return null
        return lat to lon
    }

    private fun identificativo(oggetto: JsonObject): String {
        val tipo = runCatching { oggetto["type"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "osm"
        val id = runCatching { oggetto["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "?"
        return "$tipo/$id"
    }

    private fun abitanti(tag: Map<String, String>): Int? =
        tag["population"]?.filter { it.isDigit() }?.takeUnless { it.isEmpty() }?.toIntOrNull()

    /**
     * La riga di dettaglio: quello che cambia la decisione, non tutto quello
     * che OSM sa. Per un distributore il gestore; per un'area di sosta se e' a
     * pagamento; per un supermercato la catena.
     */
    private fun dettaglio(tag: Map<String, String>, categoria: CategoriaPoi): String? {
        val pezzi = buildList {
            tag["operator"]?.let { add(it) }
            tag["brand"]?.takeIf { it != tag["operator"] }?.let { add(it) }
            when (tag["fee"]) {
                "yes" -> add("a pagamento")
                "no" -> add("gratuito")
            }
            if (categoria == CategoriaPoi.ACQUA && tag["drinking_water"] == "no") {
                add("non potabile")
            }
            tag["opening_hours"]?.takeIf { it == "24/7" }?.let { add("sempre aperto") }
        }
        return pezzi.distinct().joinToString(" · ").takeUnless { it.isEmpty() }
    }

    private fun numero(oggetto: JsonObject, nome: String): Double? =
        runCatching { oggetto[nome]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    private fun gradi(valore: Double): String =
        String.format(java.util.Locale.ROOT, "%.5f", valore)

    private fun arrotonda(valore: Double): String =
        String.format(java.util.Locale.ROOT, "%.4f", valore)

    /**
     * I server di Overpass, in ordine di tentativo.
     *
     * **Non e' ridondanza da manuale, e' quello che e' successo.** Il primo
     * tentativo vero da un telefono ha ricevuto un 504 con dentro
     * `Dispatcher_Client::request_read_and_idx::timeout`: non un rifiuto della
     * query — quello suona diverso, «Query timed out» o «run out of memory» — ma
     * il processo che distribuisce i turni di lettura che non aveva uno slot
     * libero. Congestione, o database in aggiornamento. Nessuna correzione alla
     * richiesta puo' rimediare a un server che in quel momento non risponde.
     *
     * Sono tutti server di cortesia, e si interrogano **in fila e uno per volta**:
     * il secondo si prova solo se il primo non ha risposto, e alla prima risposta
     * si smette. Una ricerca che riesce costa una richiesta, come prima.
     *
     * L'ordine mette per primo quello ufficiale — e' quello con i dati piu'
     * freschi — e dopo due specchi pubblici che la comunita' OSM mantiene per
     * questo. Se un giorno uno di questi chiude, gli altri restano: e' il senso
     * di averne tre invece di uno.
     */
    val SERVIZI = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.osm.ch/api/interpreter",
    )

    /** Come si chiama il server, per scriverlo nell'esito senza mezzo indirizzo. */
    fun nomeServizio(indirizzo: String): String =
        indirizzo.removePrefix("https://").substringBefore("/api")

    /**
     * Il messaggio che conta, dentro il corpo d'errore di Overpass.
     *
     * Un errore arriva avvolto in due righe di licenza ODbL sempre uguali, e in
     * duecento caratteri quelle si mangiano la parte utile: si taglia da `Error`
     * in poi, che e' dove il server dice cosa e' andato storto. Se quella parola
     * non c'e', si tiene tutto — un messaggio inatteso e' proprio quello che non
     * va buttato.
     */
    fun causa(corpo: String): String {
        val pulito = corpo
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val da = pulito.indexOf("Error")
        return if (da >= 0) pulito.substring(da) else pulito
    }

    /**
     * Dieci chilometri dal punto: quello che si raggiunge in un quarto d'ora
     * dalla tappa.
     *
     * Erano quindici, quando la richiesta era una sola per tutto l'itinerario e
     * il raggio doveva coprire un corridoio. Su un punto solo dieci bastano, e
     * l'area di una query cresce col quadrato del raggio: da 15 a 10 chilometri
     * il lavoro del server si piu' che dimezza.
     */
    const val RAGGIO_METRI = 10_000

    /**
     * Trenta secondi lato server.
     *
     * Erano novanta per la query larga. Una ricerca su un punto solo che ci
     * mette piu' di trenta secondi non e' lenta: e' rotta, e vale saperlo
     * subito invece di aspettare un minuto e mezzo per scoprirlo.
     */
    const val TIMEOUT = 30
}
