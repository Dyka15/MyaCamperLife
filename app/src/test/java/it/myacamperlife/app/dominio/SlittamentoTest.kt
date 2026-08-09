package it.myacamperlife.app.dominio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlittamentoTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-10")

    private fun tappa(
        ordine: Int,
        nome: String,
        giorno: String?,
        stato: StatoTappa = StatoTappa.DA_FARE,
    ) = Tappa(
        id = "t$ordine", ordine = ordine, nome = nome,
        lat = 42.0 + ordine, lon = 12.0, giorno = giorno, stato = stato,
    )

    private val itinerario = listOf(
        tappa(1, "Firenze", "2026-08-05", StatoTappa.FATTA),
        tappa(2, "Orvieto", "2026-08-08", StatoTappa.FATTA),
        tappa(3, "Bolsena", "2026-08-09"),
        tappa(4, "Viterbo", "2026-08-10"),
        tappa(5, "Roma", "2026-08-11"),
    )

    // --- misurare -------------------------------------------------------------

    @Test
    fun `arrivare due giorni dopo e' un ritardo di due giorni`() {
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        val misura = Slittamenti.misura(bolsena, itinerario, quando = oggi.plusDays(1), oggi = oggi)!!
        // Bolsena era prevista il 9, si arriva l'11.
        assertEquals(2L, misura.giorni)
        assertTrue(misura.ritardo)
        assertEquals(2L, misura.quanti)
        assertTrue(misura.daChiedere)
    }

    @Test
    fun `arrivare prima e' un anticipo, e si propone comunque`() {
        val roma = itinerario.first { it.nome == "Roma" }
        val misura = Slittamenti.misura(roma, itinerario, quando = oggi.minusDays(1), oggi = oggi)!!
        // Roma era prevista l'11, si arriva il 9.
        assertEquals(-2L, misura.giorni)
        assertTrue(misura.anticipo)
        assertEquals(2L, misura.quanti)
    }

    @Test
    fun `in orario non si chiede niente`() {
        val viterbo = itinerario.first { it.nome == "Viterbo" }
        val misura = Slittamenti.misura(viterbo, itinerario, quando = oggi, oggi = oggi)!!
        assertEquals(0L, misura.giorni)
        assertTrue(!misura.daChiedere)
    }

    @Test
    fun `mezza giornata non e' un ritardo da rimediare`() {
        // La soglia e' il giorno intero: un ritardo di ore si recupera guidando,
        // e proporre di riscrivere l'itinerario per quello sarebbe fastidioso.
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        val misura = Slittamenti.misura(bolsena, itinerario, quando = oggi.minusDays(1), oggi = oggi)!!
        assertEquals(0L, misura.giorni)
        assertTrue(!misura.daChiedere)
    }

    @Test
    fun `senza una data non c'e' un programma da cui essere in ritardo`() {
        val senzaData = tappa(3, "Bolsena", null)
        assertNull(Slittamenti.misura(senzaData, itinerario, quando = oggi, oggi = oggi))
    }

    @Test
    fun `l'ultima tappa non ha niente da spostare, quindi non si chiede`() {
        val roma = itinerario.first { it.nome == "Roma" }
        // Roma era prevista l'11, si arriva il 13: due giorni di ritardo.
        val misura = Slittamenti.misura(roma, itinerario, quando = oggi.plusDays(3), oggi = oggi)!!
        assertEquals(2L, misura.giorni)
        assertEquals(0, misura.daFare)
        // Il ritardo e' un fatto, ma non ha conseguenze: chiedere sarebbe rumore.
        assertTrue(!misura.daChiedere)
    }

    // --- spostare -------------------------------------------------------------

    @Test
    fun `slittare sposta solo le tappe che vengono dopo`() {
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(itinerario, bolsena, giorni = 2, oggi = oggi)

        assertEquals(listOf("Viterbo", "Roma"), cambiate.map { it.nome })
        assertEquals("2026-08-12", cambiate.first { it.nome == "Viterbo" }.giorno)
        assertEquals("2026-08-13", cambiate.first { it.nome == "Roma" }.giorno)
    }

    @Test
    fun `le tappe gia' fatte non si toccano, perche' sono storia`() {
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(itinerario, bolsena, giorni = 2, oggi = oggi)
        assertTrue(cambiate.none { it.nome == "Firenze" || it.nome == "Orvieto" })
    }

    @Test
    fun `una tappa saltata non si sposta`() {
        val con = itinerario.map {
            if (it.nome == "Viterbo") it.copy(stato = StatoTappa.SALTATA) else it
        }
        val bolsena = con.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(con, bolsena, giorni = 1, oggi = oggi)
        assertEquals(listOf("Roma"), cambiate.map { it.nome })
    }

    @Test
    fun `un anticipo sposta indietro`() {
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(itinerario, bolsena, giorni = -1, oggi = oggi)
        assertEquals("2026-08-09", cambiate.first { it.nome == "Viterbo" }.giorno)
    }

    @Test
    fun `slittare di zero non cambia niente`() {
        val bolsena = itinerario.first { it.nome == "Bolsena" }
        assertTrue(Slittamenti.slitta(itinerario, bolsena, giorni = 0, oggi = oggi).isEmpty())
    }

    @Test
    fun `una tappa senza data leggibile resta dov'e'`() {
        val con = itinerario.map {
            if (it.nome == "Viterbo") it.copy(giorno = "quando capita") else it
        }
        val bolsena = con.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(con, bolsena, giorni = 2, oggi = oggi)
        // Non si sa da dove partire, e inventare un punto di partenza e' peggio.
        assertEquals(listOf("Roma"), cambiate.map { it.nome })
    }

    @Test
    fun `una data scritta in forma libera diventa ISO`() {
        val con = itinerario.map {
            if (it.nome == "Viterbo") it.copy(giorno = "10/08/2026") else it
        }
        val bolsena = con.first { it.nome == "Bolsena" }
        val cambiate = Slittamenti.slitta(con, bolsena, giorni = 1, oggi = oggi)
        // La forma originale si perde, e la data diventa piu' precisa: e' una
        // perdita accettabile, e l'alternativa sarebbe indovinare ogni formato.
        assertEquals("2026-08-11", cambiate.first { it.nome == "Viterbo" }.giorno)
    }
}

class GiorniDelViaggioTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-08")

    private fun tappa(ordine: Int, nome: String, giorno: String?) = Tappa(
        id = "t$ordine", ordine = ordine, nome = nome,
        lat = 42.0 + ordine, lon = 12.0, giorno = giorno,
    )

    @Test
    fun `un giorno senza tappe non spariscono, dice dove si resta`() {
        // L'itinerario va a Bolsena il 9 e a Roma l'11: il 10 si sta a Bolsena, e
        // il 10 e' un giorno di viaggio come gli altri.
        val giorni = GiorniDelViaggio.giorni(
            tappe = listOf(tappa(1, "Bolsena", "2026-08-09"), tappa(2, "Roma", "2026-08-11")),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-11"),
            oggi = oggi,
        )

        assertEquals(3, giorni.size)
        assertEquals(listOf("Bolsena"), giorni[0].nomi)
        assertTrue(giorni[1].fermo)
        assertEquals("Bolsena", giorni[1].restaA)
        assertEquals(listOf("Roma"), giorni[2].nomi)
    }

    @Test
    fun `dove ti trovi davvero vince sull'itinerario`() {
        // Il check-in e' un fatto, l'ultima tappa in programma e' un'ipotesi.
        val giorni = GiorniDelViaggio.giorni(
            tappe = listOf(tappa(1, "Bolsena", "2026-08-08")),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-09"),
            oggi = oggi,
            dove = "Viterbo",
        )
        assertEquals("Viterbo", giorni.single().restaA)
    }

    @Test
    fun `il primo giorno fermo sa dove sei da prima della finestra`() {
        val giorni = GiorniDelViaggio.giorni(
            tappe = listOf(tappa(1, "Bolsena", "2026-08-08"), tappa(2, "Roma", "2026-08-11")),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-10"),
            oggi = oggi,
        )
        assertEquals(2, giorni.size)
        assertEquals("Bolsena", giorni[0].restaA)
        assertEquals("Bolsena", giorni[1].restaA)
    }

    @Test
    fun `senza niente da cui partire il giorno fermo non inventa un posto`() {
        val giorni = GiorniDelViaggio.giorni(
            tappe = emptyList(),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-09"),
            oggi = oggi,
        )
        assertEquals(1, giorni.size)
        assertTrue(giorni.single().fermo)
        assertNull(giorni.single().restaA)
    }

    @Test
    fun `il ripiego dice dove sei quando l'itinerario non lo sa`() {
        val giorni = GiorniDelViaggio.giorni(
            tappe = emptyList(),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-09"),
            oggi = oggi,
            dove = "Orvieto",
        )
        assertEquals("Orvieto", giorni.single().restaA)
    }

    @Test
    fun `due tappe nello stesso giorno restano insieme e in ordine`() {
        val giorni = GiorniDelViaggio.giorni(
            tappe = listOf(tappa(2, "Bolsena", "2026-08-09"), tappa(1, "Orvieto", "2026-08-09")),
            da = LocalDate.parse("2026-08-09"),
            a = LocalDate.parse("2026-08-09"),
            oggi = oggi,
        )
        assertEquals(listOf("Orvieto", "Bolsena"), giorni.single().nomi)
    }

    @Test
    fun `una finestra rovesciata non da' giorni`() {
        assertTrue(
            GiorniDelViaggio.giorni(
                tappe = emptyList(),
                da = LocalDate.parse("2026-08-10"),
                a = LocalDate.parse("2026-08-09"),
                oggi = oggi,
            ).isEmpty(),
        )
    }

    // --- i buchi dell'itinerario ----------------------------------------------

    @Test
    fun `un giorno saltato in mezzo si segnala`() {
        val buchi = GiorniDelViaggio.buchi(
            listOf(
                tappa(1, "Orvieto", "2026-08-08"),
                tappa(2, "Roma", "2026-08-11"),
            ),
            oggi,
        )
        assertEquals(
            listOf(LocalDate.parse("2026-08-09"), LocalDate.parse("2026-08-10")),
            buchi,
        )
    }

    @Test
    fun `un itinerario senza buchi non segnala niente`() {
        val buchi = GiorniDelViaggio.buchi(
            listOf(
                tappa(1, "Orvieto", "2026-08-08"),
                tappa(2, "Bolsena", "2026-08-09"),
                tappa(3, "Roma", "2026-08-10"),
            ),
            oggi,
        )
        assertTrue(buchi.isEmpty())
    }

    @Test
    fun `due tappe nello stesso giorno non fanno un buco`() {
        val buchi = GiorniDelViaggio.buchi(
            listOf(
                tappa(1, "Orvieto", "2026-08-08"),
                tappa(2, "Bolsena", "2026-08-08"),
                tappa(3, "Roma", "2026-08-09"),
            ),
            oggi,
        )
        assertTrue(buchi.isEmpty())
    }

    @Test
    fun `una tappa sola, o nessuna data, non hanno buchi`() {
        assertTrue(GiorniDelViaggio.buchi(listOf(tappa(1, "Orvieto", "2026-08-08")), oggi).isEmpty())
        assertTrue(GiorniDelViaggio.buchi(listOf(tappa(1, "Orvieto", null)), oggi).isEmpty())
        assertTrue(GiorniDelViaggio.buchi(emptyList(), oggi).isEmpty())
    }
}

class CampiExtraTest {

    @Test
    fun `i campi in piu' fanno un giro completo`() {
        val campi = listOf("orari" to "9-18", "telefono" to "0763 341772")
        val cella = CampiExtra.scrivi(campi)
        assertEquals(campi, CampiExtra.leggi(cella))
    }

    @Test
    fun `l'ordine del file si conserva`() {
        val campi = listOf("zeta" to "1", "alfa" to "2", "mezzo" to "3")
        // L'ordine in cui chi ha scritto l'itinerario ha messo le cose e'
        // un'informazione: la prima riga e' probabilmente la piu' importante.
        assertEquals(campi.map { it.first }, CampiExtra.leggi(CampiExtra.scrivi(campi)).map { it.first })
    }

    @Test
    fun `niente campi da' una cella vuota, non due graffe`() {
        assertEquals("", CampiExtra.scrivi(emptyList()))
        assertTrue(CampiExtra.leggi("").isEmpty())
        assertTrue(CampiExtra.leggi(null).isEmpty())
    }

    @Test
    fun `una cella sta su una riga sola anche con un valore a capo`() {
        val cella = CampiExtra.scrivi(listOf("note" to "prima riga\nseconda riga"))
        // L'invariante del formato: una riga fisica e' un record.
        assertTrue(cella, !cella.contains('\n'))
        assertEquals("prima riga\nseconda riga", CampiExtra.leggi(cella).single().second)
    }

    @Test
    fun `un valore col punto e virgola non rompe niente`() {
        val cella = CampiExtra.scrivi(listOf("nota" to "a; b; c"))
        assertEquals("a; b; c", CampiExtra.leggi(cella).single().second)
    }

    @Test
    fun `una cella rovinata a mano non impedisce di aprire l'itinerario`() {
        assertTrue(CampiExtra.leggi("{questo non e' json").isEmpty())
        assertTrue(CampiExtra.leggi("boh").isEmpty())
    }

    @Test
    fun `un campo si mostra col nome che aveva nel file`() {
        assertEquals("orari: 9-18", CampiExtra.riga("orari" to "9-18"))
    }
}
