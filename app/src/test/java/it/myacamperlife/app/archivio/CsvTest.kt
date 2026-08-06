package it.myacamperlife.app.archivio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun `il decimale e la virgola`() {
        assertEquals("1,72", Csv.numero(1.72))
        assertEquals("107,16", Csv.numero(107.16))
        assertEquals("42,718500", Csv.numero(42.7185, 6))
        assertEquals("0,00", Csv.numero(0.0))
    }

    @Test
    fun `il numero non dipende dalla lingua del telefono`() {
        // Se si usasse la Locale di sistema, lo stesso file cambierebbe
        // formato cambiando lingua al telefono.
        val precedente = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("1,72", Csv.numero(1.72))
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1,72", Csv.numero(1.72))
        } finally {
            java.util.Locale.setDefault(precedente)
        }
    }

    @Test
    fun `in lettura si accettano virgola e punto`() {
        assertEquals(1.72, Csv.leggiNumero("1,72")!!, 1e-9)
        assertEquals(1.72, Csv.leggiNumero("1.72")!!, 1e-9)
        assertEquals(1.72, Csv.leggiNumero(" 1,72 ")!!, 1e-9)
    }

    @Test
    fun `il punto delle migliaia messo da un foglio di calcolo si butta`() {
        assertEquals(1234.5, Csv.leggiNumero("1.234,5")!!, 1e-9)
        assertEquals(1234.5, Csv.leggiNumero("1 234,5")!!, 1e-9)
        // Spazio insecabile: quello che i fogli di calcolo usano davvero.
        assertEquals(1234.5, Csv.leggiNumero("1\u00A0234,5")!!, 1e-9)
    }

    @Test
    fun `un numero assente o illeggibile e nullo non zero`() {
        assertNull(Csv.leggiNumero(""))
        assertNull(Csv.leggiNumero("   "))
        assertNull(Csv.leggiNumero(null))
        assertNull(Csv.leggiNumero("abc"))
    }

    @Test
    fun `un campo con il separatore viene recintato`() {
        val riga = Csv.componi(listOf("a", "Orvieto; Scalo", "c"))

        assertEquals("a;\"Orvieto; Scalo\";c", riga)
        assertEquals(listOf("a", "Orvieto; Scalo", "c"), Csv.dividi(riga))
    }

    @Test
    fun `gli apici dentro un campo si raddoppiano e si rileggono`() {
        val originale = listOf("""dice "ciao"""", "b")
        val riga = Csv.componi(originale)

        assertEquals(originale, Csv.dividi(riga))
    }

    @Test
    fun `un campo vuoto resta un campo`() {
        assertEquals(listOf("a", "", "c"), Csv.dividi("a;;c"))
        assertEquals("a;;c", Csv.componi(listOf("a", "", "c")))
        assertEquals(listOf("", ""), Csv.dividi(";"))
    }

    @Test
    fun `i ritorni a capo non entrano in un campo`() {
        assertEquals("una nota su due righe", Csv.testo("una nota su\ndue righe"))
        assertEquals("con ritorno windows", Csv.testo("con ritorno\r\nwindows"))
        assertEquals("", Csv.testo(null))
        assertEquals("senza spazi ai bordi", Csv.testo("  senza spazi ai bordi  "))
    }

    @Test
    fun `una nota multiriga non puo produrre due record`() {
        val riga = Csv.componi(listOf("id1", "prima\nseconda"))

        assertFalse(riga.contains('\n'))
        assertEquals(2, Csv.dividi(riga).size)
    }

    @Test
    fun `il booleano si scrive si e si legge tollerante`() {
        assertEquals("si", Csv.booleano(true))
        assertEquals("no", Csv.booleano(false))

        assertTrue(Csv.leggiBooleano("si"))
        assertTrue(Csv.leggiBooleano("Sì"))
        assertTrue(Csv.leggiBooleano("TRUE"))
        assertTrue(Csv.leggiBooleano("1"))
        assertFalse(Csv.leggiBooleano("no"))
        assertFalse(Csv.leggiBooleano(""))
        assertFalse(Csv.leggiBooleano(null))
    }
}
