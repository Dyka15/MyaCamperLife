package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanzaTest {

    private fun punto(minuti: Long, lat: Double, lon: Double) =
        Punto(OffsetDateTime.parse("2026-08-06T10:00:00+02:00").plusMinutes(minuti), lat, lon)

    @Test
    fun `Firenze Roma in linea d'aria sono circa 230 km`() {
        // Valore noto: utile perche' un errore nella formula si vede subito.
        val km = Distanza.km(43.7696, 11.2558, 41.9028, 12.4964)

        assertEquals(230.0, km, 5.0)
    }

    @Test
    fun `lo stesso punto da zero`() {
        assertEquals(0.0, Distanza.km(43.0, 11.0, 43.0, 11.0), 1e-9)
    }

    @Test
    fun `un grado di latitudine sono circa 111 km`() {
        assertEquals(111.2, Distanza.km(43.0, 11.0, 44.0, 11.0), 0.5)
    }

    @Test
    fun `la distanza non dipende dal verso`() {
        val andata = Distanza.km(43.7696, 11.2558, 41.9028, 12.4964)
        val ritorno = Distanza.km(41.9028, 12.4964, 43.7696, 11.2558)

        assertEquals(andata, ritorno, 1e-9)
    }

    @Test
    fun `una catena di punti somma i tratti`() {
        val percorsi = Distanza.percorsi(
            listOf(
                punto(0, 43.0, 11.0),
                punto(60, 44.0, 11.0),
                punto(120, 45.0, 11.0),
            ),
        )

        assertEquals(222.4, percorsi, 1.0)
    }

    @Test
    fun `l'ordine di arrivo non conta perche' si ordina per ora`() {
        val mescolati = listOf(
            punto(120, 45.0, 11.0),
            punto(0, 43.0, 11.0),
            punto(60, 44.0, 11.0),
        )

        assertEquals(222.4, Distanza.percorsi(mescolati), 1.0)
    }

    @Test
    fun `il rumore del GPS stando fermi non accumula chilometri`() {
        // Dieci posizioni registrate nello stesso posto, con qualche decina di
        // metri di scarto: senza la soglia diventerebbero centinaia di metri.
        val fermo = (0..9).map { i ->
            punto(i.toLong(), 43.0 + i * 0.0002, 11.0 + i * 0.0002)
        }

        assertEquals(0.0, Distanza.percorsi(fermo), 1e-9)
    }

    @Test
    fun `una guida lenta con registrazioni ravvicinate viene contata comunque`() {
        // Ogni tratto e' sotto soglia, ma restando ancorati al punto di
        // partenza la somma la supera e i chilometri si contano.
        val lenta = (0..20).map { i -> punto(i.toLong(), 43.0 + i * 0.005, 11.0) }

        val percorsi = Distanza.percorsi(lenta)

        assertTrue("attesi circa 11 km, ottenuti $percorsi", percorsi > 10.0)
    }

    @Test
    fun `un punto solo non fa distanza`() {
        assertEquals(0.0, Distanza.percorsi(listOf(punto(0, 43.0, 11.0))), 1e-9)
        assertEquals(0.0, Distanza.percorsi(emptyList()), 1e-9)
    }

    @Test
    fun `andata e ritorno contano il doppio non zero`() {
        // Il caso della gita fuori itinerario: si torna dove si era, ma i
        // chilometri sono stati fatti.
        val gita = listOf(
            punto(0, 43.0, 11.0),
            punto(60, 43.4, 11.0),
            punto(120, 43.0, 11.0),
        )

        assertEquals(89.0, Distanza.percorsi(gita), 2.0)
    }
}
