package it.myacamperlife.app.dominio

import java.net.URLEncoder

/**
 * Gli indirizzi con cui si apre un punto in un'app di mappe.
 *
 * Funzioni pure che costruiscono stringhe: chi le usa ci mette intorno un
 * intent. Stanno qui e non nell'interfaccia perche' sono **regole**, e una
 * regola sbagliata (un nome non codificato, una virgola decimale al posto del
 * punto) manda l'utente da un'altra parte senza dire niente.
 *
 * Due indirizzi e non uno, perche' rispondono a due domande diverse:
 *
 * - `geo:` chiede **dove**, e lo chiede al telefono: risponde Organic Maps o
 *   OsmAnd, quindi **anche senza rete**, che in viaggio e' la condizione
 *   normale.
 * - Google chiede **cos'e'**: orari, foto, recensioni. Ha bisogno di rete, ed e'
 *   il motivo per cui uno vuole quel collegamento invece delle coordinate.
 */
object Mappe {

    /**
     * L'indirizzo `geo:` di un punto, con l'etichetta fra parentesi.
     *
     * Le coordinate compaiono due volte di proposito: la prima posiziona la
     * mappa, la seconda e' la ricerca (`q=`) che fa comparire il segnaposto.
     * Senza `q=` alcune app aprono la zona senza indicare niente.
     */
    fun geo(lat: Double, lon: Double, nome: String): String {
        val gradi = "${gradi(lat)},${gradi(lon)}"
        return "geo:$gradi?q=$gradi(${codifica(nome)})"
    }

    /**
     * L'indirizzo di Google Maps di un punto.
     *
     * **Cerca per nome quando il nome c'e'**, non per coordinate: cercare
     * `45.123456,11.654321` apre uno spillo in mezzo alla strada, mentre
     * "Chiesa di San Giacomo, Rothenburg" apre la scheda del posto — che e'
     * quello che si voleva. Il toponimo accanto al nome serve a disambiguare:
     * di chiese di San Giacomo ce n'e' una per provincia.
     *
     * Senza nome — e OpenStreetMap ne ha tanti senza — restano le coordinate,
     * che almeno indicano il punto giusto.
     *
     * Il toponimo va passato **nudo** ("Rothenburg"), non nella forma che si
     * mostra a schermo ("3 km da Rothenburg"): quella e' una frase, e in una
     * ricerca non cerca niente.
     */
    fun google(lat: Double, lon: Double, nome: String?, luogo: String? = null): String {
        val cercato = nome?.takeUnless { it.isBlank() }
            ?.let { listOfNotNull(it, luogo?.takeUnless { l -> l.isBlank() }).joinToString(", ") }
            ?: "${gradi(lat)},${gradi(lon)}"
        return "$RICERCA${codifica(cercato)}"
    }

    /** Sei decimali: un metro circa. Punto decimale e non virgola, sempre. */
    private fun gradi(valore: Double): String =
        String.format(java.util.Locale.ROOT, "%.6f", valore)

    /**
     * La codifica per un pezzo di URL. `URLEncoder` e' fatto per i moduli e
     * scrive `+` in luogo dello spazio: dentro un `geo:` quel piu' resta un
     * piu', e il segnaposto si chiamerebbe "Area+Lido".
     */
    private fun codifica(testo: String): String =
        URLEncoder.encode(testo, "UTF-8").replace("+", "%20")

    /** La forma documentata e stabile della ricerca di Google Maps. */
    private const val RICERCA = "https://www.google.com/maps/search/?api=1&query="
}
