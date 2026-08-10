package it.myacamperlife.app.dominio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il programma giorno per giorno, letto dal corpo Markdown dell'itinerario.
 *
 * Il documento di prova ha la forma di un itinerario vero: intestazioni
 * `## 6/8 — Giovedì`, un percorso in `###`, un corpo con orari e durate, i
 * separatori `---`, e in fondo delle sezioni che **non sono giorni** —
 * riepiloghi, consigli, il blocco della mappa. Quelle ultime sono la ragione per
 * cui il riconoscimento passa dalla data e non da un elenco di titoli.
 */
class ProgrammaTest {

    private val riferimento: LocalDate = LocalDate.parse("2026-08-06")

    private val documento = """
        # 🚐 Baviera, Romantic Road, Bratislava e Istria

        **Periodo:** 6 agosto 2026 – 23 agosto 2026
        **Totale Km Stimati:** ~2.645 km

        ---

        ## 6/8 — Giovedì
        ### Lonigo → Garmisch-Partenkirchen → Eibsee

        🚐 **Spostamento: Lonigo → Garmisch-Partenkirchen**
        Partenza: 08:00 | Arrivo: 12:00 | 300 km | 4 ore

        ⚠️ Vignette austriaca obbligatoria.

        🌙 **Notte: Camping Resort Zugspitze**

        ---

        ## 7/8 — Venerdì
        ### EIBSEE — Giornata intera 🚶 (giro del lago)

        📷 **Mattina: Giro del Lago di Eibsee**
        Orario: 09:30 | Durata consigliata: 2,5 ore

        ---

        ## 10/8 — Lunedì
        ### MONACO — Giornata intera

        📷 **Mattina: Marienplatz e Frauenkirche**
        Orario: 09:30 | Durata consigliata: 2 ore
        Il Municipio Nuovo neogotico domina la piazza con il suo Glockenspiel.

        🌙 **Notte: Campingplatz München-Thalkirchen** *(seconda notte)*

        ---

        ## RIEPILOGO KM GIORNALIERI

        | Giorno | Km |
        |---|---|
        | 6/8 | 310 |

        ---

        ## BLOCCO MAPPA

        ```json
        { "waypoints": [] }
        ```
    """.trimIndent()

    private val sezioni = Programmi.sezioni(documento, riferimento)

    // --- quali sezioni sono giornate -------------------------------------------

    @Test
    fun `le giornate si riconoscono dalla data nell'intestazione`() {
        assertEquals(
            listOf(
                LocalDate.parse("2026-08-06"),
                LocalDate.parse("2026-08-07"),
                LocalDate.parse("2026-08-10"),
            ),
            sezioni.map { it.giorno },
        )
    }

    @Test
    fun `riepiloghi e blocco mappa non sono giornate`() {
        // **La ragione per cui il riconoscimento passa dalla data**: un elenco di
        // titoli da ignorare invecchierebbe al primo itinerario scritto
        // diversamente.
        assertTrue(sezioni.none { it.etichetta.contains("RIEPILOGO") })
        assertTrue(sezioni.none { it.etichetta.contains("BLOCCO MAPPA") })
    }

    @Test
    fun `il titolo del documento non e' una giornata`() {
        assertTrue(sezioni.none { it.etichetta.contains("Baviera") })
    }

    // --- cosa contiene una giornata --------------------------------------------

    @Test
    fun `il percorso del giorno sta a parte dal testo`() {
        val primo = sezioni.first()
        assertEquals("Lonigo → Garmisch-Partenkirchen → Eibsee", primo.titolo)
        // E non si ripete dentro il corpo: sarebbe rumore.
        assertTrue(primo.testo, !primo.testo.contains("### "))
        assertTrue(primo.testo, !primo.testo.contains("Lonigo → Garmisch-Partenkirchen → Eibsee"))
    }

    @Test
    fun `il testo arriva per intero, capoversi compresi`() {
        val monaco = Programmi.per(sezioni, LocalDate.parse("2026-08-10"))!!
        assertTrue(monaco.testo, monaco.testo.contains("Marienplatz"))
        assertTrue(monaco.testo, monaco.testo.contains("Orario: 09:30"))
        assertTrue(monaco.testo, monaco.testo.contains("Glockenspiel"))
        // Fino all'ultima riga della giornata: e' quella che dice dove si dorme.
        assertTrue(monaco.testo, monaco.testo.contains("Thalkirchen"))
        // E la struttura resta: schiacciare i capoversi trasformerebbe un
        // programma leggibile in un muro di parole.
        assertTrue(monaco.testo, monaco.testo.contains("\n"))
    }

    @Test
    fun `i separatori del Markdown non entrano nel testo`() {
        sezioni.forEach { sezione ->
            assertTrue(sezione.testo, sezione.testo.lines().none { it.trim() == "---" })
        }
    }

    @Test
    fun `il testo di una giornata non sconfina in quella dopo`() {
        val primo = sezioni.first()
        assertTrue(primo.testo, !primo.testo.contains("Eibsee stupisce"))
        assertTrue(primo.testo, !primo.testo.contains("Giro del Lago"))
    }

    @Test
    fun `l'etichetta resta come l'ha scritta chi viaggia`() {
        assertEquals("6/8 — Giovedì", sezioni.first().etichetta)
    }

    // --- trovare la giornata di una tappa --------------------------------------

    @Test
    fun `piu' tappe dello stesso giorno trovano lo stesso programma`() {
        // Il 6 agosto si passa da Lonigo, Garmisch e l'Eibsee: quel testo
        // racconta la giornata, non il singolo punto sulla mappa.
        val giorno = LocalDate.parse("2026-08-06")
        val primo = Programmi.per(sezioni, giorno)
        val secondo = Programmi.per(sezioni, giorno)
        assertNotNull(primo)
        assertEquals(primo, secondo)
    }

    @Test
    fun `un giorno di cui l'itinerario non parla non ha programma`() {
        assertNull(Programmi.per(sezioni, LocalDate.parse("2026-08-08")))
        assertNull(Programmi.per(sezioni, null))
    }

    // --- prudenza -------------------------------------------------------------

    @Test
    fun `un documento senza giornate non da' sezioni`() {
        assertTrue(Programmi.sezioni("# Solo un titolo\n\nDue righe.", riferimento).isEmpty())
        assertTrue(Programmi.sezioni("", riferimento).isEmpty())
    }

    @Test
    fun `una giornata senza corpo si riconosce come vuota`() {
        val magra = Programmi.sezioni("## 6/8 — Giovedì\n", riferimento).single()
        assertTrue(magra.vuota)
        assertNull(magra.titolo)
    }

    @Test
    fun `una data senza anno si risolve in avanti`() {
        // `6/8` non ha un anno: si prende quello che viene dal riferimento, come
        // per il campo `giorno` di una tappa.
        assertEquals(
            LocalDate.parse("2027-01-05"),
            Programmi.sezioni("## 5/1 — Martedì\ntesto", LocalDate.parse("2026-12-20"))
                .single().giorno,
        )
    }
}
