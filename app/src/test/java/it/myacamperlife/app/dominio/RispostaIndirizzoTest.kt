package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RispostaIndirizzoTest {

    private val risposta = """
        [
          {
            "place_id": 1, "lat": "42.7185", "lon": "12.1112",
            "name": "Orvieto",
            "display_name": "Orvieto, Terni, Umbria, 05018, Italia",
            "type": "town"
          },
          {
            "place_id": 2, "lat": "42.6437", "lon": "11.9871",
            "name": "Camping Lido",
            "display_name": "Camping Lido, Viale Cadorna, Bolsena, Viterbo, Lazio, Italia",
            "type": "camp_site"
          }
        ]
    """.trimIndent()

    // --- la risposta ----------------------------------------------------------

    @Test
    fun `le coordinate arrivano come stringhe e si leggono comunque`() {
        val primo = RispostaIndirizzo.leggi(risposta).first()
        assertEquals(42.7185, primo.lat, 1e-6)
        assertEquals(12.1112, primo.lon, 1e-6)
    }

    @Test
    fun `il nome corto sta sopra, il resto sotto`() {
        val primo = RispostaIndirizzo.leggi(risposta).first()
        assertEquals("Orvieto", primo.nome)
        assertEquals("Terni, Umbria, 05018, Italia", primo.descrizione)
    }

    @Test
    fun `si legge anche un posto che non e' un paese`() {
        val secondo = RispostaIndirizzo.leggi(risposta)[1]
        assertEquals("Camping Lido", secondo.nome)
        assertTrue(secondo.descrizione!!.contains("Bolsena"))
    }

    @Test
    fun `senza il campo name si prende la prima parte del nome completo`() {
        val corpo = """
            [{"lat": "45.4642", "lon": "9.1900",
              "display_name": "Milano, Lombardia, Italia"}]
        """.trimIndent()
        val trovato = RispostaIndirizzo.leggi(corpo).single()
        assertEquals("Milano", trovato.nome)
        assertEquals("Lombardia, Italia", trovato.descrizione)
    }

    @Test
    fun `un risultato senza coordinate si scarta`() {
        val corpo = """[{"name": "Nessun posto", "display_name": "Nessun posto"}]"""
        assertTrue(RispostaIndirizzo.leggi(corpo).isEmpty())
    }

    @Test
    fun `coordinate fuori dal mondo si scartano`() {
        val corpo = """[{"name": "Assurdo", "lat": "999", "lon": "0"}]"""
        assertTrue(RispostaIndirizzo.leggi(corpo).isEmpty())
    }

    @Test
    fun `una risposta rotta non fa cadere niente`() {
        assertTrue(RispostaIndirizzo.leggi("").isEmpty())
        assertTrue(RispostaIndirizzo.leggi("[]").isEmpty())
        assertTrue(RispostaIndirizzo.leggi("<html>429 Too Many Requests</html>").isEmpty())
        assertTrue(RispostaIndirizzo.leggi("""{"error": "boh"}""").isEmpty())
    }

    @Test
    fun `senza descrizione il campo resta nullo, non una stringa vuota`() {
        val corpo = """[{"name": "Posto", "lat": "42.0", "lon": "12.0", "display_name": "Posto"}]"""
        assertNull(RispostaIndirizzo.leggi(corpo).single().descrizione)
    }

    // --- la richiesta ---------------------------------------------------------

    @Test
    fun `l'indirizzo codifica quello che l'utente ha scritto`() {
        val indirizzo = RispostaIndirizzo.indirizzo("via Cassia 214, Orvieto")
        assertTrue(indirizzo, indirizzo.contains("q=via+Cassia+214%2C+Orvieto"))
        assertTrue(indirizzo, indirizzo.contains("format=jsonv2"))
    }

    @Test
    fun `non si chiedono piu risultati di quelli che si mostrano`() {
        assertTrue(RispostaIndirizzo.indirizzo("Orvieto", quanti = 99).contains("limit=20"))
        assertTrue(RispostaIndirizzo.indirizzo("Orvieto", quanti = 0).contains("limit=1"))
    }
}
