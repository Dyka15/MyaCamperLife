package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniatureTest {

    @Test
    fun `un'immagine gia' piccola non si riduce`() {
        assertEquals(1, Miniature.quantoRidurre(200, 150, 256))
    }

    @Test
    fun `uno scatto da dodici megapixel si riduce abbastanza`() {
        // 4000x3000 verso una miniatura da 256: 4000/16 = 250, dentro.
        assertEquals(16, Miniature.quantoRidurre(4000, 3000, 256))
        // Il controllo che conta e' questo: quanti pixel restano davvero.
        val pixel = (4000 / 16) * (3000 / 16)
        assertTrue("$pixel", pixel < 256 * 256)
    }

    @Test
    fun `una panoramica larga e bassa si riduce sul lato lungo`() {
        // 8000x1000: guardando solo l'altezza il fattore sarebbe 4, e la
        // larghezza resterebbe a duemila pixel. E' il caso per cui si guardano
        // entrambi i lati.
        val fattore = Miniature.quantoRidurre(8000, 1000, 256)
        assertTrue("$fattore", 8000 / fattore < 512)
    }

    @Test
    fun `il fattore e' sempre una potenza di due`() {
        val casi = listOf(
            Triple(4000, 3000, 256), Triple(1920, 1080, 1200),
            Triple(3, 5000, 256), Triple(640, 480, 64),
        )
        casi.forEach { (larghezza, altezza, massimo) ->
            val fattore = Miniature.quantoRidurre(larghezza, altezza, massimo)
            assertTrue("$fattore", fattore > 0 && fattore and (fattore - 1) == 0)
        }
    }

    @Test
    fun `si scende fino a entrare, e non oltre`() {
        // 512 verso 256: un dimezzamento porta esattamente a 256, che ci sta.
        assertEquals(2, Miniature.quantoRidurre(512, 512, 256))
        assertEquals(4, Miniature.quantoRidurre(1024, 1024, 256))
        // Un pixel oltre il limite costa un dimezzamento in piu': e' il prezzo
        // dell'unico fattore che `inSampleSize` sa applicare.
        assertEquals(2, Miniature.quantoRidurre(257, 257, 256))
    }

    @Test
    fun `il risultato entra sempre nel limite`() {
        val casi = listOf(
            Triple(4000, 3000, 256), Triple(8000, 1000, 256),
            Triple(1920, 1080, 1200), Triple(257, 3, 256),
        )
        casi.forEach { (larghezza, altezza, massimo) ->
            val fattore = Miniature.quantoRidurre(larghezza, altezza, massimo)
            assertTrue("$larghezza/$fattore", larghezza / fattore <= massimo)
            assertTrue("$altezza/$fattore", altezza / fattore <= massimo)
        }
    }

    @Test
    fun `misure impossibili non fanno girare a vuoto`() {
        // Un file illeggibile da' zero come dimensione: senza la guardia il ciclo
        // non finirebbe mai, e l'app resterebbe bloccata invece di mostrare un
        // riquadro vuoto.
        assertEquals(1, Miniature.quantoRidurre(0, 0, 256))
        assertEquals(1, Miniature.quantoRidurre(-1, 100, 256))
        assertEquals(1, Miniature.quantoRidurre(100, 100, 0))
    }
}
