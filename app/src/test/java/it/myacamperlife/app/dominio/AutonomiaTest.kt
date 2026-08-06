package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomiaTest {

    private fun ora(giorno: Int, h: Int) =
        OffsetDateTime.parse("2026-08-%02dT%02d:00:00+02:00".format(giorno, h))

    private fun pieno(giorno: Int, h: Int, km: Int, lat: Double? = null, lon: Double? = null) =
        Rifornimento(
            id = "p$giorno$h", istante = ora(giorno, h), km = km, litri = 60.0,
            pieno = true, lat = lat, lon = lon,
        )

    private fun parziale(giorno: Int, h: Int, km: Int) =
        Rifornimento(id = "q$giorno$h", istante = ora(giorno, h), km = km, litri = 20.0, pieno = false)

    @Test
    fun `senza il parametro non si stima niente`() {
        val stima = StimaAutonomia.calcola(null, listOf(pieno(1, 8, 1000)), emptyList())

        assertNull(stima)
    }

    @Test
    fun `un parametro assurdo non produce una stima`() {
        assertNull(StimaAutonomia.calcola(0, listOf(pieno(1, 8, 1000)), emptyList()))
        assertNull(StimaAutonomia.calcola(-100, listOf(pieno(1, 8, 1000)), emptyList()))
    }

    @Test
    fun `senza pieni non si stima niente`() {
        val stima = StimaAutonomia.calcola(900, listOf(parziale(1, 8, 1000)), emptyList())

        assertNull("un parziale non dice quanto c'e' nel serbatoio", stima)
    }

    @Test
    fun `appena fatto il pieno l'autonomia e' intera e lo dichiara`() {
        val stima = StimaAutonomia.calcola(900, listOf(pieno(1, 8, 1000)), emptyList())!!

        assertEquals(0.0, stima.kmStimati, 1e-9)
        assertEquals(900.0, stima.residui, 1e-9)
        assertTrue("senza punti registrati va detto", stima.senzaDati)
        assertEquals(1f, stima.frazione, 1e-6f)
    }

    @Test
    fun `i punti dopo il pieno scalano l'autonomia`() {
        val stima = StimaAutonomia.calcola(
            kmConUnPieno = 900,
            rifornimenti = listOf(pieno(1, 8, 1000, lat = 43.0, lon = 11.0)),
            punti = listOf(
                Punto(ora(1, 12), 44.0, 11.0),
                Punto(ora(1, 18), 45.0, 11.0),
            ),
        )!!

        assertEquals(222.4, stima.kmStimati, 1.0)
        assertEquals(677.6, stima.residui, 1.0)
        assertFalse(stima.senzaDati)
        assertEquals(2, stima.puntiUsati)
    }

    @Test
    fun `il conto parte dal distributore quando ne conosciamo la posizione`() {
        // Con la posizione del pieno, il primo tratto conta; senza, si parte
        // dal primo punto registrato e quel tratto e' perso.
        val punti = listOf(Punto(ora(1, 12), 44.0, 11.0), Punto(ora(1, 18), 45.0, 11.0))

        val conPosizione = StimaAutonomia.calcola(
            900, listOf(pieno(1, 8, 1000, lat = 43.0, lon = 11.0)), punti,
        )!!
        val senzaPosizione = StimaAutonomia.calcola(900, listOf(pieno(1, 8, 1000)), punti)!!

        assertEquals(222.4, conPosizione.kmStimati, 1.0)
        assertEquals(111.2, senzaPosizione.kmStimati, 1.0)
    }

    @Test
    fun `i punti prima del pieno non contano`() {
        val stima = StimaAutonomia.calcola(
            kmConUnPieno = 900,
            rifornimenti = listOf(pieno(2, 8, 1000, lat = 43.0, lon = 11.0)),
            punti = listOf(
                Punto(ora(1, 10), 40.0, 11.0),  // il giorno prima: fuori
                Punto(ora(2, 12), 44.0, 11.0),
            ),
        )!!

        assertEquals(111.2, stima.kmStimati, 1.0)
        assertEquals(1, stima.puntiUsati)
    }

    @Test
    fun `vale l'ultimo pieno non il primo`() {
        val stima = StimaAutonomia.calcola(
            kmConUnPieno = 900,
            rifornimenti = listOf(
                pieno(1, 8, 1000, lat = 40.0, lon = 11.0),
                pieno(3, 8, 1800, lat = 43.0, lon = 11.0),
            ),
            punti = listOf(
                Punto(ora(2, 12), 41.0, 11.0),  // fra i due pieni: fuori
                Punto(ora(3, 12), 44.0, 11.0),
            ),
        )!!

        assertEquals(ora(3, 8), stima.ultimoPieno)
        assertEquals(111.2, stima.kmStimati, 1.0)
    }

    @Test
    fun `l'autonomia non va sotto zero`() {
        val stima = StimaAutonomia.calcola(
            kmConUnPieno = 100,
            rifornimenti = listOf(pieno(1, 8, 1000, lat = 43.0, lon = 11.0)),
            punti = listOf(Punto(ora(1, 12), 46.0, 11.0)),
        )!!

        assertTrue(stima.kmStimati > 300)
        assertEquals("si mostra zero, non un numero negativo", 0.0, stima.residui, 1e-9)
        assertEquals(0f, stima.frazione, 1e-6f)
    }

    @Test
    fun `una gita andata e ritorno viene contata`() {
        // Il caso che ha motivato l'uso di tutti i punti e non solo dei
        // check-in: si torna alla stessa tappa, ma il carburante e' andato.
        val stima = StimaAutonomia.calcola(
            kmConUnPieno = 900,
            rifornimenti = listOf(pieno(1, 8, 1000, lat = 43.0, lon = 11.0)),
            punti = listOf(
                Punto(ora(1, 11), 43.4, 11.0),
                Punto(ora(1, 16), 43.0, 11.0),
            ),
        )!!

        assertEquals(89.0, stima.kmStimati, 2.0)
    }
}
