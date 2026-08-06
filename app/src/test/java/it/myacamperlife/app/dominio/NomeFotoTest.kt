package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NomeFotoTest {

    private val istante = OffsetDateTime.parse("2026-08-06T14:30:12+02:00")

    @Test
    fun `nome con la localita`() {
        assertEquals("foto_20260806_143012_Orvieto.jpg", NomeFoto.per(istante, "Orvieto"))
    }

    @Test
    fun `nome senza localita`() {
        assertEquals("foto_20260806_143012.jpg", NomeFoto.per(istante))
        assertEquals("foto_20260806_143012.jpg", NomeFoto.per(istante, "   "))
    }

    @Test
    fun `gli spazi diventano trattini e gli accenti spariscono`() {
        assertEquals("foto_20260806_143012_Lago-di-Bolsena.jpg", NomeFoto.per(istante, "Lago di Bolsena"))
        assertEquals("foto_20260806_143012_Citta-di-Castello.jpg", NomeFoto.per(istante, "Città di Castello"))
    }

    @Test
    fun `i caratteri che un filesystem non gradisce non entrano nel nome`() {
        val nome = NomeFoto.per(istante, "Roma / ZTL?")

        assertEquals("foto_20260806_143012_Roma-ZTL.jpg", nome)
    }

    @Test
    fun `una localita che si riduce a niente non lascia un trattino appeso`() {
        assertEquals("foto_20260806_143012.jpg", NomeFoto.per(istante, "???"))
    }

    @Test
    fun `l'ora ha sempre due cifre cosi l'ordine alfabetico e cronologico`() {
        val mattina = OffsetDateTime.parse("2026-08-06T09:05:03+02:00")

        assertEquals("foto_20260806_090503.jpg", NomeFoto.per(mattina))
    }
}
