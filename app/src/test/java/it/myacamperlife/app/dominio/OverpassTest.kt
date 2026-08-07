package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassTest {

    private val punti = listOf(Coordinate(42.7185, 12.1112), Coordinate(42.4207, 12.1077))

    // --- la richiesta ---------------------------------------------------------

    @Test
    fun `la query e un corridoio intorno alla polilinea, non un rettangolo`() {
        val query = Overpass.query(punti)
        // Un solo `around` con tutte le coordinate in fila: Overpass misura
        // dalla linea, quindi copre la strada fra le tappe.
        assertTrue(query, query.contains("around:15000,42.71850,12.11120,42.42070,12.10770"))
    }

    @Test
    fun `la query chiede tutte le categorie e i toponimi insieme`() {
        val query = Overpass.query(punti)
        assertTrue(query, query.contains("\"tourism\"=\"caravan_site\""))
        assertTrue(query, query.contains("\"amenity\"=\"sanitary_dump_station\""))
        assertTrue(query, query.contains("\"shop\"=\"supermarket\""))
        assertTrue(query, query.contains("\"place\"~\"^(city|town|village|hamlet)$\""))
        // Il centro e non la geometria: di un poligono ci serve il punto.
        assertTrue(query, query.contains("out center tags;"))
    }

    @Test
    fun `la query usa il punto decimale, che e' quello che vuole il servizio`() {
        assertTrue(!Overpass.query(punti).contains("42,71850"))
    }

    // --- la risposta ----------------------------------------------------------

    private val risposta = """
        {
          "version": 0.6,
          "elements": [
            {
              "type": "node", "id": 111, "lat": 42.7200, "lon": 12.1130,
              "tags": {"amenity": "fuel", "name": "Eni Station", "operator": "Eni", "opening_hours": "24/7"}
            },
            {
              "type": "way", "id": 222,
              "center": {"lat": 42.6450, "lon": 11.9880},
              "tags": {"tourism": "caravan_site", "name": "Area Il Cipresso", "fee": "yes"}
            },
            {
              "type": "node", "id": 333, "lat": 42.7185, "lon": 12.1112,
              "tags": {"place": "town", "name": "Orvieto", "population": "20394"}
            },
            {
              "type": "node", "id": 444, "lat": 42.7350, "lon": 12.0900,
              "tags": {"place": "hamlet", "name": "Sugano"}
            },
            {
              "type": "node", "id": 555, "lat": 42.7000, "lon": 12.1000,
              "tags": {"amenity": "bench"}
            },
            {
              "type": "relation", "id": 666,
              "tags": {"tourism": "camp_site", "name": "Senza posizione"}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `punti di interesse e toponimi si separano leggendo i tag`() {
        val dintorno = Overpass.leggi(risposta)
        assertEquals(listOf("Eni Station", "Area Il Cipresso"), dintorno.poi.map { it.nome })
        assertEquals(listOf("Orvieto", "Sugano"), dintorno.luoghi.map { it.nome })
    }

    @Test
    fun `un poligono porta il suo centro`() {
        val area = Overpass.leggi(risposta).poi.first { it.categoria == CategoriaPoi.SOSTA }
        assertEquals(42.6450, area.lat, 0.0001)
        assertEquals(11.9880, area.lon, 0.0001)
    }

    @Test
    fun `un elemento senza posizione si scarta`() {
        // Una relazione senza `center` non si puo' ne' ordinare per distanza
        // ne' aprire in una mappa: non e' un risultato.
        assertTrue(Overpass.leggi(risposta).poi.none { it.nome == "Senza posizione" })
    }

    @Test
    fun `un tag che non ci interessa non entra`() {
        assertTrue(Overpass.leggi(risposta).poi.none { it.categoria == CategoriaPoi.ATTRAZIONE })
        assertEquals(2, Overpass.leggi(risposta).poi.size)
    }

    @Test
    fun `il dettaglio dice quello che cambia la decisione`() {
        val poi = Overpass.leggi(risposta).poi
        assertEquals("Eni · sempre aperto", poi.first { it.nome == "Eni Station" }.dettaglio)
        assertEquals("a pagamento", poi.first { it.nome == "Area Il Cipresso" }.dettaglio)
    }

    @Test
    fun `la popolazione arriva quando c'e'`() {
        val luoghi = Overpass.leggi(risposta).luoghi
        assertEquals(20394, luoghi.first { it.nome == "Orvieto" }.abitanti)
        assertNull(luoghi.first { it.nome == "Sugano" }.abitanti)
    }

    @Test
    fun `lo stesso posto come nodo e come poligono conta una volta`() {
        val doppio = """
            {"elements": [
              {"type": "node", "id": 1, "lat": 42.6450, "lon": 11.9880,
               "tags": {"tourism": "caravan_site", "name": "Area"}},
              {"type": "way", "id": 2, "center": {"lat": 42.64501, "lon": 11.98801},
               "tags": {"tourism": "caravan_site", "name": "Area"}}
            ]}
        """.trimIndent()
        assertEquals(1, Overpass.leggi(doppio).poi.size)
    }

    @Test
    fun `una risposta rotta non fa cadere niente`() {
        assertTrue(Overpass.leggi("").poi.isEmpty())
        assertTrue(Overpass.leggi("<html>504 Gateway Timeout</html>").luoghi.isEmpty())
        assertTrue(Overpass.leggi("""{"elements": []}""").poi.isEmpty())
        assertTrue(Overpass.leggi("""{"remark": "runtime error: Query timed out"}""").poi.isEmpty())
    }

    @Test
    fun `un posto senza nome resta se ha una categoria`() {
        val senzaNome = """
            {"elements": [
              {"type": "node", "id": 1, "lat": 42.6, "lon": 11.9,
               "tags": {"amenity": "drinking_water"}}
            ]}
        """.trimIndent()
        val poi = Overpass.leggi(senzaNome).poi.single()
        assertNull(poi.nome)
        assertEquals("Fontana", poi.etichetta())
    }

    @Test
    fun `un toponimo senza nome non serve a niente e si scarta`() {
        val senzaNome = """
            {"elements": [{"type": "node", "id": 1, "lat": 42.6, "lon": 11.9, "tags": {"place": "village"}}]}
        """.trimIndent()
        assertTrue(Overpass.leggi(senzaNome).luoghi.isEmpty())
    }
}
