package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeseTest {

    private var contatore = 0

    private fun spesa(
        euro: Double,
        categoria: Categoria = Categoria.ALTRO,
        modalita: Modalita = Modalita.CONTANTI,
        giorno: String = "2026-08-06",
        valuta: String = "EUR",
        cambio: Double? = null,
    ) = Spesa(
        id = "s${contatore++}",
        istante = OffsetDateTime.parse("${giorno}T12:0${contatore % 10}:00+02:00"),
        categoria = categoria,
        importo = euro,
        modalita = modalita,
        valuta = valuta,
        cambio = cambio,
    )

    // --- la voce -------------------------------------------------------------

    @Test
    fun `una spesa in euro vale il suo importo`() {
        val spesa = spesa(18.0)
        assertFalse(spesa.estera)
        assertEquals(18.0, spesa.euro, 1e-9)
    }

    @Test
    fun `una spesa in valuta estera si converte col cambio registrato`() {
        val spesa = spesa(45.0, valuta = "CHF", cambio = 1.06)
        assertTrue(spesa.estera)
        assertEquals(47.7, spesa.euro, 1e-9)
    }

    @Test
    fun `una spesa estera senza cambio non sparisce dal conto`() {
        // Vale uno: meglio un totale un po' sbagliato che una voce invisibile.
        assertEquals(45.0, spesa(45.0, valuta = "CHF").euro, 1e-9)
    }

    @Test
    fun `la valuta si confronta senza guardare le maiuscole`() {
        assertFalse(spesa(10.0, valuta = "eur").estera)
    }

    @Test
    fun `una categoria sconosciuta diventa altro`() {
        assertEquals(Categoria.ALTRO, Categoria.da("acquascivolo"))
        assertEquals(Categoria.ALTRO, Categoria.da(null))
        assertEquals(Categoria.SOSTA, Categoria.da(" Sosta "))
    }

    @Test
    fun `una modalita sconosciuta diventa contanti`() {
        assertEquals(Modalita.CONTANTI, Modalita.da("assegno"))
        assertEquals(Modalita.CARTA, Modalita.da("CARTA"))
    }

    // --- il conto ------------------------------------------------------------

    @Test
    fun `senza spese e senza carburante il conto e vuoto`() {
        val conto = Spese.conta(emptyList())
        assertTrue(conto.vuoto)
        assertEquals(0.0, conto.totale, 1e-9)
        assertNull(conto.alGiorno)
    }

    @Test
    fun `il totale somma le spese e il carburante resta distinto`() {
        val conto = Spese.conta(listOf(spesa(18.0), spesa(32.0)), carburante = 100.0)
        assertEquals(50.0, conto.spese, 1e-9)
        assertEquals(100.0, conto.carburante, 1e-9)
        assertEquals(150.0, conto.totale, 1e-9)
        assertEquals(2, conto.voci)
    }

    @Test
    fun `il carburante da solo basta a non rendere vuoto il conto`() {
        assertFalse(Spese.conta(emptyList(), carburante = 80.0).vuoto)
    }

    @Test
    fun `le categorie escono dalla piu cara alla meno cara`() {
        val conto = Spese.conta(
            listOf(
                spesa(10.0, Categoria.SPESA),
                spesa(30.0, Categoria.SOSTA),
                spesa(5.0, Categoria.SPESA),
                spesa(60.0, Categoria.RISTORANTE),
            ),
        )
        assertEquals(
            listOf(Categoria.RISTORANTE, Categoria.SOSTA, Categoria.SPESA),
            conto.perCategoria.map { it.chiave },
        )
        val spesa = conto.perCategoria.first { it.chiave == Categoria.SPESA }
        assertEquals(15.0, spesa.euro, 1e-9)
        assertEquals(2, spesa.voci)
    }

    @Test
    fun `a pari importo le categorie non si scambiano di posto`() {
        val conto = Spese.conta(listOf(spesa(10.0, Categoria.VISITE), spesa(10.0, Categoria.SOSTA)))
        assertEquals(
            listOf(Categoria.SOSTA, Categoria.VISITE),
            conto.perCategoria.map { it.chiave },
        )
    }

    @Test
    fun `le modalita si sommano ciascuna per conto suo`() {
        val conto = Spese.conta(
            listOf(
                spesa(20.0, modalita = Modalita.CONTANTI),
                spesa(50.0, modalita = Modalita.CARTA),
                spesa(5.0, modalita = Modalita.CONTANTI),
            ),
        )
        assertEquals(2, conto.perModalita.size)
        assertEquals(Modalita.CARTA, conto.perModalita.first().chiave)
        assertEquals(25.0, conto.perModalita.first { it.chiave == Modalita.CONTANTI }.euro, 1e-9)
    }

    @Test
    fun `i giorni escono in ordine di calendario`() {
        val conto = Spese.conta(
            listOf(
                spesa(10.0, giorno = "2026-08-08"),
                spesa(20.0, giorno = "2026-08-06"),
                spesa(5.0, giorno = "2026-08-08"),
            ),
        )
        assertEquals(
            listOf(LocalDate.parse("2026-08-06"), LocalDate.parse("2026-08-08")),
            conto.perGiorno.map { it.chiave },
        )
        assertEquals(15.0, conto.perGiorno.last().euro, 1e-9)
    }

    @Test
    fun `la media giornaliera conta anche i giorni in cui non hai speso`() {
        // 6 e 8 agosto: tre giorni coperti, non due.
        val conto = Spese.conta(
            listOf(spesa(30.0, giorno = "2026-08-06"), spesa(60.0, giorno = "2026-08-08")),
        )
        assertEquals(3, conto.giorni)
        assertEquals(30.0, conto.alGiorno!!, 1e-9)
    }

    @Test
    fun `la media giornaliera comprende il carburante`() {
        val conto = Spese.conta(listOf(spesa(10.0, giorno = "2026-08-06")), carburante = 50.0)
        assertEquals(1, conto.giorni)
        assertEquals(60.0, conto.alGiorno!!, 1e-9)
    }

    @Test
    fun `il giorno di un rifornimento allunga il viaggio anche se non hai speso altro`() {
        val conto = Spese.conta(
            spese = listOf(spesa(30.0, giorno = "2026-08-06")),
            carburante = 90.0,
            giorniDelCarburante = listOf(LocalDate.parse("2026-08-08")),
        )
        assertEquals(3, conto.giorni)
        assertEquals(40.0, conto.alGiorno!!, 1e-9)
    }

    @Test
    fun `un viaggio col solo carburante ha comunque i suoi giorni`() {
        val conto = Spese.conta(
            spese = emptyList(),
            carburante = 100.0,
            giorniDelCarburante = listOf(
                LocalDate.parse("2026-08-06"),
                LocalDate.parse("2026-08-07"),
            ),
        )
        assertEquals(2, conto.giorni)
        assertEquals(50.0, conto.alGiorno!!, 1e-9)
    }

    @Test
    fun `le valute estere si raggruppano tenendo gli importi originali`() {
        val conto = Spese.conta(
            listOf(
                spesa(45.0, valuta = "CHF", cambio = 1.06),
                spesa(15.0, valuta = "CHF", cambio = 1.06),
                spesa(20.0),
            ),
        )
        val chf = conto.valute.single()
        assertEquals("CHF", chf.valuta)
        assertEquals(60.0, chf.importo, 1e-9)
        assertEquals(63.6, chf.euro, 1e-9)
        assertEquals(2, chf.voci)
        // Il totale e' in euro, sempre.
        assertEquals(83.6, conto.spese, 1e-9)
    }

    @Test
    fun `la frazione di una quota sta fra zero e uno`() {
        val quota = Quota(Categoria.SOSTA, 25.0, 1)
        assertEquals(0.25f, quota.frazione(100.0), 1e-6f)
        assertEquals(0f, quota.frazione(0.0), 1e-6f)
    }

    // --- il cambio -----------------------------------------------------------

    @Test
    fun `l ultimo cambio usato e quello della spesa piu recente`() {
        val vecchia = spesa(10.0, valuta = "CHF", cambio = 1.02, giorno = "2026-08-01")
        val nuova = spesa(10.0, valuta = "CHF", cambio = 1.07, giorno = "2026-08-05")
        assertEquals(1.07, Spese.ultimoCambio(listOf(nuova, vecchia), "CHF")!!, 1e-9)
        assertNull(Spese.ultimoCambio(listOf(nuova), "GBP"))
    }

    @Test
    fun `le valute usate escono dalla piu recente e senza ripetizioni`() {
        val spese = listOf(
            spesa(10.0, valuta = "CHF", cambio = 1.0, giorno = "2026-08-01"),
            spesa(10.0, valuta = "GBP", cambio = 1.2, giorno = "2026-08-04"),
            spesa(10.0, valuta = "CHF", cambio = 1.0, giorno = "2026-08-02"),
            spesa(10.0),
        )
        assertEquals(listOf("GBP", "CHF"), Spese.valuteUsate(spese))
    }
}
