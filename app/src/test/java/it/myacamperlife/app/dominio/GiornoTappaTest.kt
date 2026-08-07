package it.myacamperlife.app.dominio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiornoTappaTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-06")

    private fun leggi(testo: String?, riferimento: LocalDate = oggi) =
        GiornoTappa.leggi(testo, riferimento)

    private fun data(iso: String) = LocalDate.parse(iso)

    // --- le forme complete ----------------------------------------------------

    @Test
    fun `la forma ISO e quella che scrive l'itinerario`() {
        assertEquals(data("2026-08-06"), leggi("2026-08-06"))
        assertEquals(data("2026-08-06"), leggi("2026-8-6"))
    }

    @Test
    fun `la forma italiana ha il giorno prima del mese`() {
        assertEquals(data("2026-08-06"), leggi("06/08/2026"))
        assertEquals(data("2026-08-06"), leggi("6.8.2026"))
        assertEquals(data("2026-08-06"), leggi("6-8-26"))
    }

    @Test
    fun `il mese scritto per esteso, con l'anno`() {
        assertEquals(data("2026-08-06"), leggi("6 agosto 2026"))
        assertEquals(data("2027-01-06"), leggi("6 gennaio 2027"))
    }

    // --- le forme parziali ----------------------------------------------------

    @Test
    fun `il mese senza anno prende l'anno in corso`() {
        assertEquals(data("2026-08-06"), leggi("6 agosto"))
        assertEquals(data("2026-08-06"), leggi("gio 6 agosto"))
        assertEquals(data("2026-08-06"), leggi("6 ago"))
    }

    @Test
    fun `un mese gia passato da un pezzo e dell'anno prossimo`() {
        // Letto in agosto, "6 gennaio" e' il gennaio che viene.
        assertEquals(data("2027-01-06"), leggi("6 gennaio"))
    }

    @Test
    fun `il mese scorso resta il mese scorso, per la tappa non spuntata`() {
        assertEquals(data("2026-07-30"), leggi("30 luglio"))
    }

    @Test
    fun `il giorno da solo sta nel mese in corso`() {
        assertEquals(data("2026-08-08"), leggi("8"))
        assertEquals(data("2026-08-08"), leggi("sab 8"))
        assertEquals(data("2026-08-08"), leggi("sabato 8"))
    }

    @Test
    fun `un giorno gia passato nel mese passa al mese dopo`() {
        // Il 3, letto il 28 agosto, e' il 3 settembre.
        assertEquals(data("2026-09-03"), leggi("mer 3", data("2026-08-28")))
    }

    @Test
    fun `il giorno di ieri resta ieri`() {
        assertEquals(data("2026-08-05"), leggi("mer 5"))
    }

    @Test
    fun `il passaggio d'anno funziona anche col giorno da solo`() {
        assertEquals(data("2027-01-02"), leggi("2", data("2026-12-28")))
    }

    // --- quello che non si legge ----------------------------------------------

    @Test
    fun `un campo vuoto o assente non da una data`() {
        assertNull(leggi(null))
        assertNull(leggi(""))
        assertNull(leggi("   "))
    }

    @Test
    fun `un giorno d'ordine non si indovina`() {
        // "Giorno 2" e' il secondo giorno di viaggio, non il 2 del mese:
        // senza sapere quando parti, datarlo metterebbe la tappa nel giorno
        // sbagliato.
        assertNull(leggi("giorno 2"))
        assertNull(leggi("Giorno 1"))
        assertNull(leggi("day 3"))
        assertNull(leggi("tappa 4"))
    }

    @Test
    fun `un giorno della settimana senza numero non basta`() {
        assertNull(leggi("mercoledi"))
        assertNull(leggi("nel fine settimana"))
    }

    @Test
    fun `una data impossibile non da una data`() {
        assertNull(leggi("2026-02-31"))
        assertNull(leggi("31/02/2026"))
    }

    @Test
    fun `un numero fuori dai giorni del mese non e una data`() {
        assertNull(leggi("2026"))
    }

    // --- il raggruppamento ----------------------------------------------------

    @Test
    fun `le tappe si raggruppano per data, in ordine di calendario`() {
        val (perGiorno, senza) = GiornoTappa.perGiorno(
            listOf(
                tappa(1, "Roma", "2026-08-08"),
                tappa(2, "Orvieto", "2026-08-06"),
                tappa(3, "Bolsena", "2026-08-06"),
                tappa(4, "Non si sa", null),
            ),
            oggi,
        )

        assertEquals(listOf(data("2026-08-06"), data("2026-08-08")), perGiorno.keys.toList())
        assertEquals(listOf("Orvieto", "Bolsena"), perGiorno[data("2026-08-06")]!!.map { it.nome })
        assertEquals(listOf("Non si sa"), senza.map { it.nome })
    }

    private fun tappa(ordine: Int, nome: String, giorno: String?) = Tappa(
        id = "t$ordine",
        ordine = ordine,
        nome = nome,
        lat = 42.0,
        lon = 12.0,
        giorno = giorno,
    )
}
