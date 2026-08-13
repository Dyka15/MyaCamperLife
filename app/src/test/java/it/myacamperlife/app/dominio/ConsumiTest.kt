package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumiTest {

    private var contatore = 0

    private fun rif(
        km: Int,
        litri: Double,
        euro: Double? = null,
        pieno: Boolean = true,
        istante: OffsetDateTime? = null,
    ) = Rifornimento(
        id = "r${contatore++}",
        istante = istante
            ?: OffsetDateTime.parse("2026-08-01T10:00:00+02:00").plusDays(contatore.toLong()),
        km = km,
        litri = litri,
        euro = euro,
        pieno = pieno,
    )

    private fun ora(giorno: Int): OffsetDateTime =
        OffsetDateTime.parse("2026-08-%02dT10:00:00+02:00".format(giorno))

    @Test
    fun `due pieni danno un tratto e il consumo`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 48000, litri = 60.0),
                rif(km = 48600, litri = 50.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(600, tratto.km)
        assertEquals(50.0, tratto.litri, 1e-9)
        assertEquals(12.0, tratto.kmPerLitro, 1e-9)
        assertEquals(8.333, tratto.litriPer100, 1e-3)
    }

    @Test
    fun `i litri del tratto sono quelli messi dopo il primo pieno`() {
        // Il primo pieno riempie il serbatoio: i suoi litri appartengono al
        // tratto precedente, non a questo.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1000, litri = 70.0),
                rif(km = 1500, litri = 40.0),
            ),
        )

        assertEquals(40.0, consumo.segmenti.single().litri, 1e-9)
    }

    @Test
    fun `un riempimento parziale in mezzo entra nei litri del tratto`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1000, litri = 60.0),
                rif(km = 1300, litri = 20.0, pieno = false),
                rif(km = 1600, litri = 30.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(600, tratto.km)
        assertEquals("venti piu' trenta", 50.0, tratto.litri, 1e-9)
        assertEquals(12.0, tratto.kmPerLitro, 1e-9)
    }

    @Test
    fun `i rifornimenti prima del primo pieno non contano`() {
        // Senza sapere quanto c'era nel serbatoio all'inizio, nessun conto e'
        // possibile: quei litri restano fuori.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 900, litri = 25.0, pieno = false),
                rif(km = 1000, litri = 60.0),
                rif(km = 1600, litri = 50.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(1000, tratto.daKm)
        assertEquals(50.0, tratto.litri, 1e-9)
    }

    @Test
    fun `con un solo pieno non c'e' consumo`() {
        val consumo = Consumi.calcola(listOf(rif(km = 1000, litri = 60.0)))

        assertTrue(consumo.segmenti.isEmpty())
        assertFalse(consumo.presente)
        assertNull(consumo.kmPerLitro)
    }

    @Test
    fun `senza rifornimenti non c'e' consumo`() {
        val consumo = Consumi.calcola(emptyList())

        assertFalse(consumo.presente)
        assertNull(consumo.kmPerLitro)
        assertNull(consumo.euroTotali)
    }

    @Test
    fun `la media e pesata sui chilometri non e la media delle medie`() {
        // Un tratto lungo ed efficiente e uno corto e assetato: la media
        // delle medie darebbe 10,5 km/l, quella corretta 11,52.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 0, litri = 60.0),
                rif(km = 1200, litri = 100.0),  // 12 km/l su 1200 km
                rif(km = 1300, litri = 11.1),   // 9 km/l su 100 km
            ),
        )

        assertEquals(1300, consumo.kmTotali)
        assertEquals(111.1, consumo.litriTotali, 1e-9)
        assertEquals(11.70, consumo.kmPerLitro!!, 0.01)
    }

    @Test
    fun `gli importi si sommano e danno il costo per cento chilometri`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1000, litri = 60.0, euro = 100.0),
                rif(km = 1500, litri = 50.0, euro = 86.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(86.0, tratto.euro!!, 1e-9)
        assertEquals(17.2, tratto.euroPer100!!, 1e-9)
        assertEquals(0.172, tratto.euroPerKm!!, 1e-9)
        assertEquals(86.0, consumo.euroTotali!!, 1e-9)
    }

    @Test
    fun `un importo mancante annulla i soldi ma non i litri`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1000, litri = 60.0, euro = 100.0),
                rif(km = 1300, litri = 20.0, euro = null, pieno = false),
                rif(km = 1600, litri = 30.0, euro = 52.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertNull("un buco negli importi non si inventa", tratto.euro)
        assertEquals(50.0, tratto.litri, 1e-9)
        assertEquals(12.0, tratto.kmPerLitro, 1e-9)
        assertNull(consumo.euroTotali)
    }

    @Test
    fun `un tratto con chilometraggio fermo viene scartato`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1000, litri = 60.0),
                rif(km = 1000, litri = 10.0),
            ),
        )

        assertTrue(consumo.segmenti.isEmpty())
    }

    @Test
    fun `l'ordine di inserimento non conta perche' si ordina per ora`() {
        // **Si ordina per ora, non piu' per contachilometri.** Era il
        // contachilometri finche' era l'unica misura; un rifornimento che porta
        // solo i chilometri fatti dall'ultima colonnina non si ordina su quelli,
        // e l'ora del rifornimento c'e' sempre — e' la stessa che lo mette nel
        // giorno giusto del diario.
        val mescolati = listOf(
            rif(km = 1600, litri = 30.0, istante = ora(9)),
            rif(km = 1000, litri = 60.0, istante = ora(5)),
            rif(km = 1300, litri = 20.0, pieno = false, istante = ora(7)),
        )

        val consumo = Consumi.calcola(mescolati)

        assertEquals(600, consumo.segmenti.single().km)
        assertEquals(50.0, consumo.segmenti.single().litri, 1e-9)
    }

    @Test
    fun `un contachilometri che torna indietro non produce un tratto`() {
        // Una data sbagliata mette un rifornimento prima di uno che l'ha
        // precedute: la differenza dei contachilometri viene negativa. Il tratto
        // si scarta invece di entrare nella media con un numero impossibile — il
        // rifornimento resta nel diario, dove si vede e si corregge.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 1600, litri = 60.0, istante = ora(5)),
                rif(km = 1000, litri = 50.0, istante = ora(7)),
            ),
        )
        assertTrue(consumo.segmenti.isEmpty())
    }

    @Test
    fun `tre pieni danno due tratti`() {
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 0, litri = 60.0),
                rif(km = 600, litri = 50.0),
                rif(km = 1200, litri = 48.0),
            ),
        )

        assertEquals(2, consumo.segmenti.size)
        assertEquals(listOf(600, 600), consumo.segmenti.map { it.km })
    }

    // --- i chilometri dal pieno precedente -------------------------------------

    /**
     * Il modo nuovo di registrare: **il parziale azzerato alla colonnina**.
     *
     * Non e' una comodita' di digitazione. Un contachilometri e' un numero di sei
     * cifre da copiare mentre si tiene la pompa, e sbagliarne una fa un tratto di
     * ventimila chilometri; il parziale ne ha tre e misura esattamente
     * l'intervallo che serve al consumo.
     */
    private fun daPieno(km: Int, litri: Double, euro: Double? = null, pieno: Boolean = true) =
        Rifornimento(
            id = "d${contatore++}",
            istante = OffsetDateTime.parse("2026-08-01T10:00:00+02:00").plusDays(contatore.toLong()),
            kmDaPieno = km,
            litri = litri,
            euro = euro,
            pieno = pieno,
        )

    @Test
    fun `il parziale del secondo pieno e' la lunghezza del tratto`() {
        val consumo = Consumi.calcola(
            listOf(
                // Il parziale del primo pieno riguarda il tratto precedente, che
                // non c'e': non entra in nessun conto.
                daPieno(km = 700, litri = 60.0),
                daPieno(km = 600, litri = 50.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(600, tratto.km)
        assertEquals(12.0, tratto.kmPerLitro, 1e-9)
        // Da dove a dove non si sa, e non si inventa: il parziale dice quanto e'
        // lungo il tratto, non dove sta nella vita del mezzo.
        assertNull(tratto.daKm)
        assertNull(tratto.aKm)
    }

    @Test
    fun `i parziali di un rabbocco si sommano, come i litri`() {
        // Pieno, 300 km, rabbocco, altri 300, pieno: il tratto e' 600 km e i
        // litri sono quelli del rabbocco piu' quelli del pieno di chiusura.
        val consumo = Consumi.calcola(
            listOf(
                daPieno(km = 500, litri = 60.0),
                daPieno(km = 300, litri = 20.0, pieno = false),
                daPieno(km = 300, litri = 30.0),
            ),
        )

        val tratto = consumo.segmenti.single()
        assertEquals(600, tratto.km)
        assertEquals(50.0, tratto.litri, 1e-9)
    }

    @Test
    fun `senza il parziale si ripiega sui contachilometri`() {
        // Le righe scritte prima che il parziale esistesse continuano a contare.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 48000, litri = 60.0),
                rif(km = 48600, litri = 50.0),
            ),
        )
        val tratto = consumo.segmenti.single()
        assertEquals(600, tratto.km)
        assertEquals(48000, tratto.daKm)
        assertEquals(48600, tratto.aKm)
    }

    @Test
    fun `passando dal totale al parziale il tratto si misura comunque`() {
        // Il giorno del passaggio: il pieno di prima porta il contachilometri,
        // quello nuovo il parziale. Il parziale misura proprio quel tratto, ed e'
        // per questo che si guarda lui per primo.
        val consumo = Consumi.calcola(
            listOf(
                rif(km = 48000, litri = 60.0),
                daPieno(km = 620, litri = 50.0),
            ),
        )
        assertEquals(620, consumo.segmenti.single().km)
    }

    @Test
    fun `un tratto senza nessuna delle due misure si scarta`() {
        val senzaNiente = Rifornimento(
            id = "x", istante = OffsetDateTime.parse("2026-08-02T10:00:00+02:00"),
            litri = 50.0, pieno = true,
        )
        val consumo = Consumi.calcola(listOf(rif(km = 48000, litri = 60.0), senzaNiente))
        // Inventare i chilometri di un consumo vorrebbe dire inventare il consumo.
        assertTrue(consumo.segmenti.isEmpty())
        assertFalse(consumo.presente)
    }
}
