package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarburanteTest {

    @Test
    fun `i litri sono l'importo diviso il prezzo al litro`() {
        // Quello che c'e' scritto sullo scontrino: 107,16 euro a 1,719 il litro.
        assertEquals(62.34, Carburante.litri(107.16, 1.719)!!, 0.01)
        assertEquals(60.0, Carburante.litri(102.0, 1.7)!!, 1e-9)
    }

    @Test
    fun `senza uno dei due numeri i litri non si calcolano`() {
        assertNull(Carburante.litri(null, 1.719))
        assertNull(Carburante.litri(107.16, null))
        assertNull(Carburante.litri(null, null))
    }

    @Test
    fun `un prezzo a zero non da infiniti litri`() {
        // Un rifornimento da infinito litri manderebbe il consumo a zero senza
        // che niente segnali il problema.
        assertNull(Carburante.litri(107.16, 0.0))
        assertNull(Carburante.litri(107.16, -1.7))
    }

    @Test
    fun `un importo a zero non e' un rifornimento`() {
        assertNull(Carburante.litri(0.0, 1.719))
        assertNull(Carburante.litri(-10.0, 1.719))
    }

    @Test
    fun `il prezzo si ricava da importo e litri, per i file vecchi`() {
        assertEquals(1.72, Carburante.prezzo(107.5, 62.5)!!, 1e-9)
        assertNull(Carburante.prezzo(107.5, 0.0))
        assertNull(Carburante.prezzo(null, 62.5))
    }

    @Test
    fun `i tre numeri tornano fra loro`() {
        val euro = 89.4
        val prezzo = 1.49
        val litri = Carburante.litri(euro, prezzo)!!
        assertEquals(prezzo, Carburante.prezzo(euro, litri)!!, 1e-9)
    }
}
