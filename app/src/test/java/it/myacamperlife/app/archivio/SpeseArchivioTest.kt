package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Spesa
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeseArchivioTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio
    private lateinit var slug: String

    @Before
    fun prepara() {
        radice = File.createTempFile("spese", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        archivio = Archivio(radice)
        archivio.prepara()
        slug = archivio.creaViaggio(
            nome = "Svizzera",
            punti = listOf(Waypoint("Lugano", 46.0, 8.95)),
            oggi = LocalDate.parse("2026-08-01"),
            adesso = OffsetDateTime.parse("2026-08-01T09:00:00+02:00"),
        ).slug
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    private fun quando(giorno: String, ora: String = "12:00:00") =
        OffsetDateTime.parse("${giorno}T$ora+02:00")

    // --- scrivere e rileggere ------------------------------------------------

    @Test
    fun `una spesa in euro si rilegge come e stata scritta`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SOSTA,
            importo = 18.0,
            modalita = Modalita.CONTANTI,
            descrizione = "area Il Cipresso",
            adesso = quando("2026-08-06"),
        )

        val spesa = archivio.spese(slug).single()
        assertEquals(Categoria.SOSTA, spesa.categoria)
        assertEquals(18.0, spesa.importo, 1e-9)
        assertEquals(18.0, spesa.euro, 1e-9)
        assertEquals(Modalita.CONTANTI, spesa.modalita)
        assertEquals("area Il Cipresso", spesa.descrizione)
        assertEquals("EUR", spesa.valuta)
        assertNull(spesa.cambio)
    }

    @Test
    fun `una spesa in valuta estera conserva l importo dello scontrino`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.RISTORANTE,
            importo = 45.0,
            modalita = Modalita.CARTA,
            valuta = "chf",
            cambio = 1.06,
            adesso = quando("2026-08-06"),
        )

        val spesa = archivio.spese(slug).single()
        assertEquals("CHF", spesa.valuta)
        assertEquals(45.0, spesa.importo, 1e-9)
        assertEquals(1.06, spesa.cambio!!, 1e-9)
        assertEquals(47.7, spesa.euro, 1e-9)
    }

    @Test
    fun `nel file l importo ha la virgola e la colonna euro e gia calcolata`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.RISTORANTE,
            importo = 45.0,
            modalita = Modalita.CARTA,
            valuta = "CHF",
            cambio = 1.06,
            adesso = quando("2026-08-06"),
        )

        val riga = archivio.tabellaSpese(slug).vive().single()
        assertEquals("45,00", riga.mappa()[SpeseTabella.IMPORTO])
        assertEquals("1,0600", riga.mappa()[SpeseTabella.CAMBIO])
        assertEquals("47,70", riga.mappa()[SpeseTabella.EURO])
        assertEquals("chf", riga.mappa()[SpeseTabella.VALUTA]?.lowercase())
    }

    @Test
    fun `la spesa registra la tappa in cui ti trovi`() {
        val tappa = archivio.tappe(slug).single()
        archivio.checkin(slug, tappa, adesso = quando("2026-08-06", "10:00:00"))
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SPESA,
            importo = 12.0,
            modalita = Modalita.POS,
            adesso = quando("2026-08-06", "11:00:00"),
        )
        assertEquals("Lugano", archivio.spese(slug).single().tappa)
    }

    @Test
    fun `una descrizione su piu righe finisce su una riga sola`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.ALTRO,
            importo = 3.0,
            modalita = Modalita.CONTANTI,
            descrizione = "prima riga\nseconda riga",
            adesso = quando("2026-08-06"),
        )
        val righe = File(archivio.cartellaViaggio(slug), SpeseTabella.NOME_FILE)
            .readLines()
            .filter { it.isNotBlank() }
        assertEquals(2, righe.size)
        assertEquals("prima riga seconda riga", archivio.spese(slug).single().descrizione)
    }

    @Test
    fun `un file di spese piu vecchio si legge lo stesso`() {
        // Colonne minime: e' la promessa dell'intestazione letta per nome.
        File(archivio.cartellaViaggio(slug), SpeseTabella.NOME_FILE).writeText(
            "id;ts;cancellato;categoria;importo;modalita\n" +
                "a1;2026-08-06T12:00:00+02:00;;pedaggi;9,80;carta\n",
        )
        val spesa = archivio.spese(slug).single()
        assertEquals(Categoria.PEDAGGI, spesa.categoria)
        assertEquals(9.80, spesa.euro, 1e-9)
        assertEquals(Modalita.CARTA, spesa.modalita)
        assertEquals("EUR", spesa.valuta)
    }

    // --- il conto ------------------------------------------------------------

    @Test
    fun `il conto somma le spese e il carburante dei rifornimenti`() {
        archivio.registraSpesa(
            slug, Categoria.SOSTA, 18.0, Modalita.CONTANTI,
            adesso = quando("2026-08-06"),
        )
        archivio.registraSpesa(
            slug, Categoria.RISTORANTE, 32.0, Modalita.CARTA,
            adesso = quando("2026-08-06", "20:00:00"),
        )
        archivio.registraRifornimento(
            slug = slug, km = 48000, litri = 62.3, euro = 107.09,
            adesso = quando("2026-08-07", "08:40:00"),
        )

        val conto = archivio.conto(slug)
        assertEquals(50.0, conto.spese, 1e-9)
        assertEquals(107.09, conto.carburante, 1e-9)
        assertEquals(157.09, conto.totale, 1e-9)
        assertEquals(2, conto.voci)
        assertEquals(2, conto.giorni)
    }

    @Test
    fun `un rifornimento senza importo non falsa il conto`() {
        archivio.registraRifornimento(
            slug = slug, km = 48000, litri = 40.0, euro = null,
            adesso = quando("2026-08-06"),
        )
        assertEquals(0.0, archivio.conto(slug).carburante, 1e-9)
    }

    @Test
    fun `il conto di un viaggio senza niente e vuoto`() {
        assertTrue(archivio.conto(slug).vuoto)
    }

    // --- il diario -----------------------------------------------------------

    @Test
    fun `una spesa compare nel diario con importo e modalita`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SOSTA,
            importo = 18.0,
            modalita = Modalita.CONTANTI,
            descrizione = "area Il Cipresso",
            adesso = quando("2026-08-06", "19:30:00"),
        )

        val voce = archivio.voci(slug).single { it.genere == Genere.SPESA }
        assertEquals("sosta — area Il Cipresso: 18,00 € (contanti)", voce.testo)
        assertTrue(archivio.diario(slug).testo().contains("19:30 · sosta — area Il Cipresso"))
    }

    @Test
    fun `una spesa estera porta nel diario tutti e due i numeri`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.RISTORANTE,
            importo = 45.0,
            modalita = Modalita.CARTA,
            valuta = "CHF",
            cambio = 1.06,
            adesso = quando("2026-08-06", "20:15:00"),
        )
        val voce = archivio.voci(slug).single { it.genere == Genere.SPESA }
        assertEquals("ristorante: 45,00 CHF = 47,70 € (carta)", voce.testo)
    }

    // --- la posizione --------------------------------------------------------

    @Test
    fun `le coordinate di una spesa contano fra i punti del viaggio`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SPESA,
            importo = 5.0,
            modalita = Modalita.CONTANTI,
            posizione = Posizione(46.0037, 8.9511),
            adesso = quando("2026-08-06"),
        )
        val punto = archivio.punti(slug).single()
        assertEquals(46.0037, punto.lat, 1e-6)
        assertEquals(8.9511, punto.lon, 1e-6)
    }

    // --- lo scontrino --------------------------------------------------------

    @Test
    fun `lo scontrino si allega col nome del file`() {
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SPESA,
            importo = 24.0,
            modalita = Modalita.POS,
            scontrino = "scontrino_20260806_183000.jpg",
            adesso = quando("2026-08-06", "18:30:00"),
        )
        assertEquals(
            "scontrino_20260806_183000.jpg",
            archivio.spese(slug).single().scontrino,
        )
        assertTrue(archivio.cartellaScontrini(slug).isDirectory)
    }

    // --- il formato documentato ----------------------------------------------

    @Test
    fun `FORMATI descrive anche le spese`() {
        val formati = File(radice, "FORMATI.md").readText()
        assertTrue(formati.contains("## spese.csv"))
        assertTrue(formati.contains("`modalita`"))
        assertTrue(formati.contains("Il carburante non sta qui"))
    }

    @Test
    fun `la sigla della valuta di default e EUR`() {
        assertEquals("EUR", Spesa.EURO)
    }
}
