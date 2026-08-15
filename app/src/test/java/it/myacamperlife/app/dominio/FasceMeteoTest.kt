package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tre fasce di una giornata, ricavate dalle ore di Open-Meteo.
 *
 * **Ogni regola di aggregazione ha una prova**, perche' ognuna puo' sbagliare in
 * un modo che nessuno nota: una media al posto di un massimo trasforma un
 * temporale di un'ora in un pomeriggio nuvoloso, e chi legge parte in bici.
 */
class FasceMeteoTest {

    /**
     * Una risposta con `daily` di un giorno e `hourly` costruito a mano: mattino
     * sereno e fresco, pomeriggio con un'ora di temporale, sera nuvolosa. Le ore
     * di notte ci sono di proposito — devono essere scartate.
     */
    private val risposta = """
        {
          "latitude": 49.4, "longitude": 11.1,
          "daily": {
            "time": ["2026-08-16"],
            "weather_code": [95],
            "temperature_2m_max": [28.0],
            "temperature_2m_min": [14.0],
            "precipitation_sum": [6.0],
            "precipitation_probability_max": [70],
            "wind_speed_10m_max": [34.0]
          },
          "hourly": {
            "time": [
              "2026-08-16T03:00", "2026-08-16T04:00",
              "2026-08-16T07:00", "2026-08-16T09:00", "2026-08-16T11:00",
              "2026-08-16T13:00", "2026-08-16T15:00", "2026-08-16T17:00",
              "2026-08-16T19:00", "2026-08-16T22:00"
            ],
            "weather_code":              [61, 61, 0,   0,   0,   3,   95,  80,  3,   3],
            "temperature_2m":            [12.0, 11.0, 15.0, 19.0, 23.0, 26.0, 28.0, 25.0, 21.0, 18.0],
            "precipitation":             [1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 5.0, 1.0, 0.0, 0.0],
            "precipitation_probability": [80, 80, 0,   5,   10,  20,  70,  40,  10,  5],
            "wind_speed_10m":            [20.0, 20.0, 6.0, 9.0, 12.0, 18.0, 34.0, 22.0, 14.0, 10.0]
          }
        }
    """.trimIndent()

    private val previsione: Previsione =
        RispostaMeteo.leggi(risposta).single().previsioni.single()

    @Test
    fun `una giornata si divide in tre fasce, nell'ordine della giornata`() {
        assertEquals(
            listOf(FasciaGiorno.MATTINO, FasciaGiorno.POMERIGGIO, FasciaGiorno.SERA),
            previsione.fasce.map { it.quale },
        )
    }

    @Test
    fun `le ore di notte non finiscono in nessuna fascia`() {
        // Le 3 e le 4 del mattino portano pioggia (codice 61) e vento: se
        // entrassero nel mattino, una mattina serena diventerebbe piovosa.
        val mattino = previsione.fascia(FasciaGiorno.MATTINO)!!
        assertEquals(CieloMeteo.SERENO, mattino.cielo)
        assertEquals(0.0, mattino.pioggiaMm!!, 0.001)
        assertEquals(15.0, mattino.minima!!, 0.001)
    }

    @Test
    fun `le temperature di una fascia sono la sua minima e la sua massima`() {
        val mattino = previsione.fascia(FasciaGiorno.MATTINO)!!
        // Non la media: "15–23°" dice come vestirsi, "19°" no.
        assertEquals(15.0, mattino.minima!!, 0.001)
        assertEquals(23.0, mattino.massima!!, 0.001)
    }

    @Test
    fun `il cielo di una fascia e' il piu' grave fra le sue ore`() {
        // Il pomeriggio ha nuvoloso, temporale e rovesci: quello da sapere e' il
        // temporale, e una media direbbe "nuvoloso" — cioe' una previsione falsa.
        assertEquals(CieloMeteo.TEMPORALE, previsione.fascia(FasciaGiorno.POMERIGGIO)!!.cielo)
    }

    @Test
    fun `un'ora senza codice non vince su un'ora di sole`() {
        // IGNOTO sta sotto tutti: un buco nei dati non deve cancellare il sole.
        val conBuco = """
            {"latitude":49.4,"longitude":11.1,
             "daily":{"time":["2026-08-16"]},
             "hourly":{"time":["2026-08-16T09:00","2026-08-16T10:00"],
                       "weather_code":[0,null]}}
        """.trimIndent()
        val fascia = RispostaMeteo.leggi(conBuco).single().previsioni.single()
            .fascia(FasciaGiorno.MATTINO)!!
        assertEquals(CieloMeteo.SERENO, fascia.cielo)
    }

    @Test
    fun `la probabilita' di pioggia di una fascia e' la piu' alta, i millimetri la somma`() {
        val pomeriggio = previsione.fascia(FasciaGiorno.POMERIGGIO)!!
        // Un'ora al 70% e' un pomeriggio in cui puoi bagnarti, anche se la media
        // dice 43. I millimetri invece sono una quantita': 0 + 5 + 1.
        assertEquals(70, pomeriggio.probabilitaPioggia)
        assertEquals(6.0, pomeriggio.pioggiaMm!!, 0.001)
    }

    @Test
    fun `il vento di una fascia e' il massimo`() {
        // Con un camper conta la raffica, non la media.
        assertEquals(34.0, previsione.fascia(FasciaGiorno.POMERIGGIO)!!.ventoKmh!!, 0.001)
    }

    @Test
    fun `una fascia da guardare si riconosce come un giorno da guardare`() {
        assertTrue(previsione.fascia(FasciaGiorno.POMERIGGIO)!!.daGuardare)
        assertTrue(!previsione.fascia(FasciaGiorno.MATTINO)!!.daGuardare)
    }

    @Test
    fun `senza il blocco delle ore le fasce non ci sono, e il giorno resta`() {
        // E' il caso di un file scritto prima delle fasce e dei giorni oltre
        // l'orizzonte orario: mezza previsione e' meglio di nessuna previsione.
        val senzaOre = """
            {"latitude":49.4,"longitude":11.1,
             "daily":{"time":["2026-08-16"],"temperature_2m_max":[28.0]}}
        """.trimIndent()
        val previsione = RispostaMeteo.leggi(senzaOre).single().previsioni.single()
        assertTrue(previsione.fasce.isEmpty())
        assertEquals(28.0, previsione.massima!!, 0.001)
        assertTrue(TestoMeteo.fasce(previsione).isEmpty())
        assertNull(TestoMeteo.fasceInLinea(previsione))
    }

    @Test
    fun `le ore di un giorno non finiscono nelle fasce di un altro`() {
        val dueGiorni = """
            {"latitude":49.4,"longitude":11.1,
             "daily":{"time":["2026-08-16","2026-08-17"]},
             "hourly":{"time":["2026-08-16T09:00","2026-08-17T09:00"],
                       "temperature_2m":[15.0,25.0]}}
        """.trimIndent()
        val previsioni = RispostaMeteo.leggi(dueGiorni).single().previsioni
        assertEquals(15.0, previsioni[0].fascia(FasciaGiorno.MATTINO)!!.massima!!, 0.001)
        assertEquals(25.0, previsioni[1].fascia(FasciaGiorno.MATTINO)!!.massima!!, 0.001)
    }

    @Test
    fun `la richiesta chiede anche le ore`() {
        val indirizzo = RispostaMeteo.indirizzo(listOf(PuntoMeteo("Roth", 49.2, 11.1)))
        assertTrue(indirizzo, indirizzo.contains("&hourly="))
        assertTrue(indirizzo, indirizzo.contains("precipitation_probability"))
        // `timezone=auto` e' il motivo per cui l'ora nel timestamp e' quella
        // locale del posto, e quindi per cui "sera" vuol dire sera.
        assertTrue(indirizzo, indirizzo.contains("timezone=auto"))
    }

    // --- come si leggono ------------------------------------------------------

    @Test
    fun `le tre righe si leggono una per fascia`() {
        val righe = TestoMeteo.fasce(previsione)
        assertEquals(3, righe.size)
        assertEquals("Mattino: Sereno, 15–23°", righe[0])
        // 25° e' l'ora delle 17, che sta ancora nel pomeriggio: la fascia arriva
        // fino alle 18 escluse, e la minima e' quella di tutte le sue ore.
        assertEquals("Pomeriggio: Temporale, 25–28°, pioggia 70%, 6,0 mm, vento 34 km/h", righe[1])
        assertTrue(righe[2], righe[2].startsWith("Sera: Nuvoloso, 18–21°"))
    }

    @Test
    fun `in linea si dice solo come cambia il cielo`() {
        // Per la notifica: tre temperature e tre probabilita' su una riga sono
        // una riga che non si legge.
        assertEquals(
            "mattino sereno, pomeriggio temporale, sera nuvoloso",
            TestoMeteo.fasceInLinea(previsione),
        )
    }
}
