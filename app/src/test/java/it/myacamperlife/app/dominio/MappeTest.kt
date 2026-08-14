package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gli indirizzi delle mappe.
 *
 * Sembrano due concatenazioni di stringhe, e sono la ragione per cui questa
 * classe esiste: una virgola decimale al posto del punto o uno spazio non
 * codificato mandano l'utente da un'altra parte **senza dire niente** — nessun
 * errore, nessun messaggio, solo un posto sbagliato.
 */
class MappeTest {

    @Test
    fun `l'indirizzo geo porta le coordinate due volte e il nome fra parentesi`() {
        assertEquals(
            "geo:43.532000,11.887000?q=43.532000,11.887000(Area%20Lido)",
            Mappe.geo(43.532, 11.887, "Area Lido"),
        )
    }

    @Test
    fun `i gradi usano il punto decimale anche con la lingua italiana`() {
        val prima = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.ITALIAN)
        try {
            // Con la virgola l'indirizzo avrebbe quattro numeri invece di due, e
            // la mappa si aprirebbe in mezzo all'oceano.
            assertTrue(Mappe.geo(43.5, 11.8, "x").contains("geo:43.500000,11.800000"))
        } finally {
            java.util.Locale.setDefault(prima)
        }
    }

    @Test
    fun `Google cerca per nome col paese accanto`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Chiesa%20di%20San%20Giacomo%2C%20Rothenburg",
            Mappe.google(49.377, 10.179, "Chiesa di San Giacomo", "Rothenburg"),
        )
    }

    @Test
    fun `senza toponimo resta il solo nome`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Museo%20del%20Natale",
            Mappe.google(49.377, 10.179, "Museo del Natale", null),
        )
    }

    @Test
    fun `senza nome si cercano le coordinate`() {
        // OpenStreetMap ha molti punti senza nome: cercare "Rothenburg" da solo
        // aprirebbe il centro del paese, che non e' il punto che si e' toccato.
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=49.377000%2C10.179000",
            Mappe.google(49.377, 10.179, null, "Rothenburg"),
        )
    }

    @Test
    fun `un nome in bianco vale come nessun nome`() {
        assertTrue(Mappe.google(1.0, 2.0, "   ", "Bolsena").endsWith("query=1.000000%2C2.000000"))
    }

    @Test
    fun `un toponimo in bianco non lascia una virgola sospesa`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Fonte%20Vecchia",
            Mappe.google(1.0, 2.0, "Fonte Vecchia", " "),
        )
    }

    @Test
    fun `gli spazi non diventano piu' dentro un geo`() {
        // URLEncoder scrive '+' per lo spazio: dentro un `geo:` quel piu' resta
        // un piu', e il segnaposto si chiamerebbe "Area+Lido".
        assertTrue(!Mappe.geo(1.0, 2.0, "Area Lido").contains("+"))
        assertTrue(!Mappe.google(1.0, 2.0, "Area Lido").contains("+"))
    }
}
