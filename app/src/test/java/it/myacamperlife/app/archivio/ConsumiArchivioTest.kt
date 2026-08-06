package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.StimaAutonomia
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Rifornimenti, consumi e autonomia dal lato dell'archivio. */
class ConsumiArchivioTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio
    private lateinit var slug: String

    @Before
    fun prepara() {
        radice = Files.createTempDirectory("consumi").toFile()
        archivio = Archivio(radice)
        archivio.prepara()
        slug = archivio.creaViaggio(
            nome = "Toscana",
            punti = listOf(
                Waypoint("Firenze", 43.7696, 11.2558),
                Waypoint("Orvieto", 42.7185, 12.1112),
            ),
            oggi = LocalDate.of(2026, 8, 5),
            adesso = OffsetDateTime.parse("2026-08-05T08:00:00+02:00"),
        ).slug
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    private fun ora(giorno: Int, h: Int, m: Int = 0): OffsetDateTime =
        OffsetDateTime.parse("2026-08-%02dT%02d:%02d:00+02:00".format(giorno, h, m))

    @Test
    fun `un rifornimento si rilegge con tutti i suoi campi`() {
        archivio.registraRifornimento(
            slug, km = 48210, litri = 62.3, euro = 107.16, pieno = true,
            posizione = Posizione(42.7185, 12.1112), adesso = ora(6, 18, 5),
        )

        val letto = archivio.rifornimenti(slug).single()
        assertEquals(48210, letto.km)
        assertEquals(62.3, letto.litri, 1e-6)
        assertEquals(107.16, letto.euro!!, 1e-6)
        assertTrue(letto.pieno)
        assertEquals(42.7185, letto.lat!!, 1e-6)
    }

    @Test
    fun `un rifornimento senza importo resta valido`() {
        archivio.registraRifornimento(slug, km = 1000, litri = 40.0, euro = null, adesso = ora(6, 9))

        val letto = archivio.rifornimenti(slug).single()
        assertNull(letto.euro)
        assertEquals(40.0, letto.litri, 1e-6)
    }

    @Test
    fun `il parziale si distingue dal pieno`() {
        archivio.registraRifornimento(slug, km = 1000, litri = 60.0, pieno = true, adesso = ora(6, 9))
        archivio.registraRifornimento(slug, km = 1300, litri = 20.0, pieno = false, adesso = ora(7, 9))

        val letti = archivio.rifornimenti(slug)
        assertEquals(listOf(true, false), letti.map { it.pieno })
    }

    @Test
    fun `il consumo si calcola sui dati riletti da file`() {
        archivio.registraRifornimento(slug, km = 48000, litri = 60.0, euro = 100.0, adesso = ora(5, 9))
        archivio.registraRifornimento(slug, km = 48600, litri = 50.0, euro = 86.0, adesso = ora(7, 9))

        val consumo = archivio.consumo(slug)
        assertEquals(600, consumo.kmTotali)
        assertEquals("600 km con 50 litri", 12.0, consumo.kmPerLitro!!, 1e-6)
        // 86 euro su 600 km. I 100 euro del primo pieno appartengono al tratto
        // precedente, che non c'e': per questo non entrano nel conto.
        assertEquals(14.333, consumo.euroPer100!!, 1e-3)
        assertEquals(86.0, consumo.euroTotali!!, 1e-6)
    }

    @Test
    fun `la virgola decimale sopravvive al giro su file`() {
        // Il punto per cui il separatore e' il punto e virgola: 62,3 litri e
        // 107,16 euro devono restare due numeri, non quattro campi.
        archivio.registraRifornimento(slug, km = 1000, litri = 62.3, euro = 107.16, adesso = ora(6, 9))

        val file = File(archivio.cartellaViaggio(slug), RifornimentiTabella.NOME_FILE)
        val riga = file.readLines()[1]
        assertTrue("la riga deve contenere 62,30", riga.contains("62,30"))
        assertTrue("la riga deve contenere 107,16", riga.contains("107,16"))

        val letto = archivio.rifornimenti(slug).single()
        assertEquals(62.3, letto.litri, 1e-6)
        assertEquals(107.16, letto.euro!!, 1e-6)
    }

    @Test
    fun `un rifornimento finisce nel diario con litri e importo`() {
        archivio.checkin(slug, archivio.tappe(slug).first { it.nome == "Orvieto" }, adesso = ora(6, 14))
        archivio.registraRifornimento(slug, km = 1000, litri = 62.3, euro = 107.16, adesso = ora(6, 18, 5))

        val diario = archivio.diario(slug).testo()
        assertTrue(diario, diario.contains("- 18:05 · pieno a Orvieto: 62,3 litri, 107,16"))
    }

    @Test
    fun `un parziale nel diario non si chiama pieno`() {
        archivio.registraRifornimento(slug, km = 1000, litri = 20.0, pieno = false, adesso = ora(6, 9))

        val voce = archivio.voci(slug).single { it.genere == Genere.RIFORNIMENTO }
        assertTrue(voce.testo, voce.testo.startsWith("rifornimento"))
    }

    @Test
    fun `i punti raccolgono tutte le fonti di coordinate`() {
        val orvieto = archivio.tappe(slug).first { it.nome == "Orvieto" }
        archivio.checkin(slug, orvieto, adesso = ora(6, 14))
        archivio.registraPosizione(slug, Posizione(42.70, 12.10), adesso = ora(6, 15))
        archivio.registraNota(slug, "una nota", Posizione(42.71, 12.11), adesso = ora(6, 16))
        archivio.registraFoto(slug, "x.jpg", null, Posizione(42.72, 12.12), adesso = ora(6, 17))
        archivio.registraRifornimento(
            slug, km = 1000, litri = 60.0, posizione = Posizione(42.73, 12.13), adesso = ora(6, 18),
        )

        val punti = archivio.punti(slug)
        assertEquals("check-in, posizione, nota, foto, rifornimento", 5, punti.size)
        assertEquals("in ordine di ora", punti.sortedBy { it.istante }, punti)
    }

    @Test
    fun `una nota senza coordinate non diventa un punto`() {
        archivio.registraNota(slug, "senza GPS", posizione = null, adesso = ora(6, 16))

        assertTrue(archivio.punti(slug).isEmpty())
    }

    @Test
    fun `l'autonomia si stima dai dati dell'archivio`() {
        archivio.salvaImpostazioni(Impostazioni(kmConUnPieno = 900))
        archivio.registraRifornimento(
            slug, km = 1000, litri = 60.0, pieno = true,
            posizione = Posizione(43.0, 11.0), adesso = ora(6, 9),
        )
        archivio.registraPosizione(slug, Posizione(44.0, 11.0), adesso = ora(6, 15))

        val stima = StimaAutonomia.calcola(
            kmConUnPieno = archivio.impostazioni().kmConUnPieno,
            rifornimenti = archivio.rifornimenti(slug),
            punti = archivio.punti(slug),
        )!!

        assertEquals(111.2, stima.kmStimati, 1.0)
        assertEquals(788.8, stima.residui, 1.0)
    }

    @Test
    fun `le impostazioni si salvano e si rileggono`() {
        assertNull("all'inizio il parametro non c'e'", archivio.impostazioni().kmConUnPieno)

        archivio.salvaImpostazioni(Impostazioni(kmConUnPieno = 850))

        assertEquals(850, archivio.impostazioni().kmConUnPieno)
        assertTrue(File(radice, "impostazioni.json").exists())
    }

    @Test
    fun `un file di impostazioni rovinato non fa cadere l'app`() {
        File(radice, "impostazioni.json").writeText("{ questo non e' json")

        assertNull(archivio.impostazioni().kmConUnPieno)
    }

    @Test
    fun `una correzione a un rifornimento vince sulla riga di prima`() {
        archivio.registraRifornimento(slug, km = 1000, litri = 60.0, adesso = ora(6, 9))
        val sbagliato = archivio.rifornimenti(slug).single()

        // Correggere e' accodare una riga con lo stesso id.
        archivio.tabellaRifornimenti(slug).accoda(
            mapOf(
                Csv.ID to sbagliato.id,
                Csv.TS to "2026-08-06T10:00:00+02:00",
                RifornimentiTabella.KM to "1000",
                RifornimentiTabella.LITRI to "62,30",
                RifornimentiTabella.PIENO to "si",
            ),
        )

        assertEquals(62.3, archivio.rifornimenti(slug).single().litri, 1e-6)
        assertEquals("la storia resta nel file", 2, archivio.tabellaRifornimenti(slug).righe().size)
    }
}
