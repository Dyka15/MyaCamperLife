package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.Dintorno
import it.myacamperlife.app.dominio.Luogo
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.MeteoLuogo
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.Previsione
import it.myacamperlife.app.dominio.Tratta
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BriefingArchivioTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio

    @Before
    fun prepara() {
        radice = File.createTempFile("briefing", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        archivio = Archivio(radice)
        archivio.prepara()
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    private val oggi: LocalDate = LocalDate.parse("2026-08-06")

    private fun creaToscana(): String = archivio.creaViaggio(
        nome = "Toscana",
        punti = listOf(
            Waypoint("Orvieto", 42.7185, 12.1112, giorno = "2026-08-06"),
            Waypoint("Viterbo", 42.4207, 12.1077, giorno = "2026-08-07"),
            Waypoint("Roma", 41.9028, 12.4964, giorno = "2026-08-08"),
        ),
        oggi = oggi,
        adesso = OffsetDateTime.parse("2026-08-01T09:00:00+02:00"),
    ).slug

    private fun quando(giorno: String, ora: String = "12:00:00") =
        OffsetDateTime.parse("${giorno}T$ora+02:00")

    // --- cosa dice ------------------------------------------------------------

    @Test
    fun `il briefing legge le tappe dell'itinerario`() {
        val briefing = archivio.briefing(creaToscana(), oggi)
        assertEquals(listOf("Viterbo"), briefing.domani!!.nomi)
        assertEquals(listOf("Roma"), briefing.poi.single().nomi)
    }

    @Test
    fun `una tappa spuntata esce dal briefing`() {
        val slug = creaToscana()
        val viterbo = archivio.tappe(slug).first { it.nome == "Viterbo" }
        archivio.checkin(slug, viterbo, adesso = quando("2026-08-06", "16:00:00"))

        assertNull(archivio.briefing(slug, oggi).domani)
    }

    @Test
    fun `i chilometri di domani partono dall'ultima posizione registrata`() {
        val slug = creaToscana()
        archivio.registraPosizione(
            slug = slug,
            posizione = Posizione(42.7185, 12.1112), // Orvieto
            adesso = quando("2026-08-06", "15:00:00"),
        )
        // Orvieto-Viterbo in linea d'aria: una trentina di chilometri.
        assertEquals(33.1, archivio.briefing(slug, oggi).kmDomani!!, 2.0)
    }

    @Test
    fun `senza itinerario aperto non c'e' briefing`() {
        assertNull(archivio.briefingCorrente(oggi))
    }

    @Test
    fun `il briefing corrente e quello del viaggio piu recente`() {
        creaToscana()
        archivio.creaViaggio(
            nome = "Svizzera",
            punti = listOf(Waypoint("Lugano", 46.0, 8.95, giorno = "2026-08-07")),
            oggi = oggi,
            adesso = OffsetDateTime.parse("2026-08-05T09:00:00+02:00"),
        )
        assertEquals(listOf("Lugano"), archivio.briefingCorrente(oggi)!!.domani!!.nomi)
    }

    // --- l'avviso di rifornimento ---------------------------------------------

    @Test
    fun `senza i km con un pieno non c'e' avviso di rifornimento`() {
        val slug = creaToscana()
        archivio.registraRifornimento(
            slug, km = 48000, euro = 100.0, prezzoLitro = 1.667,
            adesso = quando("2026-08-05"),
        )

        val briefing = archivio.briefing(slug, oggi)
        assertNull(briefing.autonomia)
        assertFalse(briefing.rifornire)
    }

    @Test
    fun `con un serbatoio quasi vuoto il briefing avvisa`() {
        val slug = creaToscana()
        archivio.salvaImpostazioni(Impostazioni(kmConUnPieno = 100))
        archivio.registraRifornimento(
            slug = slug,
            km = 48000,
            euro = 100.0,
            prezzoLitro = 1.667,
            posizione = Posizione(43.7696, 11.2558), // Firenze
            adesso = quando("2026-08-05", "09:00:00"),
        )
        // Da Firenze a Orvieto sono circa 120 km in linea d'aria: con un pieno
        // che ne fa 100, l'autonomia stimata e' a zero.
        archivio.registraPosizione(
            slug = slug,
            posizione = Posizione(42.7185, 12.1112),
            adesso = quando("2026-08-06", "15:00:00"),
        )

        val briefing = archivio.briefing(slug, oggi)
        assertEquals(0.0, briefing.autonomia!!.residui, 0.001)
        assertTrue(briefing.rifornire)
    }

    @Test
    fun `con un pieno grande e poca strada non avvisa`() {
        val slug = creaToscana()
        archivio.salvaImpostazioni(Impostazioni(kmConUnPieno = 900))
        archivio.registraRifornimento(
            slug = slug,
            km = 48000,
            euro = 100.0,
            prezzoLitro = 1.667,
            posizione = Posizione(42.7185, 12.1112),
            adesso = quando("2026-08-06", "09:00:00"),
        )

        assertFalse(archivio.briefing(slug, oggi).rifornire)
    }

    // --- le impostazioni ------------------------------------------------------

    @Test
    fun `il riepilogo e acceso di riposo, alle diciannove`() {
        val impostazioni = archivio.impostazioni()
        assertTrue(impostazioni.briefingAttivo)
        assertEquals(19, impostazioni.ora)
    }

    @Test
    fun `un'ora fuori dal quadrante si riporta dentro`() {
        assertEquals(23, Impostazioni(oraBriefing = 47).ora)
        assertEquals(0, Impostazioni(oraBriefing = -3).ora)
    }

    @Test
    fun `le impostazioni del briefing sopravvivono al salvataggio`() {
        archivio.salvaImpostazioni(
            Impostazioni(kmConUnPieno = 600, briefingAttivo = false, oraBriefing = 20),
        )
        val rilette = archivio.impostazioni()
        assertEquals(600, rilette.kmConUnPieno)
        assertFalse(rilette.briefingAttivo)
        assertEquals(20, rilette.ora)
    }

    @Test
    fun `un file di impostazioni piu vecchio si legge lo stesso`() {
        // Scritto da una versione che il briefing non ce l'aveva.
        File(radice, "impostazioni.json").writeText("""{"kmConUnPieno": 550}""")
        val rilette = archivio.impostazioni()
        assertEquals(550, rilette.kmConUnPieno)
        assertTrue(rilette.briefingAttivo)
        assertEquals(19, rilette.ora)
    }

    // --- la scorta ------------------------------------------------------------

    private val orvieto = Waypoint("Orvieto", 42.7185, 12.1112)
    private val viterbo = Waypoint("Viterbo", 42.4207, 12.1077)

    @Test
    fun `le tratte salvate si rileggono e valgono per il briefing`() {
        val slug = creaToscana()
        archivio.registraPosizione(
            slug = slug,
            posizione = Posizione(orvieto.lat, orvieto.lon),
            adesso = quando("2026-08-06", "15:00:00"),
        )
        archivio.salvaTratte(
            slug = slug,
            tratte = listOf(
                Tratta(orvieto.lat, orvieto.lon, viterbo.lat, viterbo.lon, 58.0, 62, "Orvieto", "Viterbo"),
            ),
            adesso = quando("2026-08-06", "10:00:00"),
        )

        val briefing = archivio.briefing(slug, oggi)
        assertTrue(briefing.suStrada)
        assertEquals(58.0, briefing.kmDomani!!, 0.001)
        assertEquals(62, briefing.minutiDomani)
    }

    @Test
    fun `ricalcolare una tratta la corregge invece di duplicarla`() {
        val slug = creaToscana()
        val prima = Tratta(orvieto.lat, orvieto.lon, viterbo.lat, viterbo.lon, 58.0, 62)
        archivio.salvaTratte(slug, listOf(prima), quando("2026-08-06", "10:00:00"))
        archivio.salvaTratte(slug, listOf(prima.copy(km = 61.0)), quando("2026-08-06", "18:00:00"))

        val tratte = archivio.tratte(slug)
        assertEquals(1, tratte.tutte.size)
        assertEquals(61.0, tratte.tutte.single().km, 0.001)
    }

    @Test
    fun `senza tratte la scorta stradale e vuota`() {
        assertTrue(archivio.tratte(creaToscana()).vuoto)
    }

    @Test
    fun `il meteo salvato si rilegge e finisce nel briefing`() {
        val slug = creaToscana()
        archivio.salvaMeteo(
            slug = slug,
            meteo = Meteo(
                scaricatoIl = "2026-08-06T19:00:00+02:00",
                luoghi = listOf(
                    MeteoLuogo(
                        nome = "Viterbo",
                        lat = viterbo.lat,
                        lon = viterbo.lon,
                        previsioni = listOf(
                            Previsione("2026-08-07", codice = 61, minima = 17.0, massima = 26.0),
                        ),
                    ),
                ),
            ),
        )

        val briefing = archivio.briefing(
            slug = slug,
            oggi = oggi,
            adesso = OffsetDateTime.parse("2026-08-06T19:05:00+02:00"),
        )
        assertEquals(26.0, briefing.meteoDomani!!.massima!!, 0.001)
    }

    @Test
    fun `un file di meteo rovinato vale come assente`() {
        val slug = creaToscana()
        File(archivio.cartellaScorta(slug), "meteo.json").writeText("{ meta' file")
        assertNull(archivio.meteo(slug))
        // E il briefing esce lo stesso.
        assertEquals(listOf("Viterbo"), archivio.briefing(slug, oggi).domani!!.nomi)
    }

    @Test
    fun `i punti per il meteo sono le tappe da fare nei prossimi giorni`() {
        val slug = creaToscana()
        val nomi = archivio.puntiMeteo(slug, oggi).map { it.nome }
        assertEquals(listOf("Orvieto", "Viterbo", "Roma"), nomi)
    }

    @Test
    fun `una tappa spuntata non entra fra i punti del meteo`() {
        val slug = creaToscana()
        val tappa = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, tappa, adesso = quando("2026-08-06", "14:00:00"))

        assertEquals(listOf("Viterbo", "Roma"), archivio.puntiMeteo(slug, oggi).map { it.nome })
    }

    @Test
    fun `i punti per le tratte sono tutte le tappe, in ordine`() {
        val slug = creaToscana()
        assertEquals(
            listOf("Orvieto", "Viterbo", "Roma"),
            archivio.puntiTratte(slug).map { it.nome },
        )
    }

    @Test
    fun `la scorta sta dentro il viaggio e se ne va con lui`() {
        val slug = creaToscana()
        archivio.salvaTratte(
            slug,
            listOf(Tratta(orvieto.lat, orvieto.lon, viterbo.lat, viterbo.lon, 58.0, 62)),
        )
        assertTrue(File(archivio.cartellaViaggio(slug), "scorta/tratte.csv").exists())

        archivio.elimina(slug)
        assertFalse(archivio.cartellaViaggio(slug).exists())
    }

    // --- i dintorni -----------------------------------------------------------

    private fun dintorno() = Dintorno(
        poi = listOf(
            Poi("node/1", "Area Il Cipresso", CategoriaPoi.SOSTA, 42.7200, 12.1130, "a pagamento"),
            Poi("node/2", "Eni", CategoriaPoi.CARBURANTE, 42.7250, 12.1200),
        ),
        luoghi = listOf(
            Luogo("Orvieto", 42.7185, 12.1112, abitanti = 20_394),
            Luogo("Bolsena", 42.6437, 11.9871, abitanti = 4_000),
            Luogo("Sugano", 42.7350, 12.0900),
        ),
    )

    @Test
    fun `i dintorni salvati si rileggono`() {
        val slug = creaToscana()
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-06", "10:00:00"))

        val poi = archivio.poi(slug)
        assertEquals(2, poi.size)
        assertEquals(CategoriaPoi.SOSTA, poi.first { it.id == "node/1" }.categoria)
        assertEquals("a pagamento", poi.first { it.id == "node/1" }.dettaglio)

        val luoghi = archivio.luoghi(slug)
        assertEquals(3, luoghi.tutti.size)
        assertEquals(20_394, luoghi.tutti.first { it.nome == "Orvieto" }.abitanti)
        assertNull(luoghi.tutti.first { it.nome == "Sugano" }.abitanti)
    }

    @Test
    fun `riscaricare i dintorni aggiorna invece di duplicare`() {
        val slug = creaToscana()
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-06", "10:00:00"))
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-07", "10:00:00"))

        assertEquals(2, archivio.poi(slug).size)
        assertEquals(3, archivio.luoghi(slug).tutti.size)
    }

    @Test
    fun `senza dintorni le liste sono vuote e non cade niente`() {
        val slug = creaToscana()
        assertTrue(archivio.poi(slug).isEmpty())
        assertTrue(archivio.luoghi(slug).vuoto)
    }

    // --- come si chiama il posto dove sei -------------------------------------

    @Test
    fun `il toponimo vince sul nome della tappa`() {
        val slug = creaToscana()
        val orvieto = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, orvieto, adesso = quando("2026-08-06", "14:00:00"))
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-06", "10:00:00"))

        // Sei sceso al lago: il check-in dice Orvieto, ma sei a Bolsena.
        // Sugano, che dista due chilometri e mezzo da Orvieto, non andrebbe
        // bene per questa prova: li' vincerebbe Orvieto, ed e' giusto cosi'.
        assertEquals("Bolsena", archivio.dove(slug, Posizione(42.6437, 11.9871)))
    }

    @Test
    fun `senza scorta di toponimi si ripiega sulla tappa`() {
        val slug = creaToscana()
        val orvieto = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, orvieto, adesso = quando("2026-08-06", "14:00:00"))

        assertEquals("Orvieto", archivio.dove(slug, Posizione(42.6437, 11.9871)))
    }

    @Test
    fun `senza posizione si ripiega sulla tappa anche con la scorta`() {
        val slug = creaToscana()
        val orvieto = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, orvieto, adesso = quando("2026-08-06", "14:00:00"))
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-06", "10:00:00"))

        assertEquals("Orvieto", archivio.dove(slug, null))
    }

    @Test
    fun `la foto registrata prende il nome del posto vero`() {
        val slug = creaToscana()
        archivio.salvaDintorni(slug, dintorno(), quando("2026-08-06", "10:00:00"))
        archivio.registraFoto(
            slug = slug,
            nomeFile = "foto_20260806_150000_Bolsena.jpg",
            posizione = Posizione(42.6437, 11.9871),
            adesso = quando("2026-08-06", "15:00:00"),
        )
        assertEquals(
            "Bolsena",
            archivio.tabellaFoto(slug).vive().single().testo(FotoTabella.TAPPA),
        )
    }

    @Test
    fun `la ricerca parte dall'ultima posizione registrata`() {
        val slug = creaToscana()
        archivio.registraPosizione(
            slug = slug,
            posizione = Posizione(42.6437, 11.9871),
            adesso = quando("2026-08-06", "15:00:00"),
        )
        val da = archivio.dovePunto(slug)!!
        assertEquals(42.6437, da.lat, 0.0001)
    }

    @Test
    fun `senza niente registrato si parte dalla prima tappa`() {
        val slug = creaToscana()
        assertEquals(42.7185, archivio.dovePunto(slug)!!.lat, 0.0001)
    }

    @Test
    fun `i punti dei dintorni sono le tappe da fare, con quella dove sei`() {
        val slug = creaToscana()
        val orvieto = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, orvieto, adesso = quando("2026-08-06", "14:00:00"))

        // Orvieto in testa perche' e' dove sei, poi le due che restano.
        assertEquals(3, archivio.puntiDintorni(slug).size)
        assertEquals(42.7185, archivio.puntiDintorni(slug).first().lat, 0.0001)
    }
}
