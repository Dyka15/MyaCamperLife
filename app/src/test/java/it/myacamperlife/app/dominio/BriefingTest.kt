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
    fun `sulla linea d'aria il margine fa scattare l'avviso prima`() {
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

    // --- le tratte su strada --------------------------------------------------

    private val orvieto = Coordinate(42.7185, 12.1112)
    private val viterbo = Coordinate(42.4207, 12.1077)

    private fun scorta(km: Double, minuti: Int) = Tratte(
        listOf(Tratta(orvieto.lat, orvieto.lon, viterbo.lat, viterbo.lon, km, minuti)),
    )

    // Duecento chilometri e non cinquanta: sotto la riserva degli 80 km
    // l'avviso scatta comunque, e non si vedrebbe l'effetto del margine.
    private fun conTratte(autonomia: Autonomia? = null, tratte: Tratte? = scorta(200.0, 150)) =
        Briefings.componi(
            tappe = listOf(tappa("Viterbo", "2026-08-07", lat = viterbo.lat, lon = viterbo.lon)),
            oggi = oggi,
            autonomia = autonomia,
            da = orvieto,
            tratte = tratte,
        )

    @Test
    fun `con le tratte i chilometri sono quelli veri, e arriva il tempo di guida`() {
        val briefing = conTratte()
        assertTrue(briefing.suStrada)
        assertEquals(200.0, briefing.kmDomani!!, 0.001)
        assertEquals(150, briefing.minutiDomani)
    }

    @Test
    fun `senza tratte si ripiega sulla linea d'aria, e si dichiara`() {
        val briefing = conTratte(tratte = null)
        assertFalse(briefing.suStrada)
        assertNull(briefing.minutiDomani)
        // La linea d'aria e' piu' corta della strada: e' il motivo per cui
        // bisogna dire quale dei due numeri e'.
        assertTrue(briefing.kmDomani!! < 200.0)
    }

    @Test
    fun `una tratta che non copre tutta la catena non vale`() {
        val altrove = Tratte(listOf(Tratta(45.0, 9.0, 45.5, 9.5, 60.0, 60)))
        assertFalse(conTratte(tratte = altrove).suStrada)
    }

    @Test
    fun `sulle tratte vere il margine si stringe`() {
        // 200 km su strada: la soglia e' 230 con il margine della strada
        // (1,15) e sarebbe 280 con quello dell'aria (1,4). Con 250 km di
        // autonomia l'avviso non deve suonare, perche' una delle due
        // incertezze non c'e' piu'.
        assertFalse(conTratte(autonomia = autonomia(residui = 250.0, pieno = 900)).rifornire)
        assertTrue(conTratte(autonomia = autonomia(residui = 220.0, pieno = 900)).rifornire)
    }

    @Test
    fun `il testo dice i chilometri su strada e il tempo di guida`() {
        val corpo = TestoBriefing.corpo(conTratte())
        assertTrue(corpo, corpo.contains("200 km, 2 h 30 di guida"))
        assertTrue(corpo, !corpo.contains("linea d'aria"))
    }

    // --- il meteo -------------------------------------------------------------

    private val seraDiOggi: OffsetDateTime = OffsetDateTime.parse("2026-08-06T19:00:00+02:00")

    private fun conMeteo(
        previsione: Previsione,
        scaricatoIl: OffsetDateTime = seraDiOggi,
        adesso: OffsetDateTime = seraDiOggi,
    ) = Briefings.componi(
        tappe = listOf(tappa("Viterbo", "2026-08-07", lat = viterbo.lat, lon = viterbo.lon)),
        oggi = oggi,
        meteo = Meteo(
            scaricatoIl = scaricatoIl.toString(),
            luoghi = listOf(MeteoLuogo("Viterbo", viterbo.lat, viterbo.lon, listOf(previsione))),
        ),
        adesso = adesso,
    )

    @Test
    fun `il meteo di domani entra nel briefing`() {
        val briefing = conMeteo(Previsione("2026-08-07", codice = 0, minima = 18.0, massima = 31.0))
        assertEquals(31.0, briefing.meteoDomani!!.massima!!, 0.001)
        assertTrue(TestoBriefing.corpo(briefing).contains("Sereno, 18–31°"))
    }

    @Test
    fun `un meteo vecchio si usa dicendo quanto e' vecchio`() {
        val briefing = conMeteo(
            previsione = Previsione("2026-08-07", codice = 0, massima = 31.0),
            scaricatoIl = seraDiOggi.minusHours(30),
        )
        assertTrue(TestoBriefing.corpo(briefing).contains("meteo di ieri"))
    }

    @Test
    fun `un meteo scaduto non si mostra affatto`() {
        val briefing = conMeteo(
            previsione = Previsione("2026-08-07", codice = 0, massima = 31.0),
            scaricatoIl = seraDiOggi.minusDays(5),
        )
        assertNull(briefing.meteoDomani)
    }

    @Test
    fun `il meteo di un altro giorno non e' quello di domani`() {
        val briefing = conMeteo(Previsione("2026-08-20", codice = 0, massima = 31.0))
        assertNull(briefing.meteoDomani)
    }

    @Test
    fun `senza scorta di meteo il briefing esce lo stesso`() {
        val briefing = Briefings.componi(listOf(tappa("Viterbo", "2026-08-07")), oggi)
        assertNull(briefing.meteoDomani)
        assertEquals("Domani, venerdì 7 agosto: Viterbo", TestoBriefing.titolo(briefing))
    }
}
