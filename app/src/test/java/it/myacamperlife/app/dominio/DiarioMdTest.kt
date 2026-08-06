package it.myacamperlife.app.dominio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiarioMdTest {

    private val cinque = LocalDate.of(2026, 8, 5)
    private val sei = LocalDate.of(2026, 8, 6)
    private val sette = LocalDate.of(2026, 8, 7)

    private fun sezione(giorno: LocalDate, testo: String) =
        "## $giorno — giornata\n\n- $testo\n"

    @Test
    fun `su un diario vuoto la prima giornata diventa tutto il file`() {
        val risultato = DiarioMd.sostituisci("", sei, sezione(sei, "prima riga"))

        assertEquals("## 2026-08-06 — giornata\n\n- prima riga\n", risultato)
    }

    @Test
    fun `il preambolo resta in testa`() {
        val diario = "# Toscana, agosto 2026\n"

        val risultato = DiarioMd.sostituisci(diario, sei, sezione(sei, "arrivo"))

        assertEquals(
            "# Toscana, agosto 2026\n\n## 2026-08-06 — giornata\n\n- arrivo\n",
            risultato,
        )
    }

    @Test
    fun `una giornata nuova e piu recente va in fondo`() {
        val diario = "# Titolo\n\n" + sezione(cinque, "primo giorno")

        val risultato = DiarioMd.sostituisci(diario, sei, sezione(sei, "secondo giorno"))

        assertEquals(listOf(cinque, sei), DiarioMd.giorni(risultato))
    }

    @Test
    fun `una giornata vecchia scritta dopo si infila al posto giusto`() {
        // Il caso che rende necessario l'ordinamento: si registra oggi, poi si
        // rigenera il diario di ieri.
        val diario = sezione(sei, "oggi") + "\n" + sezione(sette, "domani")

        val risultato = DiarioMd.sostituisci(diario, cinque, sezione(cinque, "ieri"))

        assertEquals(listOf(cinque, sei, sette), DiarioMd.giorni(risultato))
    }

    @Test
    fun `rigenerare una giornata riscrive solo la sua sezione`() {
        val diario = sezione(cinque, "cinque") + "\n" + sezione(sei, "vecchio") + "\n" + sezione(sette, "sette")

        val risultato = DiarioMd.sostituisci(diario, sei, sezione(sei, "nuovo"))

        assertEquals(listOf(cinque, sei, sette), DiarioMd.giorni(risultato))
        assertEquals("## 2026-08-06 — giornata\n\n- nuovo", DiarioMd.sezione(risultato, sei))
        assertEquals("## 2026-08-05 — giornata\n\n- cinque", DiarioMd.sezione(risultato, cinque))
        assertEquals("## 2026-08-07 — giornata\n\n- sette", DiarioMd.sezione(risultato, sette))
    }

    @Test
    fun `rigenerare due volte di seguito non cambia il file`() {
        val prima = DiarioMd.sostituisci("# Titolo\n", sei, sezione(sei, "a"))
        val seconda = DiarioMd.sostituisci(prima, sei, sezione(sei, "a"))

        assertEquals(prima, seconda)
    }

    @Test
    fun `una sezione scritta a mano non viene interpretata come giornata`() {
        val diario = "# Titolo\n\n## Note sparse\n\nqualcosa a mano\n"

        val risultato = DiarioMd.sostituisci(diario, sei, sezione(sei, "arrivo"))

        assertEquals(listOf(sei), DiarioMd.giorni(risultato))
        assertEquals(true, risultato.contains("## Note sparse"))
        assertEquals(true, risultato.contains("qualcosa a mano"))
    }

    @Test
    fun `una giornata che non c'e non ha sezione`() {
        assertNull(DiarioMd.sezione(sezione(sei, "x"), sette))
    }

    @Test
    fun `il file finisce sempre con un solo ritorno a capo`() {
        val risultato = DiarioMd.sostituisci("", sei, sezione(sei, "x") + "\n\n\n")

        assertEquals(true, risultato.endsWith("- x\n"))
        assertEquals(false, risultato.endsWith("\n\n"))
    }
}
