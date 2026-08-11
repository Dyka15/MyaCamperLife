package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedaTappaTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-08")
    private val adesso: OffsetDateTime = OffsetDateTime.parse("2026-08-08T18:00:00+02:00")

    private val orvieto = Tappa(
        id = "1", ordine = 1, nome = "Orvieto", lat = 42.7185, lon = 12.1112,
        giorno = "2026-08-08", descrizione = "Duomo e pozzo di San Patrizio",
    )
    private val bolsena = Tappa(
        id = "2", ordine = 2, nome = "Bolsena", lat = 42.6437, lon = 11.9871,
        giorno = "2026-08-10", descrizione = "  Sosta sul lago  ",
    )
    private val viterbo = Tappa(
        id = "3", ordine = 3, nome = "Viterbo", lat = 42.4173, lon = 12.1057,
    )
    private val tappe = listOf(orvieto, bolsena, viterbo)

    private fun meteo(scaricatoIl: String = "2026-08-08T19:00:00+02:00") = Meteo(
        scaricatoIl = scaricatoIl,
        luoghi = listOf(
            MeteoLuogo(
                nome = "Bolsena", lat = 42.6437, lon = 11.9871,
                previsioni = listOf(
                    Previsione("2026-08-08", codice = 0, minima = 19.0, massima = 33.0),
                    Previsione("2026-08-10", codice = 61, minima = 17.0, massima = 24.0),
                ),
            ),
        ),
    )

    private fun poi(nome: String, categoria: CategoriaPoi, lat: Double, lon: Double) =
        Poi("node/$nome", nome, categoria, lat, lon)

    // --- il giorno ------------------------------------------------------------

    @Test
    fun `la scheda legge il giorno e dice quanto e' lontano`() {
        val scheda = Schede.componi(bolsena, tappe, oggi)
        assertEquals(LocalDate.parse("2026-08-10"), scheda.giorno)
        assertEquals(2L, scheda.fraGiorni)
    }

    @Test
    fun `un giorno illeggibile lascia la scheda senza data, non nel giorno sbagliato`() {
        val scheda = Schede.componi(bolsena.copy(giorno = "quando capita"), tappe, oggi)
        assertNull(scheda.giorno)
        assertNull(scheda.fraGiorni)
    }

    @Test
    fun `una tappa datata nel passato ha giorni negativi`() {
        val scheda = Schede.componi(bolsena.copy(giorno = "2026-08-05"), tappe, oggi)
        assertEquals(-3L, scheda.fraGiorni)
    }

    // --- la descrizione -------------------------------------------------------

    @Test
    fun `la descrizione arriva dall'itinerario, ripulita`() {
        assertEquals("Sosta sul lago", Schede.componi(bolsena, tappe, oggi).descrizione)
    }

    @Test
    fun `una descrizione di soli spazi non e' una descrizione`() {
        assertNull(Schede.componi(bolsena.copy(descrizione = "   "), tappe, oggi).descrizione)
    }

    // --- il meteo -------------------------------------------------------------

    @Test
    fun `la previsione e' quella del giorno della tappa, non di oggi`() {
        val scheda = Schede.componi(bolsena, tappe, oggi, meteo = meteo(), adesso = adesso)
        // Oggi a Bolsena e' sereno 19-33; il giorno della tappa e' un altro.
        assertEquals(61, scheda.previsione?.codice)
        assertEquals(24.0, scheda.previsione?.massima)
    }

    @Test
    fun `senza il giorno non c'e' previsione, perche' non si sa di quando`() {
        val scheda = Schede.componi(
            bolsena.copy(giorno = null), tappe, oggi, meteo = meteo(), adesso = adesso,
        )
        assertNull(scheda.previsione)
    }

    @Test
    fun `una scorta meteo scaduta non si usa affatto`() {
        // Cinque giorni: non e' un dato vecchio, e' un dato sbagliato.
        val scheda = Schede.componi(
            bolsena, tappe, oggi,
            meteo = meteo("2026-08-03T19:00:00+02:00"),
            adesso = adesso,
        )
        assertNull(scheda.previsione)
        assertNull(scheda.meteoOreFa)
    }

    @Test
    fun `l'eta' della previsione si porta dietro la previsione`() {
        val scheda = Schede.componi(
            bolsena, tappe, oggi,
            meteo = meteo("2026-08-07T19:00:00+02:00"),
            adesso = adesso,
        )
        assertNotNull(scheda.previsione)
        assertEquals(23L, scheda.meteoOreFa)
    }

    @Test
    fun `una previsione troppo lontana dalla tappa non le appartiene`() {
        // La griglia di Open-Meteo e' larga, ma non centinaia di chilometri.
        val lontana = Tappa(id = "9", ordine = 9, nome = "Aosta", lat = 45.7372, lon = 7.3206, giorno = "2026-08-10")
        val scheda = Schede.componi(lontana, tappe + lontana, oggi, meteo = meteo(), adesso = adesso)
        assertNull(scheda.previsione)
    }

    // --- da dove si arriva ----------------------------------------------------

    @Test
    fun `si arriva dalla tappa prima nell'itinerario, non dalla piu' vicina`() {
        // Viterbo e' piu' vicina a Orvieto che a Bolsena in linea d'aria? Non
        // importa: l'itinerario dice Bolsena, e l'itinerario e' quello che fai.
        assertEquals("Bolsena", Schede.componi(viterbo, tappe, oggi).da?.nome)
    }

    @Test
    fun `la prima tappa non arriva da nessuna parte`() {
        val scheda = Schede.componi(orvieto, tappe, oggi)
        assertNull(scheda.da)
        assertNull(scheda.percorso)
    }

    @Test
    fun `i chilometri sono quelli su strada, quando le tratte ce li hanno`() {
        val tratte = Tratte(
            listOf(
                Tratta(
                    daLat = 42.7185, daLon = 12.1112, aLat = 42.6437, aLon = 11.9871,
                    km = 21.0, minuti = 28,
                ),
            ),
        )
        val scheda = Schede.componi(bolsena, tappe, oggi, tratte = tratte)
        assertEquals(21.0, scheda.percorso?.km)
        assertEquals("28 min", scheda.percorso?.durata)
    }

    @Test
    fun `senza tratta non si mostra una distanza travestita`() {
        assertNull(Schede.componi(bolsena, tappe, oggi, tratte = Tratte()).percorso)
    }

    // --- i dintorni -----------------------------------------------------------

    @Test
    fun `i dintorni della scheda sono quelli della tappa`() {
        val scheda = Schede.componi(
            bolsena, tappe, oggi,
            poi = listOf(
                poi("Area Lido", CategoriaPoi.SOSTA, 42.6470, 11.9900),
                // A Orvieto, venticinque chilometri piu' su: non e' nei
                // dintorni di Bolsena.
                poi("Area Orvieto", CategoriaPoi.SOSTA, 42.7185, 12.1112),
            ),
        )
        assertEquals(1, scheda.dintorni.size)
        assertEquals("Area Lido", scheda.dintorni.first().piuVicino.poi.nome)
    }

    @Test
    fun `una scorta lontana non riempie i dintorni di questa tappa`() {
        // Prima la scheda distingueva "non ho scorta" da "qui non c'e' niente",
        // e la seconda diceva "niente di segnato nel raggio di venti
        // chilometri". Quella frase reggeva **solo** finche' una ricerca sola
        // copriva tutto l'itinerario: ora si cerca una tappa per volta, e una
        // scorta presa altrove non dice niente su qui. Restano i dintorni vuoti,
        // e l'unica cosa onesta e' offrire la ricerca.
        assertTrue(Schede.componi(bolsena, tappe, oggi).dintorni.isEmpty())

        val altrove = Schede.componi(
            bolsena, tappe, oggi,
            poi = listOf(poi("Area Aosta", CategoriaPoi.SOSTA, 45.7372, 7.3206)),
        )
        assertTrue(altrove.dintorni.isEmpty())
    }

    // --- i dossier ------------------------------------------------------------

    @Test
    fun `la scheda mostra solo le risposte di quella tappa`() {
        val dossier = listOf(
            Dossier("a", adesso, "Dove dormiamo?", "Bolsena", "gemini", "a.md"),
            Dossier("b", adesso, "E qui?", "Orvieto", "gemini", "b.md"),
            Dossier("c", adesso, "Senza tappa", null, "grok", "c.md"),
        )
        val scheda = Schede.componi(bolsena, tappe, oggi, dossier = dossier)
        assertEquals(listOf("a"), scheda.dossier.map { it.id })
    }
}

class RiassuntoDintorniTest {

    private fun poi(nome: String, categoria: CategoriaPoi, lat: Double, lon: Double) =
        Poi("node/$nome", nome, categoria, lat, lon)

    private val qui = 42.6437 to 11.9871

    @Test
    fun `ogni riga porta quanti sono e il piu' vicino`() {
        val riassunto = Dintorni.riassunto(
            listOf(
                poi("Area Lido", CategoriaPoi.SOSTA, 42.6470, 11.9900),
                poi("Area Cipresso", CategoriaPoi.SOSTA, 42.6700, 12.0100),
                poi("Coop", CategoriaPoi.SPESA, 42.6450, 11.9880),
            ),
            qui.first, qui.second,
        )

        val sosta = riassunto.first { it.categoria == CategoriaPoi.SOSTA }
        assertEquals(2, sosta.quanti)
        assertEquals("Area Lido", sosta.piuVicino.poi.nome)
    }

    @Test
    fun `le categorie vuote non compaiono`() {
        val riassunto = Dintorni.riassunto(
            listOf(poi("Coop", CategoriaPoi.SPESA, 42.6450, 11.9880)),
            qui.first, qui.second,
        )
        assertEquals(listOf(CategoriaPoi.SPESA), riassunto.map { it.categoria })
    }

    @Test
    fun `si ordina per vicinanza del capofila, non per l'ordine dell'enum`() {
        // La spesa e' a duecento metri, la sosta a otto chilometri: quello che
        // e' vicino conta prima, qualunque sia l'ordine in cui l'enum le elenca.
        val riassunto = Dintorni.riassunto(
            listOf(
                poi("Area Cipresso", CategoriaPoi.SOSTA, 42.7100, 12.0100),
                poi("Coop", CategoriaPoi.SPESA, 42.6450, 11.9880),
            ),
            qui.first, qui.second,
        )
        assertEquals(CategoriaPoi.SPESA, riassunto.first().categoria)
    }

    @Test
    fun `i conteggi si fermano al raggio, come l'elenco`() {
        val riassunto = Dintorni.riassunto(
            listOf(
                poi("Area Lido", CategoriaPoi.SOSTA, 42.6470, 11.9900),
                poi("Area Aosta", CategoriaPoi.SOSTA, 45.7372, 7.3206),
            ),
            qui.first, qui.second,
        )
        assertEquals(1, riassunto.first().quanti)
    }
}

class TestoTappaTest {

    @Test
    fun `i primi giorni si dicono a parole`() {
        assertEquals("oggi", TestoTappa.quando(0))
        assertEquals("domani", TestoTappa.quando(1))
        assertEquals("dopodomani", TestoTappa.quando(2))
        assertEquals("fra 5 giorni", TestoTappa.quando(5))
    }

    @Test
    fun `il passato si dice al passato`() {
        assertEquals("ieri", TestoTappa.quando(-1))
        assertEquals("4 giorni fa", TestoTappa.quando(-4))
    }

    @Test
    fun `senza giorni non si dice niente`() {
        assertNull(TestoTappa.quando(null))
    }

    @Test
    fun `la data si legge di sfuggita`() {
        assertEquals("lun 10 agosto", TestoTappa.data(LocalDate.parse("2026-08-10")))
    }
}

class ContestoDiTappaTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-08")

    private val bolsena = Tappa(
        id = "2", ordine = 2, nome = "Bolsena", lat = 42.6437, lon = 11.9871,
        giorno = "2026-08-10", descrizione = "Sosta sul lago",
    )

    @Test
    fun `il contesto parla della tappa, non di dove sei`() {
        val contesto = Esplora.contestoDiTappa(
            tappa = bolsena,
            giorno = LocalDate.parse("2026-08-10"),
            oggi = oggi,
        )
        assertTrue(contesto, contesto.contains("la tappa di cui si parla e' Bolsena"))
        assertTrue(contesto, contesto.contains("oggi e' 2026-08-08"))
        // Le due date sono distinte, ed e' il punto: si chiede di dopodomani.
        assertTrue(contesto, contesto.contains("ci si arriva il 2026-08-10, dopodomani"))
        assertTrue(contesto, contesto.contains("42.6437"))
    }

    @Test
    fun `la descrizione si attribuisce a chi l'ha scritta`() {
        val contesto = Esplora.contestoDiTappa(bolsena, null, oggi)
        assertTrue(contesto, contesto.contains("dall'itinerario"))
        assertTrue(contesto, contesto.contains("\"Sosta sul lago\""))
    }

    @Test
    fun `quello che non si sa non si nomina`() {
        val contesto = Esplora.contestoDiTappa(
            tappa = bolsena.copy(giorno = null, descrizione = null),
            giorno = null,
            oggi = oggi,
        )
        assertTrue(contesto, !contesto.contains("ci si arriva il"))
        assertTrue(contesto, !contesto.contains("itinerario"))
        assertTrue(contesto, !contesto.contains("previsione"))
        assertTrue(contesto, !contesto.contains("dintorni"))
    }

    @Test
    fun `meteo, provenienza e dintorni entrano quando ci sono`() {
        val contesto = Esplora.contestoDiTappa(
            tappa = bolsena,
            giorno = LocalDate.parse("2026-08-10"),
            oggi = oggi,
            previsione = Previsione("2026-08-10", codice = 61, minima = 17.0, massima = 24.0),
            vicini = listOf(
                PoiVicino(Poi("node/1", "Area Lido", CategoriaPoi.SOSTA, 42.647, 11.99), 1.2),
            ),
            da = Tappa(id = "1", ordine = 1, nome = "Orvieto", lat = 42.7185, lon = 12.1112),
        )
        assertTrue(contesto, contesto.contains("si arriva da Orvieto"))
        assertTrue(contesto, contesto.contains("previsione per quel giorno: Pioggia, 17–24°"))
        assertTrue(contesto, contesto.contains("Area Lido (Area di sosta), 1,2 km"))
        assertTrue(contesto, contesto.contains("incompleto o invecchiato"))
    }

    @Test
    fun `la domanda del pulsante chiede dei dintorni della tappa`() {
        assertTrue(Esplora.DOMANDA_TAPPA.contains("dintorni"))
        assertTrue(Esplora.DOMANDA_TAPPA.contains("camper"))
    }
}
