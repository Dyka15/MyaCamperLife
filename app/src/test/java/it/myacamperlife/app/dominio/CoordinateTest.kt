package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTest {

    private fun leggi(testo: String?) = Coordinate.leggi(testo)

    private fun assertOrvieto(coordinate: Coordinate?) {
        assertEquals(42.7185, coordinate!!.lat, 1e-6)
        assertEquals(12.1112, coordinate.lon, 1e-6)
    }

    // --- le forme che si incollano --------------------------------------------

    @Test
    fun `il formato di Google Maps, virgola e spazio`() {
        assertOrvieto(leggi("42.7185, 12.1112"))
        assertOrvieto(leggi("42.7185,12.1112"))
    }

    @Test
    fun `due virgole sono due decimali, e separa lo spazio`() {
        // Come le digita una tastiera italiana.
        assertOrvieto(leggi("42,7185 12,1112"))
    }

    @Test
    fun `il punto e virgola separa sempre`() {
        assertOrvieto(leggi("42,7185;12,1112"))
        assertOrvieto(leggi("42.7185; 12.1112"))
    }

    @Test
    fun `solo lo spazio basta`() {
        assertOrvieto(leggi("42.7185 12.1112"))
    }

    @Test
    fun `gli spazi in eccesso non disturbano`() {
        assertOrvieto(leggi("  42.7185 ,  12.1112  "))
    }

    @Test
    fun `le lettere del quadrante si leggono e danno il segno`() {
        assertOrvieto(leggi("42.7185 N, 12.1112 E"))
        assertOrvieto(leggi("N42.7185 E12.1112"))

        val sudOvest = leggi("33.8688 S, 151.2093 W")!!
        assertEquals(-33.8688, sudOvest.lat, 1e-6)
        assertEquals(-151.2093, sudOvest.lon, 1e-6)
    }

    @Test
    fun `l'ovest si scrive anche con la O, come in italiano`() {
        assertEquals(-9.1393, leggi("38.7223 N, 9.1393 O")!!.lon, 1e-6)
    }

    @Test
    fun `il segno meno funziona`() {
        val patagonia = leggi("-50.3369, -72.2648")!!
        assertEquals(-50.3369, patagonia.lat, 1e-6)
        assertEquals(-72.2648, patagonia.lon, 1e-6)
    }

    @Test
    fun `i gradi interi si accettano`() {
        assertEquals(42.0, leggi("42, 12")!!.lat, 1e-9)
        assertEquals(12.0, leggi("42, 12")!!.lon, 1e-9)
    }

    // --- quello che non si legge ----------------------------------------------

    @Test
    fun `un campo vuoto non da coordinate`() {
        assertNull(leggi(null))
        assertNull(leggi(""))
        assertNull(leggi("   "))
    }

    @Test
    fun `un numero solo non e' una coppia`() {
        assertNull(leggi("42.7185"))
        // Una virgola decimale sola resta un numero solo.
        assertNull(leggi("42,7185"))
    }

    @Test
    fun `tre numeri non sono una coppia`() {
        assertNull(leggi("42.7185 12.1112 300"))
    }

    @Test
    fun `del testo non e' una coppia di coordinate`() {
        assertNull(leggi("Orvieto"))
        assertNull(leggi("via Cassia 214"))
    }

    @Test
    fun `coordinate fuori dal mondo si rifiutano`() {
        assertNull(leggi("142.7185, 12.1112"))
        assertNull(leggi("42.7185, 212.1112"))
        assertNull(leggi("-91, 0"))
    }

    // --- come si scrivono -----------------------------------------------------

    @Test
    fun `si scrivono col punto decimale e sei cifre`() {
        assertEquals("42.718500, 12.111200", Coordinate(42.7185, 12.1112).toString())
    }

    @Test
    fun `quello che l'app scrive, l'app lo rilegge`() {
        val orvieto = Coordinate(42.7185, 12.1112)
        assertOrvieto(leggi(orvieto.toString()))
    }

    @Test
    fun `le coordinate valide si riconoscono`() {
        assertTrue(Coordinate(42.7185, 12.1112).valide)
        assertFalse(Coordinate(91.0, 0.0).valide)
        assertFalse(Coordinate(0.0, -181.0).valide)
    }
}
