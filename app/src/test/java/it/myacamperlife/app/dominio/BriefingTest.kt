package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-06")

    private var contatore = 0

    private fun tappa(
        nome: String,
        giorno: String?,
        stato: StatoTappa = StatoTappa.DA_FARE,
        lat: Double = 42.0,
        lon: Double = 12.0,
    ) = Tappa(
        id = "t${contatore++}",
        ordine = contatore,
        nome = nome,
        lat = lat,
        lon = lon,
        giorno = giorno,
        stato = stato,
    )

    private fun autonomia(residui: Double, pieno: Int = 600) = Autonomia(
        kmConUnPieno = pieno,
        kmStimati = pieno - residui,
        ultimoPieno = OffsetDateTime.parse("2026-08-04T10:00:00+02:00"),
        puntiUsati = 4,
    )

    // --- cosa c'e' domani -----------------------------------------------------

    @Test
    fun `domani sono le tappe datate domani`() {
        val briefing = Briefings.componi(
            tappe = listOf(
                tappa("Orvieto", "2026-08-06"),
                tappa("Viterbo", "2026-08-07"),
                tappa("Bolsena", "2026-08-07"),
                tappa("Roma", "2026-08-08"),
            ),
            oggi = oggi,
        )

        assertEquals(listOf("Viterbo", "Bolsena"), briefing.domani!!.nomi)
        assertEquals(LocalDate.parse("2026-08-07"), briefing.domani!!.giorno)
    }

    @Test
    fun `i giorni dopo domani si citano a parte`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07"), tappa("Roma", "2026-08-08")),
            oggi = oggi,
        )
        assertEquals(listOf("Roma"), briefing.poi.single().nomi)
    }

    @Test
    fun `oltre la finestra non si guarda`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Napoli", "2026-08-20")),
            oggi = oggi,
        )
        assertTrue(briefing.giornate.isEmpty())
        assertNull(briefing.domani)
    }

    @Test
    fun `le tappe fatte e quelle saltate non riguardano domani`() {
        val briefing = Briefings.componi(
            tappe = listOf(
                tappa("Viterbo", "2026-08-07", StatoTappa.FATTA),
                tappa("Bolsena", "2026-08-07", StatoTappa.SALTATA),
                tappa("Roma", "2026-08-07"),
            ),
            oggi = oggi,
        )
        assertEquals(listOf("Roma"), briefing.domani!!.nomi)
    }

    @Test
    fun `una tappa senza data si nomina lo stesso`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Un posto qualsiasi", null)),
            oggi = oggi,
        )
        assertEquals(listOf("Un posto qualsiasi"), briefing.senzaData.map { it.nome })
        assertFalse(briefing.vuoto)
    }

    @Test
    fun `una tappa arretrata resta fra le cose da fare`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Firenze", "2026-08-04"), tappa("Viterbo", "2026-08-07")),
            oggi = oggi,
        )
        assertEquals(listOf("Firenze"), briefing.senzaData.map { it.nome })
        assertEquals(listOf("Viterbo"), briefing.domani!!.nomi)
    }

    @Test
    fun `senza tappe e senza avvisi il briefing e vuoto`() {
        assertTrue(Briefings.componi(emptyList(), oggi).vuoto)
    }

    // --- i chilometri ---------------------------------------------------------

    @Test
    fun `i chilometri di domani si contano da dove sei`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07", lat = 42.4207, lon = 12.1077)),
            oggi = oggi,
            da = Coordinate(42.7185, 12.1112), // Orvieto
        )
        // Orvieto–Viterbo in linea d'aria: una trentina di chilometri.
        assertEquals(33.1, briefing.kmDomani!!, 1.0)
    }

    @Test
    fun `i chilometri passano per tutte le tappe del giorno`() {
        val briefing = Briefings.componi(
            tappe = listOf(
                tappa("Bolsena", "2026-08-07", lat = 42.6437, lon = 11.9871),
                tappa("Viterbo", "2026-08-07", lat = 42.4207, lon = 12.1077),
            ),
            oggi = oggi,
            da = Coordinate(42.7185, 12.1112),
        )
        // Orvieto→Bolsena→Viterbo e' piu' lungo di Orvieto→Viterbo diretto.
        assertTrue(briefing.kmDomani!! > 33.1)
    }

    @Test
    fun `senza sapere dove sei si contano solo i tratti fra le tappe`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07")),
            oggi = oggi,
        )
        assertNull(briefing.kmDomani)
    }

    @Test
    fun `senza tappe domani non ci sono chilometri`() {
        assertNull(
            Briefings.componi(
                tappe = listOf(tappa("Roma", "2026-08-08")),
                oggi = oggi,
                da = Coordinate(42.7, 12.1),
            ).kmDomani,
        )
    }

    // --- l'avviso di rifornimento ---------------------------------------------

    @Test
    fun `sotto la riserva si rifornisce comunque`() {
        val briefing = Briefings.componi(
            tappe = emptyList(),
            oggi = oggi,
            autonomia = autonomia(residui = 50.0),
        )
        assertTrue(briefing.rifornire)
        // Un briefing con un avviso non e' vuoto, anche senza tappe.
        assertFalse(briefing.vuoto)
    }

    @Test
    fun `con autonomia larga e pochi chilometri non si avvisa`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07", lat = 42.4207, lon = 12.1077)),
            oggi = oggi,
            autonomia = autonomia(residui = 400.0),
            da = Coordinate(42.7185, 12.1112),
        )
        assertFalse(briefing.rifornire)
    }

    @Test
    fun `il margine fa scattare l'avviso prima che i conti tornino appena`() {
        // 100 km stimati, 120 di autonomia: i conti tornerebbero, ma le
        // distanze sono in linea d'aria e l'autonomia e' ottimista.
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Lontano", "2026-08-07", lat = 43.618, lon = 12.0)),
            oggi = oggi,
            autonomia = autonomia(residui = 120.0),
            da = Coordinate(42.7185, 12.0),
        )
        assertEquals(100.0, briefing.kmDomani!!, 3.0)
        assertTrue(briefing.rifornire)
    }

    @Test
    fun `senza il parametro dei km con un pieno non si avvisa`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Lontano", "2026-08-07", lat = 43.618, lon = 12.0)),
            oggi = oggi,
            autonomia = null,
            da = Coordinate(42.7185, 12.0),
        )
        assertFalse(briefing.rifornire)
    }

    @Test
    fun `senza sapere quanto guiderai domani conta solo la riserva`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07")),
            oggi = oggi,
            autonomia = autonomia(residui = 150.0),
        )
        assertNull(briefing.kmDomani)
        assertFalse(briefing.rifornire)
    }

    // --- il testo -------------------------------------------------------------

    @Test
    fun `il titolo nomina il giorno e le tappe`() {
        val briefing = Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07"), tappa("Bolsena", "2026-08-07")),
            oggi = oggi,
        )
        assertEquals("Domani, venerdì 7 agosto: Viterbo e Bolsena", TestoBriefing.titolo(briefing))
    }

    @Test
    fun `una tappa sola non prende la congiunzione`() {
        val briefing = Briefings.componi(listOf(tappa("Roma", "2026-08-07")), oggi)
        assertEquals("Domani, venerdì 7 agosto: Roma", TestoBriefing.titolo(briefing))
    }

    @Test
    fun `tre tappe si separano con le virgole e l'ultima con la e`() {
        val briefing = Briefings.componi(
            tappe = listOf(
                tappa("Bolsena", "2026-08-07"),
                tappa("Montefiascone", "2026-08-07"),
                tappa("Viterbo", "2026-08-07"),
            ),
            oggi = oggi,
        )
        assertTrue(TestoBriefing.titolo(briefing).endsWith("Bolsena, Montefiascone e Viterbo"))
    }

    @Test
    fun `senza tappe il titolo lo dice`() {
        assertEquals(
            "Domani nessuna tappa in programma",
            TestoBriefing.titolo(Briefings.componi(emptyList(), oggi)),
        )
    }

    @Test
    fun `se serve rifornire il titolo lo dice anche senza tappe`() {
        val briefing = Briefings.componi(emptyList(), oggi, autonomia = autonomia(residui = 40.0))
        assertEquals("Domani conviene rifornire", TestoBriefing.titolo(briefing))
    }

    @Test
    fun `il corpo porta i chilometri, l'avviso e i giorni dopo`() {
        val briefing = Briefings.componi(
            tappe = listOf(
                tappa("Lontano", "2026-08-07", lat = 43.618, lon = 12.0),
                tappa("Roma", "2026-08-08"),
            ),
            oggi = oggi,
            autonomia = autonomia(residui = 120.0),
            da = Coordinate(42.7185, 12.0),
        )

        val corpo = TestoBriefing.corpo(briefing)
        assertTrue(corpo, corpo.contains("km in linea d'aria"))
        assertTrue(corpo, corpo.contains("Rifornisci"))
        assertTrue(corpo, corpo.contains("120 km"))
        assertTrue(corpo, corpo.contains("sabato 8 agosto: Roma"))
    }

    @Test
    fun `il corpo tace su quello che non ha da dire`() {
        val briefing = Briefings.componi(listOf(tappa("Roma", "2026-08-07")), oggi)
        assertEquals("", TestoBriefing.corpo(briefing))
    }

    @Test
    fun `le tappe senza data compaiono nel corpo`() {
        val briefing = Briefings.componi(listOf(tappa("Un posto", null)), oggi)
        assertTrue(TestoBriefing.corpo(briefing).contains("Ancora da fare: Un posto"))
    }

    // --- quando scatta --------------------------------------------------------

    @Test
    fun `il prossimo scatto e stasera se le diciannove devono ancora arrivare`() {
        assertEquals(
            LocalDateTime.parse("2026-08-06T19:00"),
            Briefings.prossimoScatto(19, LocalDateTime.parse("2026-08-06T08:30")),
        )
    }

    @Test
    fun `passate le diciannove il prossimo scatto e domani`() {
        assertEquals(
            LocalDateTime.parse("2026-08-07T19:00"),
            Briefings.prossimoScatto(19, LocalDateTime.parse("2026-08-06T21:15")),
        )
    }

    @Test
    fun `alle diciannove in punto si programma domani, non fra un istante`() {
        assertEquals(
            LocalDateTime.parse("2026-08-07T19:00"),
            Briefings.prossimoScatto(19, LocalDateTime.parse("2026-08-06T19:00")),
        )
    }

    @Test
    fun `un'ora fuori dal quadrante si riporta dentro`() {
        assertEquals(
            LocalDateTime.parse("2026-08-07T00:00"),
            Briefings.prossimoScatto(-5, LocalDateTime.parse("2026-08-06T21:15")),
        )
        assertEquals(
            LocalDateTime.parse("2026-08-06T23:00"),
            Briefings.prossimoScatto(99, LocalDateTime.parse("2026-08-06T21:15")),
        )
    }
}
