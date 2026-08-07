package it.myacamperlife.app.dominio

import java.time.LocalTime
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentoTest {

    /** Giovedì 6 agosto 2026, sera. */
    private val adesso: OffsetDateTime = OffsetDateTime.parse("2026-08-06T21:15:00+02:00")

    // --- l'ora ----------------------------------------------------------------

    @Test
    fun `l'ora si scrive nei tre modi in cui la si digita`() {
        assertEquals(LocalTime.of(21, 30), Momento.orario("21:30"))
        assertEquals(LocalTime.of(21, 30), Momento.orario("21.30"))
        assertEquals(LocalTime.of(21, 30), Momento.orario("2130"))
    }

    @Test
    fun `una cifra sola e' un'ora piena`() {
        assertEquals(LocalTime.of(9, 0), Momento.orario("9"))
        assertEquals(LocalTime.of(21, 0), Momento.orario("21"))
    }

    @Test
    fun `tre cifre sono ore e minuti`() {
        assertEquals(LocalTime.of(9, 30), Momento.orario("930"))
    }

    @Test
    fun `un'ora fuori dal quadrante non e' un'ora`() {
        assertNull(Momento.orario("25:00"))
        assertNull(Momento.orario("12:70"))
        assertNull(Momento.orario(""))
        assertNull(Momento.orario("stasera"))
    }

    // --- l'istante ------------------------------------------------------------

    @Test
    fun `data e ora insieme danno l'istante, nel fuso di chi scrive`() {
        val istante = Momento.leggi("05/08/2026", "18:40", adesso)!!
        assertEquals("2026-08-05T18:40+02:00", istante.toString())
    }

    @Test
    fun `la data si legge in tutte le forme che GiornoTappa conosce`() {
        assertEquals("2026-08-05", Momento.leggi("2026-08-05", "12:00", adesso)!!.toLocalDate().toString())
        assertEquals("2026-08-05", Momento.leggi("5/8/2026", "12:00", adesso)!!.toLocalDate().toString())
        assertEquals("2026-08-05", Momento.leggi("5 agosto", "12:00", adesso)!!.toLocalDate().toString())
        // Il giorno da solo, che e' come si corregge una data precompilata.
        assertEquals("2026-08-05", Momento.leggi("5", "12:00", adesso)!!.toLocalDate().toString())
    }

    @Test
    fun `un'ora illeggibile non fa perdere la registrazione`() {
        // Si tiene l'ora di adesso: sbagliare di qualche ora e' molto meglio
        // che rifiutare la riga.
        val istante = Momento.leggi("05/08/2026", "boh", adesso)!!
        assertEquals(LocalTime.of(21, 15), istante.toLocalTime())
        assertEquals("2026-08-05", istante.toLocalDate().toString())
    }

    @Test
    fun `senza una data leggibile non c'e' istante`() {
        assertNull(Momento.leggi("quando mi pare", "18:00", adesso))
        assertNull(Momento.leggi("", "18:00", adesso))
        assertNull(Momento.leggi(null, "18:00", adesso))
    }

    // --- il futuro ------------------------------------------------------------

    @Test
    fun `una data di domani si riconosce come futura`() {
        val domani = Momento.leggi("07/08/2026", "10:00", adesso)!!
        assertTrue(Momento.oltreOggi(domani, adesso))
    }

    @Test
    fun `oggi non e' futuro, nemmeno a un'ora che deve ancora arrivare`() {
        // Alle 21:15 si registra quello che si e' speso alle 23: capita se
        // l'orologio del telefono e quello del ristorante non concordano, e non
        // e' un motivo per rifiutare la riga.
        val stasera = Momento.leggi("06/08/2026", "23:00", adesso)!!
        assertFalse(Momento.oltreOggi(stasera, adesso))
    }

    @Test
    fun `ieri non e' futuro`() {
        assertFalse(Momento.oltreOggi(Momento.leggi("05/08/2026", "10:00", adesso)!!, adesso))
    }

    // --- come si precompila ---------------------------------------------------

    @Test
    fun `la data e l'ora si scrivono come si leggono`() {
        assertEquals("06/08/2026", Momento.scriviData(adesso))
        assertEquals("21:15", Momento.scriviOra(adesso))
        // Andata e ritorno: quello che l'app scrive, l'app lo rilegge.
        assertEquals(
            adesso.toLocalDate(),
            Momento.leggi(Momento.scriviData(adesso), Momento.scriviOra(adesso), adesso)!!.toLocalDate(),
        )
    }
}
