package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteoTest {

    private val scaricatoIl = "2026-08-06T19:00:00+02:00"
    private val sera: OffsetDateTime = OffsetDateTime.parse(scaricatoIl)

    private fun previsione(
        giorno: String = "2026-08-07",
        codice: Int? = 0,
        minima: Double? = 18.0,
        massima: Double? = 31.0,
        pioggiaMm: Double? = 0.0,
        probabilita: Int? = 5,
        vento: Double? = 10.0,
    ) = Previsione(giorno, codice, minima, massima, pioggiaMm, probabilita, vento)

    private fun meteo(vararg luoghi: MeteoLuogo, quando: String = scaricatoIl) =
        Meteo(scaricatoIl = quando, luoghi = luoghi.toList())

    // --- la ricerca -----------------------------------------------------------

    @Test
    fun `la previsione si trova col luogo piu vicino, non con quello uguale`() {
        // Open-Meteo risponde con le coordinate del suo nodo di griglia.
        val scorta = meteo(MeteoLuogo("Viterbo", 42.4207, 12.1077, listOf(previsione())))
        val trovata = scorta.per(42.4300, 12.1200, LocalDate.parse("2026-08-07"))
        assertEquals(31.0, trovata!!.massima!!, 0.001)
    }

    @Test
    fun `un luogo troppo lontano non vale`() {
        val scorta = meteo(MeteoLuogo("Viterbo", 42.4207, 12.1077, listOf(previsione())))
        assertNull(scorta.per(45.4642, 9.1900, LocalDate.parse("2026-08-07")))
    }

    @Test
    fun `fra due luoghi vicini vince il piu vicino`() {
        val scorta = meteo(
            MeteoLuogo("Bolsena", 42.6437, 11.9871, listOf(previsione(massima = 29.0))),
            MeteoLuogo("Viterbo", 42.4207, 12.1077, listOf(previsione(massima = 33.0))),
        )
        val trovata = scorta.per(42.4210, 12.1080, LocalDate.parse("2026-08-07"))
        assertEquals(33.0, trovata!!.massima!!, 0.001)
    }

    @Test
    fun `un giorno senza previsione non da niente`() {
        val scorta = meteo(MeteoLuogo("Viterbo", 42.4207, 12.1077, listOf(previsione())))
        assertNull(scorta.per(42.4207, 12.1077, LocalDate.parse("2026-08-20")))
    }

    // --- l'eta' ---------------------------------------------------------------

    @Test
    fun `una previsione di stasera non e scaduta`() {
        assertFalse(meteo().scaduto(sera.plusHours(2)))
    }

    @Test
    fun `dopo tre giorni la previsione e scaduta`() {
        assertTrue(meteo().scaduto(sera.plusDays(4)))
    }

    @Test
    fun `una data di scarico illeggibile vale come scaduta`() {
        assertTrue(Meteo(scaricatoIl = "boh").scaduto(sera))
    }

    @Test
    fun `l'eta si dice come la direbbe una persona`() {
        assertEquals("meteo di poco fa", TestoMeteo.eta(1))
        assertEquals("meteo di 5 ore fa", TestoMeteo.eta(5))
        assertEquals("meteo di ieri", TestoMeteo.eta(20))
        assertEquals("meteo di 2 giorni fa", TestoMeteo.eta(50))
        assertNull(TestoMeteo.eta(null))
        assertNull(TestoMeteo.eta(-3))
    }

    // --- il cielo -------------------------------------------------------------

    @Test
    fun `i codici WMO si raggruppano in poche parole`() {
        assertEquals(CieloMeteo.SERENO, CieloMeteo.da(0))
        assertEquals(CieloMeteo.POCO_NUVOLOSO, CieloMeteo.da(2))
        assertEquals(CieloMeteo.PIOGGIA, CieloMeteo.da(63))
        assertEquals(CieloMeteo.TEMPORALE, CieloMeteo.da(95))
        assertEquals(CieloMeteo.NEVE, CieloMeteo.da(75))
    }

    @Test
    fun `un codice sconosciuto non fa cadere niente`() {
        assertEquals(CieloMeteo.IGNOTO, CieloMeteo.da(1234))
        assertEquals(CieloMeteo.IGNOTO, CieloMeteo.da(null))
    }

    @Test
    fun `una giornata da guardare si riconosce da pioggia o vento`() {
        assertFalse(previsione().daGuardare)
        assertTrue(previsione(probabilita = 70).daGuardare)
        assertTrue(previsione(pioggiaMm = 8.0).daGuardare)
        assertTrue(previsione(vento = 60.0).daGuardare)
        assertTrue(previsione(codice = 95).daGuardare)
    }

    // --- come si scrive -------------------------------------------------------

    @Test
    fun `una bella giornata si dice in poche parole`() {
        assertEquals("Sereno, 18–31°", TestoMeteo.riga(previsione()))
    }

    @Test
    fun `la pioggia probabile si dice, quella improbabile no`() {
        assertEquals("Sereno, 18–31°", TestoMeteo.riga(previsione(probabilita = 5)))
        assertEquals("Sereno, 18–31°, pioggia 40%", TestoMeteo.riga(previsione(probabilita = 40)))
    }

    @Test
    fun `i millimetri si aggiungono alla probabilita quando ce ne sono`() {
        assertEquals(
            "Pioggia, 18–31°, pioggia 80%, 12 mm",
            TestoMeteo.riga(previsione(codice = 63, probabilita = 80, pioggiaMm = 12.0)),
        )
    }

    @Test
    fun `il vento si nomina solo quando conta`() {
        assertEquals("Sereno, 18–31°", TestoMeteo.riga(previsione(vento = 12.0)))
        assertEquals(
            "Sereno, 18–31°, vento 45 km/h",
            TestoMeteo.riga(previsione(vento = 45.0)),
        )
    }

    @Test
    fun `mezza previsione si scrive lo stesso`() {
        assertEquals(
            "Cielo non pervenuto, max 28°",
            TestoMeteo.riga(
                Previsione(giorno = "2026-08-07", codice = null, minima = null, massima = 28.0),
            ),
        )
    }
}
