package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronacaTest {

    private fun ora(h: Int, m: Int) = OffsetDateTime.parse("2026-08-06T%02d:%02d:00+02:00".format(h, m))
    private val giorno = LocalDate.of(2026, 8, 6)

    @Test
    fun `l'intestazione porta la data ISO davanti e l'italiano dopo`() {
        val testa = Cronaca.intestazione(giorno, "Orvieto")

        assertEquals("## 2026-08-06 — giovedì 6 agosto 2026, Orvieto", testa)
    }

    @Test
    fun `senza luogo l'intestazione non ha la virgola appesa`() {
        assertEquals("## 2026-08-06 — giovedì 6 agosto 2026", Cronaca.intestazione(giorno))
        assertEquals("## 2026-08-06 — giovedì 6 agosto 2026", Cronaca.intestazione(giorno, "  "))
    }

    @Test
    fun `le voci escono in ordine di ora anche se arrivano mescolate`() {
        val sezione = Cronaca.sezione(
            giorno,
            listOf(
                Voce(ora(18, 5), Genere.NOTA, "comprato il pane"),
                Voce(ora(14, 12), Genere.ARRIVO, "Orvieto"),
                Voce(ora(15, 40), Genere.FOTO, "Duomo", allegato = "foto_20260806_154000_Orvieto.jpg"),
            ),
            luogo = "Orvieto",
        )

        val righe = sezione.lines().filter { it.startsWith("- ") }
        assertEquals(
            listOf(
                "- 14:12 · arrivo a Orvieto",
                "- 15:40 · foto: Duomo (`foto_20260806_154000_Orvieto.jpg`)",
                "- 18:05 · comprato il pane",
            ),
            righe,
        )
    }

    @Test
    fun `una giornata senza voci lo dice invece di restare muta`() {
        val sezione = Cronaca.sezione(giorno, emptyList())

        assertTrue(sezione.contains("Nessun evento registrato"))
        assertTrue(sezione.startsWith("## 2026-08-06"))
    }

    @Test
    fun `una foto senza didascalia mostra il file`() {
        val sezione = Cronaca.sezione(
            giorno,
            listOf(Voce(ora(9, 0), Genere.FOTO, "", allegato = "foto_20260806_090000.jpg")),
        )

        assertTrue(sezione.contains("- 09:00 · foto `foto_20260806_090000.jpg`"))
    }

    @Test
    fun `una posizione senza descrizione non produce una riga vuota`() {
        val sezione = Cronaca.sezione(giorno, listOf(Voce(ora(11, 30), Genere.POSIZIONE, "")))

        assertTrue(sezione.contains("- 11:30 · posizione registrata"))
    }
}
