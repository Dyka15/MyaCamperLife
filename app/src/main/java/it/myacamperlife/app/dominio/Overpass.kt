package it.myacamperlife.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Il risultato di una chiamata a Overpass: i dintorni del viaggio. */
data class Dintorno(val poi: List<Poi>, val luoghi: List<Luogo>)

/**
 * Costruisce e legge la richiesta dei dintorni.
 *
 * **Un `around` lungo l'itinerario, non un rettangolo.** Un rettangolo che
 * contiene Milano e Palermo contiene mezza Italia; un corridoio di quindici
 * chilometri intorno alla linea che unisce le tappe contiene la strada che
 * farai, e cresce col numero di tappe e non con la loro distanza. Overpass
 * accetta una polilinea in `around`, e misura da quella.
 *
 * **Una richiesta sola per tutto**: le sette categorie di punti di interesse e
 * i toponimi arrivano insieme, e si separano leggendo i tag. Su Overpass, che
 * e' un servizio di cortesia, chiedere una volta invece di otto non e' un
 * dettaglio di efficienza, e' buona educazione.
 *
 * Funzioni pure: costruire il testo della query e leggere la risposta.
 */
object Overpass {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Il corpo della richiesta, in Overpass QL.
     *
     * `out center tags` e non `out geom`: un campeggio in OSM e' un poligono, e
     * a noi serve il suo centro, non il suo perimetro. La geometria la disegna
     * l'app di mappe.
     */
    fun query(punti: List<Coordinate>, raggioMetri: Int = RAGGIO_METRI, timeout: Int = TIMEOUT): String {
        require(punti.isNotEmpty()) { "senza punti non c'e' un intorno" }
        val intorno = "around:$raggioMetri," + punti.joinToString(",") {
            "${gradi(it.lat)},${gradi(it.lon)}"
        }

        val righe = buildList {
            CategoriaPoi.entries.forEach { categoria ->
                categoria.filtri.forEach { filtro -> add("  nwr($intorno)$filtro;") }
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
            appendLine("out center tags;")
        }
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
     * Il server pubblico di Overpass.
     *
     * Come OSRM: un servizio di cortesia, non un'infrastruttura. Lo si
     * interroga **una volta per viaggio**, quando si importa l'itinerario, e su
     * richiesta esplicita. Mai da una schermata che si apre.
     */
    const val SERVIZIO = "https://overpass-api.de/api/interpreter"

    /** Quindici chilometri dal percorso: il corridoio in cui passerai. */
    const val RAGGIO_METRI = 15_000

    /** Novanta secondi lato server: una query larga ci mette. */
    const val TIMEOUT = 90

    /**
     * Oltre questo numero di punti la polilinea si accorcia: una query con
     * cento vertici mette in ginocchio il server pubblico, e le tappe che
     * contano sono quelle che devi ancora fare.
     */
    const val PUNTI_MASSIMI = 20
}
