package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetturaScontrinoTest {

    // --- i numeri di una riga ------------------------------------------------

    @Test
    fun `un importo ha due decimali, con la virgola o col punto`() {
        assertEquals(listOf(12.50), LetturaScontrino.importi("TOTALE 12,50"))
        assertEquals(listOf(12.50), LetturaScontrino.importi("TOTAL 12.50"))
    }

    @Test
    fun `le migliaia si separano col punto, con lo spazio o senza niente`() {
        assertEquals(listOf(1234.56), LetturaScontrino.importi("1.234,56"))
        assertEquals(listOf(1234.56), LetturaScontrino.importi("1 234,56"))
        assertEquals(listOf(1234.56), LetturaScontrino.importi("1234,56"))
        // Lo spazio insecabile, quello che stampano davvero le casse.
        assertEquals(listOf(1234.56), LetturaScontrino.importi("1\u00A0234,56"))
    }

    @Test
    fun `il simbolo dell euro non disturba, da che parte sia`() {
        assertEquals(listOf(12.50), LetturaScontrino.importi("€ 12,50"))
        assertEquals(listOf(12.50), LetturaScontrino.importi("€12,50"))
        assertEquals(listOf(12.50), LetturaScontrino.importi("12,50 €"))
    }

    @Test
    fun `una data non e un importo`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("06.08.2026 14:12"))
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("06/08/2026"))
    }

    @Test
    fun `una quantita con un decimale solo non e un importo`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("GASOLIO 45,7 L"))
    }

    @Test
    fun `una percentuale e un codice non sono importi`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("IVA 22%"))
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("P.IVA 01234567890"))
    }

    @Test
    fun `un numero attaccato a una lettera non e un importo`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("SCONTRINO N4,50"))
    }

    @Test
    fun `un importo assurdo viene scartato`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("999999999,00"))
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("0,00"))
    }

    // --- quale numero e il totale --------------------------------------------

    @Test
    fun `si prende il numero della riga del totale, non il piu grande`() {
        val importo = LetturaScontrino.importo(
            """
            AREA SOSTA IL CIPRESSO
            NOTTE                18,00
            ELETTRICITA           3,50
            CAUZIONE            100,00
            TOTALE               21,50
            """.trimIndent(),
        )
        assertEquals(21.50, importo!!, 1e-9)
    }

    @Test
    fun `l IVA sulla stessa riga del totale non confonde`() {
        assertEquals(
            12.50,
            LetturaScontrino.importo("TOTALE EURO 12,50 DI CUI IVA 2,25")!!,
            1e-9,
        )
    }

    @Test
    fun `il subtotale non e il totale`() {
        val importo = LetturaScontrino.importo(
            """
            SUBTOTALE      30,00
            SCONTO          5,00
            TOTALE         25,00
            """.trimIndent(),
        )
        assertEquals(25.00, importo!!, 1e-9)
    }

    @Test
    fun `il contante dato al cassiere non e il totale`() {
        val importo = LetturaScontrino.importo(
            """
            TOTALE COMPLESSIVO   17,30
            CONTANTE             20,00
            RESTO                 2,70
            """.trimIndent(),
        )
        assertEquals(17.30, importo!!, 1e-9)
    }

    @Test
    fun `se il totale e andato a capo si legge la riga dopo`() {
        val importo = LetturaScontrino.importo(
            """
            TOTALE COMPLESSIVO
            42,00
            """.trimIndent(),
        )
        assertEquals(42.00, importo!!, 1e-9)
    }

    @Test
    fun `si prende l ultima riga che parla di totale`() {
        // Le stampanti fiscali ripetono il totale; l'ultimo e' quello buono.
        val importo = LetturaScontrino.importo(
            """
            TOTALE                9,00
            ARROTONDAMENTO        0,05
            TOTALE DA PAGARE      9,05
            """.trimIndent(),
        )
        assertEquals(9.05, importo!!, 1e-9)
    }

    @Test
    fun `senza la parola totale si prende il numero piu alto`() {
        val importo = LetturaScontrino.importo(
            """
            CAFFE      1,20
            CORNETTO   1,50
            2,70
            """.trimIndent(),
        )
        assertEquals(2.70, importo!!, 1e-9)
    }

    @Test
    fun `uno scontrino senza numeri non da un importo`() {
        assertNull(LetturaScontrino.importo("GRAZIE E ARRIVEDERCI"))
        assertNull(LetturaScontrino.importo(""))
    }

    @Test
    fun `uno scontrino di distributore vero`() {
        val importo = LetturaScontrino.importo(
            """
            ENI STATION
            VIA CASSIA 214 - ORVIETO
            06/08/2026 08:41
            GASOLIO SELF
            LITRI            62,30
            PREZZO/L          1,719
            IMPORTO         107,09
            """.trimIndent(),
        )
        assertEquals(107.09, importo!!, 1e-9)
    }

    @Test
    fun `lo scontrino di un supermercato vero`() {
        val importo = LetturaScontrino.importo(
            """
            CONAD CITY
            PANE                   1,80
            ACQUA 6X1,5L           2,94
            PROSCIUTTO CRUDO       6,45
            SUBTOTALE             11,19
            SCONTO SOCI            0,50
            TOTALE COMPLESSIVO    10,69
            DI CUI IVA 10%         0,97
            PAGAMENTO CONTANTI    20,00
            RESTO                  9,31
            """.trimIndent(),
        )
        assertEquals(10.69, importo!!, 1e-9)
    }

    @Test
    fun `il prezzo al litro con tre decimali non viene letto affatto`() {
        assertEquals(emptyList<Double>(), LetturaScontrino.importi("PREZZO/L 1,719"))
    }
}
