package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il lettore dell'itinerario riceve un file che non e' sotto il nostro
 * controllo: questi test coprono le forme in cui puo' arrivare, non solo
 * quella giusta.
 */
class ItinerarioTest {

    @Test
    fun `legge un blocco recintato dentro il markdown`() {
        val documento = """
            # Toscana, agosto 2026

            Tre giorni fra Firenze e Orvieto.

            ```json
            {
              "waypoints": [
                {"name": "Firenze", "lat": 43.7696, "lng": 11.2558, "type": "citta", "giorno": "2026-08-05"},
                {"name": "Orvieto", "lat": 42.7185, "lng": 12.1112, "giorno": "2026-08-06", "description": "Duomo"}
              ]
            }
            ```
        """.trimIndent()

        val esito = Itinerario.leggi(documento) as Itinerario.Esito.Riuscito

        assertEquals("Toscana, agosto 2026", esito.nome)
        assertEquals(2, esito.tappe.size)
        assertEquals(0, esito.scartati)
        assertEquals("Firenze", esito.tappe[0].nome)
        assertEquals(43.7696, esito.tappe[0].lat, 1e-6)
        assertEquals(11.2558, esito.tappe[0].lon, 1e-6)
        assertEquals("citta", esito.tappe[0].tipo)
        assertEquals("2026-08-05", esito.tappe[0].giorno)
        assertEquals("Duomo", esito.tappe[1].descrizione)
    }

    @Test
    fun `legge un json senza recinto`() {
        val documento = """
            Itinerario
            {"waypoints":[{"name":"Bolsena","lat":42.6437,"lng":11.9871}]}
        """.trimIndent()

        val esito = Itinerario.leggi(documento) as Itinerario.Esito.Riuscito

        assertEquals(1, esito.tappe.size)
        assertEquals("Bolsena", esito.tappe.single().nome)
        assertNull("senza titolo # il nome non c'e'", esito.nome)
    }

    @Test
    fun `trova waypoints anche annidato`() {
        val documento =
            """{"viaggio":{"nome":"x","itinerario":{"waypoints":[{"name":"Siena","lat":43.32,"lng":11.33}]}}}"""

        val esito = Itinerario.leggi(documento) as Itinerario.Esito.Riuscito

        assertEquals("Siena", esito.tappe.single().nome)
    }

    @Test
    fun `accetta lon long e latitude come sinonimi`() {
        val esito = Itinerario.leggi(
            """{"waypoints":[
                {"name":"a","latitude":45.0,"longitude":9.0},
                {"name":"b","lat":44.0,"lon":8.0},
                {"name":"c","lat":43.0,"long":7.0}
            ]}""",
        ) as Itinerario.Esito.Riuscito

        assertEquals(3, esito.tappe.size)
        assertEquals(9.0, esito.tappe[0].lon, 1e-9)
        assertEquals(8.0, esito.tappe[1].lon, 1e-9)
        assertEquals(7.0, esito.tappe[2].lon, 1e-9)
    }

    @Test
    fun `accetta le coordinate scritte come stringa`() {
        val esito = Itinerario.leggi(
            """{"waypoints":[{"name":"a","lat":"43,5","lng":"11.25"}]}""",
        ) as Itinerario.Esito.Riuscito

        assertEquals(43.5, esito.tappe.single().lat, 1e-9)
        assertEquals(11.25, esito.tappe.single().lon, 1e-9)
    }

    @Test
    fun `una graffa dentro una descrizione non spezza il conteggio`() {
        val documento =
            """{"waypoints":[{"name":"a","lat":43.0,"lng":11.0,"description":"cerca il segno { qui"}]}"""

        val esito = Itinerario.leggi(documento) as Itinerario.Esito.Riuscito

        assertEquals("cerca il segno { qui", esito.tappe.single().descrizione)
    }

    @Test
    fun `un punto senza coordinate valide viene saltato non fa fallire tutto`() {
        val esito = Itinerario.leggi(
            """{"waypoints":[
                {"name":"buona","lat":43.0,"lng":11.0},
                {"name":"senza coordinate"},
                {"name":"fuori scala","lat":910.0,"lng":11.0}
            ]}""",
        ) as Itinerario.Esito.Riuscito

        assertEquals(1, esito.tappe.size)
        assertEquals(2, esito.scartati)
        assertEquals("buona", esito.tappe.single().nome)
    }

    @Test
    fun `un punto senza nome prende un segnaposto invece di sparire`() {
        val esito = Itinerario.leggi(
            """{"waypoints":[{"lat":43.0,"lng":11.0}]}""",
        ) as Itinerario.Esito.Riuscito

        assertEquals("Senza nome", esito.tappe.single().nome)
    }

    @Test
    fun `documento senza json`() {
        val esito = Itinerario.leggi("# Solo testo\n\nNessun itinerario qui.")

        assertEquals(Itinerario.Motivo.NESSUN_JSON, (esito as Itinerario.Esito.Fallito).motivo)
    }

    @Test
    fun `json senza waypoints`() {
        val esito = Itinerario.leggi("""{"tappe":[{"name":"a"}]}""")

        assertEquals(Itinerario.Motivo.NESSUN_WAYPOINTS, (esito as Itinerario.Esito.Fallito).motivo)
    }

    @Test
    fun `waypoints tutti senza coordinate`() {
        val esito = Itinerario.leggi("""{"waypoints":[{"name":"a"},{"name":"b"}]}""")

        assertEquals(Itinerario.Motivo.NESSUNA_TAPPA, (esito as Itinerario.Esito.Fallito).motivo)
    }

    @Test
    fun `una graffa spaiata non fa esplodere il lettore`() {
        val esito = Itinerario.leggi("""testo { rotto "waypoints": [ """)

        assertTrue(esito is Itinerario.Esito.Fallito)
    }

    @Test
    fun `il primo titolo vince anche se ci sono sottotitoli`() {
        val documento = """
            ## Prima un sottotitolo
            # Il vero titolo
            # Un altro titolo
            {"waypoints":[{"name":"a","lat":1.0,"lng":1.0}]}
        """.trimIndent()

        val esito = Itinerario.leggi(documento) as Itinerario.Esito.Riuscito

        assertEquals("Il vero titolo", esito.nome)
    }
}
