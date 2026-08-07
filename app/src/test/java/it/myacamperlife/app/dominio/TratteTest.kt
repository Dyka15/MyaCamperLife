package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TratteTest {

    private val orvieto = Coordinate(42.7185, 12.1112)
    private val bolsena = Coordinate(42.6437, 11.9871)
    private val viterbo = Coordinate(42.4207, 12.1077)

    private fun tratta(da: Coordinate, a: Coordinate, km: Double, minuti: Int) =
        Tratta(da.lat, da.lon, a.lat, a.lon, km, minuti)

    private val scorta = Tratte(
        listOf(
            tratta(orvieto, bolsena, 31.2, 33),
            tratta(bolsena, viterbo, 47.2, 50),
        ),
    )

    // --- la ricerca -----------------------------------------------------------

    @Test
    fun `una tratta si ritrova dalle sue coordinate`() {
        val trovata = scorta.fra(orvieto.lat, orvieto.lon, bolsena.lat, bolsena.lon)
        assertEquals(31.2, trovata!!.km, 0.001)
        assertEquals(33, trovata.minuti)
    }

    @Test
    fun `qualche metro di scarto non impedisce di ritrovarla`() {
        // Le coordinate girano fra file scritti in momenti diversi: un
        // confronto esatto fra Double e' una promessa che si rompe.
        val trovata = scorta.fra(42.71852, 12.11118, 42.64371, 11.98713)
        assertEquals(31.2, trovata!!.km, 0.001)
    }

    @Test
    fun `una tappa spostata di chilometri e un'altra tratta`() {
        assertNull(scorta.fra(42.80, 12.20, bolsena.lat, bolsena.lon))
    }

    @Test
    fun `il verso conta, una tratta non e' la sua contraria`() {
        assertNull(scorta.fra(bolsena.lat, bolsena.lon, orvieto.lat, orvieto.lon))
    }

    // --- il percorso ----------------------------------------------------------

    @Test
    fun `il percorso somma i tratti e i minuti`() {
        val percorso = scorta.percorso(listOf(orvieto, bolsena, viterbo))
        assertEquals(78.4, percorso!!.km, 0.001)
        assertEquals(83, percorso.minuti)
    }

    @Test
    fun `se manca anche un solo tratto il percorso non si calcola`() {
        // Un totale con un pezzo mancante sarebbe piu' corto del vero, e su
        // quel numero si decide se rifornire.
        val incompleta = Tratte(listOf(tratta(orvieto, bolsena, 31.2, 33)))
        assertNull(incompleta.percorso(listOf(orvieto, bolsena, viterbo)))
    }

    @Test
    fun `un percorso con un punto solo non e' un percorso`() {
        assertNull(scorta.percorso(listOf(orvieto)))
        assertNull(scorta.percorso(emptyList()))
    }

    @Test
    fun `una scorta vuota lo dice`() {
        assertTrue(Tratte().vuoto)
        assertNull(Tratte().percorso(listOf(orvieto, bolsena)))
    }

    // --- come si legge un tempo di guida --------------------------------------

    @Test
    fun `sotto l'ora si dicono i minuti`() {
        assertEquals("45 min", Percorso(50.0, 45).durata)
    }

    @Test
    fun `sopra l'ora si dicono le ore, e i minuti con due cifre`() {
        assertEquals("2 h 15", Percorso(180.0, 135).durata)
        assertEquals("1 h 05", Percorso(80.0, 65).durata)
        assertEquals("3 h 00", Percorso(250.0, 180).durata)
    }
}
