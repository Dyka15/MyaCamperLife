package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArchivioTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio

    @Before
    fun prepara() {
        radice = Files.createTempDirectory("archivio").toFile()
        archivio = Archivio(radice)
        archivio.prepara()
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    @Test
    fun `lo slug mette anno e mese davanti cosi l'ordine alfabetico e cronologico`() {
        assertEquals(
            "2026-08-toscana-agosto-2026",
            Archivio.slug("Toscana, agosto 2026", LocalDate.of(2026, 8, 6)),
        )
        assertEquals("2026-01-capodanno", Archivio.slug("Capodanno", LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `lo slug toglie accenti e punteggiatura`() {
        assertEquals("2026-05-citta-di-castello", Archivio.slug("Città di Castello", LocalDate.of(2026, 5, 1)))
        assertEquals("2026-05-nord-est", Archivio.slug("Nord — Est!!!", LocalDate.of(2026, 5, 1)))
    }

    @Test
    fun `un nome che si riduce a niente non produce uno slug rotto`() {
        assertEquals("2026-05-viaggio", Archivio.slug("???", LocalDate.of(2026, 5, 1)))
        assertEquals("2026-05-viaggio", Archivio.slug("", LocalDate.of(2026, 5, 1)))
    }

    @Test
    fun `creare un viaggio scrive il viaggio e le sue tappe`() {
        val viaggio = archivio.creaViaggio(
            nome = "Toscana",
            punti = listOf(
                Waypoint("Firenze", 43.7696, 11.2558, tipo = "citta", giorno = "2026-08-05"),
                Waypoint("Orvieto", 42.7185, 12.1112, descrizione = "Duomo"),
            ),
            importatoDa = "toscana.md",
            oggi = LocalDate.of(2026, 8, 6),
            adesso = OffsetDateTime.parse("2026-08-06T10:00:00+02:00"),
        )

        assertEquals("2026-08-toscana", viaggio.slug)
        assertTrue(File(archivio.cartellaViaggio(viaggio.slug), "viaggio.json").exists())
        assertTrue(File(archivio.cartellaViaggio(viaggio.slug), "tappe.csv").exists())

        val riletto = archivio.leggiViaggio(viaggio.slug)!!
        assertEquals("Toscana", riletto.nome)
        assertEquals("toscana.md", riletto.importatoDa)
    }

    @Test
    fun `le tappe si rileggono in ordine con lo stato iniziale`() {
        val viaggio = archivio.creaViaggio(
            nome = "Tre tappe",
            punti = listOf(
                Waypoint("prima", 43.0, 11.0),
                Waypoint("seconda", 43.1, 11.1),
                Waypoint("terza", 43.2, 11.2),
            ),
            oggi = LocalDate.of(2026, 8, 6),
        )

        val tappe = archivio.tappe(viaggio.slug)

        assertEquals(listOf("prima", "seconda", "terza"), tappe.map { it.nome })
        assertEquals(listOf(1, 2, 3), tappe.map { it.ordine })
        assertTrue(tappe.all { it.stato == StatoTappa.DA_FARE })
        assertEquals(43.1, tappe[1].lat, 1e-6)
        assertEquals(11.1, tappe[1].lon, 1e-6)
    }

    @Test
    fun `le coordinate sopravvivono al giro su file`() {
        val viaggio = archivio.creaViaggio(
            nome = "precisione",
            punti = listOf(Waypoint("x", 42.718512, 12.111299)),
            oggi = LocalDate.of(2026, 8, 6),
        )

        val tappa = archivio.tappe(viaggio.slug).single()

        // Sei decimali: sotto il metro non serve.
        assertEquals(42.718512, tappa.lat, 1e-6)
        assertEquals(12.111299, tappa.lon, 1e-6)
    }

    @Test
    fun `due viaggi con lo stesso nome nello stesso mese non si sovrascrivono`() {
        val primo = archivio.creaViaggio("Toscana", listOf(Waypoint("a", 43.0, 11.0)), oggi = GIORNO)
        val secondo = archivio.creaViaggio("Toscana", listOf(Waypoint("b", 44.0, 12.0)), oggi = GIORNO)

        assertNotEquals(primo.slug, secondo.slug)
        assertEquals("2026-08-toscana", primo.slug)
        assertEquals("2026-08-toscana-2", secondo.slug)
        assertEquals("a", archivio.tappe(primo.slug).single().nome)
        assertEquals("b", archivio.tappe(secondo.slug).single().nome)
    }

    @Test
    fun `l'elenco dei viaggi mette per primo il piu recente`() {
        archivio.creaViaggio(
            "vecchio", listOf(Waypoint("a", 43.0, 11.0)),
            oggi = LocalDate.of(2026, 5, 1),
            adesso = OffsetDateTime.parse("2026-05-01T10:00:00+02:00"),
        )
        archivio.creaViaggio(
            "nuovo", listOf(Waypoint("b", 43.0, 11.0)),
            oggi = GIORNO,
            adesso = OffsetDateTime.parse("2026-08-06T10:00:00+02:00"),
        )

        assertEquals(listOf("nuovo", "vecchio"), archivio.viaggi().map { it.nome })
    }

    @Test
    fun `eliminare un viaggio porta via la sua cartella`() {
        val viaggio = archivio.creaViaggio("da buttare", listOf(Waypoint("a", 43.0, 11.0)), oggi = GIORNO)

        archivio.elimina(viaggio.slug)

        assertTrue(archivio.viaggi().isEmpty())
        assertTrue(!archivio.cartellaViaggio(viaggio.slug).exists())
    }

    @Test
    fun `una cartella senza viaggio json non compare nell'elenco`() {
        File(archivio.cartellaViaggi(), "cartella-estranea").mkdirs()

        assertTrue(archivio.viaggi().isEmpty())
    }

    @Test
    fun `l'archivio si documenta da se`() {
        val formati = File(radice, "FORMATI.md")

        assertTrue(formati.exists())
        val testo = formati.readText()
        assertTrue(testo.contains("tappe.csv"))
        assertTrue(testo.contains("`cancellato`"))
    }

    private companion object {
        val GIORNO: LocalDate = LocalDate.of(2026, 8, 6)
    }
}
