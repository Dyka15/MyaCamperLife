package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le righe che raccontano com'e' finito il riepilogo.
 *
 * Sono l'unica cosa che resta di una funzione che gira quando nessuno guarda: se
 * una di queste frasi non dice **cosa fare**, la sera dopo si ricomincia a
 * indovinare.
 */
class EsitoBriefingTest {

    @Test
    fun `mandato dice quale riepilogo era`() {
        assertEquals(
            "mandato: Domani si va a Roth",
            EsitoBriefing.Mandato("Domani si va a Roth").riassunto(),
        )
    }

    @Test
    fun `il permesso mancante porta con se' il rimedio`() {
        // E' il caso in cui la notifica sparisce senza lasciare traccia da
        // Android 13: la riga deve dire dove si concede, perche' chi la legge e'
        // chi deve agire.
        val riga = EsitoBriefing.SenzaPermesso.riassunto()
        assertTrue(riga, riga.contains("permesso"))
        assertTrue(riga, riga.contains("concedilo"))
    }

    @Test
    fun `niente da dire non si confonde con un guasto`() {
        val riga = EsitoBriefing.NienteDaDire.riassunto()
        // Un riepilogo vuoto la sera dell'ultima tappa e' il comportamento
        // giusto: la riga deve leggersi come una spiegazione, non come un errore.
        assertTrue(riga, riga.startsWith("niente da dire"))
    }

    @Test
    fun `ogni esito ha una riga sua, e nessuna e' vuota`() {
        val righe = listOf(
            EsitoBriefing.Mandato("x"),
            EsitoBriefing.NienteDaDire,
            EsitoBriefing.SenzaPermesso,
            EsitoBriefing.SenzaViaggio,
            EsitoBriefing.Spento,
        ).map { it.riassunto() }

        assertTrue(righe.none { it.isBlank() })
        // Distinte: due esiti che si leggono uguali non distinguono niente, ed e'
        // esattamente il difetto che queste righe esistono per riparare.
        assertEquals(righe.size, righe.distinct().size)
    }
}
