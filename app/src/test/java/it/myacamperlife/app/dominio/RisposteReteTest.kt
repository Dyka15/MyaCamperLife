package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le risposte dei due servizi, lette da testo salvato.
 *
 * E' l'unico modo di accorgersi che un'API e' cambiata **prima** di essere in
 * un'area di sosta senza campo con un briefing muto.
 */
class RisposteReteTest {

    // --- Open-Meteo -----------------------------------------------------------

    private val unLuogo = """
        {
          "latitude": 42.75, "longitude": 12.125, "timezone": "Europe/Rome",
          "daily": {
            "time": ["2026-08-06", "2026-08-07"],
            "weather_code": [0, 61],
            "temperature_2m_max": [31.4, 26.8],
            "temperature_2m_min": [18.2, 17.1],
            "precipitation_sum": [0.0, 8.4],
            "precipitation_probability_max": [3, 75],
            "wind_speed_10m_max": [11.2, 34.9]
          }
        }
    """.trimIndent()

    @Test
    fun `una risposta con un luogo solo e un oggetto, non un array`() {
        val luoghi = RispostaMeteo.leggi(unLuogo)
        val luogo = luoghi.single()
        assertEquals(2, luogo.previsioni.size)
        assertEquals(42.75, luogo.lat, 0.001)
    }

    @Test
    fun `il blocco daily e colonnare e si traspone per giorno`() {
        val domani = RispostaMeteo.leggi(unLuogo).single().previsioni[1]
        assertEquals("2026-08-07", domani.giorno)
        assertEquals(61, domani.codice)
        assertEquals(26.8, domani.massima!!, 0.001)
        assertEquals(17.1, domani.minima!!, 0.001)
        assertEquals(8.4, domani.pioggiaMm!!, 0.001)
        assertEquals(75, domani.probabilitaPioggia)
        assertEquals(34.9, domani.ventoKmh!!, 0.001)
    }

    @Test
    fun `con piu coordinate la risposta e un array, e i nomi arrivano da noi`() {
        val corpo = "[$unLuogo, $unLuogo]"
        val richiesti = listOf(
            PuntoMeteo("Orvieto", 42.7185, 12.1112),
            PuntoMeteo("Viterbo", 42.4207, 12.1077),
        )
        val luoghi = RispostaMeteo.leggi(corpo, richiesti)

        assertEquals(2, luoghi.size)
        assertEquals("Viterbo", luoghi[1].nome)
        // Le coordinate salvate sono quelle chieste, non quelle della griglia:
        // sono quelle con cui si cerchera'.
        assertEquals(42.4207, luoghi[1].lat, 0.0001)
    }

    @Test
    fun `i valori nulli dei giorni lontani non fanno cadere niente`() {
        val corpo = """
            {"latitude": 42.0, "longitude": 12.0, "daily": {
              "time": ["2026-08-20"],
              "weather_code": [null],
              "temperature_2m_max": [null],
              "temperature_2m_min": [null],
              "precipitation_probability_max": [null]
            }}
        """.trimIndent()
        val previsione = RispostaMeteo.leggi(corpo).single().previsioni.single()
        assertEquals("2026-08-20", previsione.giorno)
        assertEquals(CieloMeteo.IGNOTO, previsione.cielo)
    }

    @Test
    fun `una risposta rotta da una lista vuota, non un'eccezione`() {
        assertTrue(RispostaMeteo.leggi("").isEmpty())
        assertTrue(RispostaMeteo.leggi("<html>errore 502</html>").isEmpty())
        assertTrue(RispostaMeteo.leggi("""{"error": true, "reason": "boh"}""").isEmpty())
        assertTrue(RispostaMeteo.leggi("""{"daily": {"time": []}}""").isEmpty())
    }

    @Test
    fun `l'indirizzo mette tutte le coordinate in una richiesta sola`() {
        val indirizzo = RispostaMeteo.indirizzo(
            listOf(
                PuntoMeteo("Orvieto", 42.7185, 12.1112),
                PuntoMeteo("Viterbo", 42.4207, 12.1077),
            ),
            giorni = 5,
        )
        assertTrue(indirizzo, indirizzo.contains("latitude=42.7185,42.4207"))
        assertTrue(indirizzo, indirizzo.contains("longitude=12.1112,12.1077"))
        assertTrue(indirizzo, indirizzo.contains("forecast_days=5"))
        // Punto decimale: e' un indirizzo web, non un file dell'archivio.
        assertTrue(indirizzo, !indirizzo.contains("42,7185"))
    }

    @Test
    fun `l'indirizzo non chiede piu giorni di quelli che esistono`() {
        val indirizzo = RispostaMeteo.indirizzo(listOf(PuntoMeteo(null, 42.0, 12.0)), giorni = 99)
        assertTrue(indirizzo.contains("forecast_days=16"))
    }

    // --- OSRM -----------------------------------------------------------------

    private val punti = listOf(
        PuntoTratta("Orvieto", 42.7185, 12.1112),
        PuntoTratta("Bolsena", 42.6437, 11.9871),
        PuntoTratta("Viterbo", 42.4207, 12.1077),
    )

    private val duePezzi = """
        {
          "code": "Ok",
          "routes": [{
            "distance": 78421.3, "duration": 4980.2,
            "legs": [
              {"distance": 31200.0, "duration": 1980.0, "summary": ""},
              {"distance": 47221.3, "duration": 3000.2, "summary": ""}
            ]
          }],
          "waypoints": []
        }
    """.trimIndent()

    @Test
    fun `una chiamata sola da tutte le tratte consecutive`() {
        val tratte = RispostaOsrm.leggi(duePezzi, punti)
        assertEquals(2, tratte.size)

        val primo = tratte.first()
        assertEquals("Orvieto", primo.da)
        assertEquals("Bolsena", primo.a)
        // Metri e secondi diventano chilometri e minuti.
        assertEquals(31.2, primo.km, 0.001)
        assertEquals(33, primo.minuti)

        assertEquals("Viterbo", tratte[1].a)
        assertEquals(50, tratte[1].minuti)
    }

    @Test
    fun `una risposta che non dice Ok non da tratte`() {
        val corpo = """{"code": "NoRoute", "message": "Impossible route"}"""
        assertTrue(RispostaOsrm.leggi(corpo, punti).isEmpty())
    }

    @Test
    fun `una risposta rotta non fa cadere niente`() {
        assertTrue(RispostaOsrm.leggi("", punti).isEmpty())
        assertTrue(RispostaOsrm.leggi("non json", punti).isEmpty())
        assertTrue(RispostaOsrm.leggi("""{"code":"Ok","routes":[]}""", punti).isEmpty())
    }

    @Test
    fun `con un punto solo non c'e' niente da chiedere`() {
        assertTrue(RispostaOsrm.leggi(duePezzi, punti.take(1)).isEmpty())
    }

    @Test
    fun `l'indirizzo di OSRM vuole la longitudine prima della latitudine`() {
        val indirizzo = RispostaOsrm.indirizzo(punti.take(2))
        assertTrue(indirizzo, indirizzo.contains("12.111200,42.718500;11.987100,42.643700"))
        assertTrue(indirizzo, indirizzo.contains("overview=false"))
    }
}
