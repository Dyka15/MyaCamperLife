package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Waypoint
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La giornata di viaggio dal lato dell'archivio: check-in, posizioni, note,
 * foto, e il diario che ne viene fuori.
 */
class GiornataTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio
    private lateinit var slug: String

    @Before
    fun prepara() {
        radice = Files.createTempDirectory("giornata").toFile()
        archivio = Archivio(radice)
        archivio.prepara()
        slug = archivio.creaViaggio(
            nome = "Toscana",
            punti = listOf(
                Waypoint("Firenze", 43.7696, 11.2558, giorno = "2026-08-05"),
                Waypoint("Orvieto", 42.7185, 12.1112, giorno = "2026-08-06"),
                Waypoint("Roma", 41.9028, 12.4964, giorno = "2026-08-07"),
            ),
            oggi = LocalDate.of(2026, 8, 5),
            adesso = ORA_CREAZIONE,
        ).slug
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    private fun ora(giorno: Int, h: Int, m: Int): OffsetDateTime =
        OffsetDateTime.parse("2026-08-%02dT%02d:%02d:00+02:00".format(giorno, h, m))

    private fun tappa(nome: String) = archivio.tappe(slug).first { it.nome == nome }

    @Test
    fun `il check-in marca la tappa e registra l'arrivo`() {
        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 14, 12))

        assertEquals(StatoTappa.FATTA, tappa("Firenze").stato)
        assertEquals("2026-08-05T14:12:00+02:00", tappa("Firenze").checkinIl)

        val arrivo = archivio.tabellaSpostamenti(slug).vive().single()
        assertEquals(SpostamentiTabella.ARRIVO, arrivo.testo(SpostamentiTabella.GENERE))
        assertEquals("Firenze", arrivo.testo(SpostamentiTabella.TAPPA))
    }

    @Test
    fun `senza GPS il check-in usa le coordinate della tappa`() {
        archivio.checkin(slug, tappa("Orvieto"), posizione = null, adesso = ora(6, 14, 0))

        val arrivo = archivio.tabellaSpostamenti(slug).vive().single()
        assertEquals(42.7185, arrivo.numero(SpostamentiTabella.LAT)!!, 1e-6)
    }

    @Test
    fun `con il GPS il check-in usa la posizione vera`() {
        archivio.checkin(slug, tappa("Orvieto"), Posizione(42.7000, 12.1000), ora(6, 14, 0))

        val arrivo = archivio.tabellaSpostamenti(slug).vive().single()
        assertEquals(42.7000, arrivo.numero(SpostamentiTabella.LAT)!!, 1e-6)
    }

    @Test
    fun `salta e ripristina scrivono una riga per volta e non perdono la storia`() {
        archivio.alternaSalto(slug, tappa("Orvieto"), ora(6, 9, 0))
        assertEquals(StatoTappa.SALTATA, tappa("Orvieto").stato)

        archivio.alternaSalto(slug, tappa("Orvieto"), ora(6, 10, 0))
        assertEquals(StatoTappa.DA_FARE, tappa("Orvieto").stato)

        val righe = archivio.tabellaTappe(slug).righe()
        assertEquals("tre tappe piu' due correzioni", 5, righe.size)
    }

    @Test
    fun `una tappa fatta non si salta e non sporca il file`() {
        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 14, 0))
        val prima = archivio.tabellaTappe(slug).righe().size

        archivio.alternaSalto(slug, tappa("Firenze"), ora(5, 15, 0))

        assertEquals(prima, archivio.tabellaTappe(slug).righe().size)
        assertEquals(StatoTappa.FATTA, tappa("Firenze").stato)
    }

    @Test
    fun `aggiungere una tappa in mezzo rinumera e riscrive solo il necessario`() {
        val prima = archivio.tabellaTappe(slug).righe().size

        archivio.aggiungiTappa(
            slug, nome = "Bolsena", lat = 42.6437, lon = 11.9871,
            giorno = "2026-08-06", primaDi = tappa("Roma").id, adesso = ora(6, 12, 0),
        )

        assertEquals(
            listOf("Firenze", "Orvieto", "Bolsena", "Roma"),
            archivio.tappe(slug).map { it.nome },
        )
        assertEquals(listOf(1, 2, 3, 4), archivio.tappe(slug).map { it.ordine })

        // Firenze e Orvieto non si muovono: due righe nuove, non quattro.
        assertEquals(prima + 2, archivio.tabellaTappe(slug).righe().size)
    }

    @Test
    fun `aggiungere una tappa in fondo`() {
        archivio.aggiungiTappa(slug, "Napoli", 40.8518, 14.2681, adesso = ora(8, 10, 0))

        assertEquals("Napoli", archivio.tappe(slug).last().nome)
        assertEquals(4, archivio.tappe(slug).last().ordine)
    }

    @Test
    fun `nota e foto prendono il nome della tappa dove sei`() {
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 0))
        archivio.registraNota(slug, "comprato il pane", adesso = ora(6, 18, 5))
        archivio.registraFoto(slug, "foto_20260806_154000_Orvieto.jpg", "Duomo", adesso = ora(6, 15, 40))

        assertEquals("Orvieto", archivio.tabellaNote(slug).vive().single().testo(NoteTabella.TAPPA))
        assertEquals("Orvieto", archivio.tabellaFoto(slug).vive().single().testo(FotoTabella.TAPPA))
    }

    @Test
    fun `una nota vuota non viene registrata`() {
        archivio.registraNota(slug, "   ", adesso = ora(6, 18, 0))

        assertTrue(archivio.tabellaNote(slug).vive().isEmpty())
    }

    @Test
    fun `le voci del viaggio escono in ordine di ora attraverso le tre tabelle`() {
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 12))
        archivio.registraNota(slug, "comprato il pane", adesso = ora(6, 18, 5))
        archivio.registraFoto(slug, "duomo.jpg", "Duomo", adesso = ora(6, 15, 40))
        archivio.registraPosizione(slug, Posizione(42.7, 12.1), adesso = ora(6, 16, 30))

        val voci = archivio.voci(slug)

        assertEquals(
            listOf(Genere.ARRIVO, Genere.FOTO, Genere.POSIZIONE, Genere.NOTA),
            voci.map { it.genere },
        )
    }

    @Test
    fun `il diario nasce con il titolo del viaggio e la sezione del giorno`() {
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 12))

        val diario = archivio.diario(slug).testo()

        assertTrue(diario.startsWith("# Toscana"))
        assertTrue(diario.contains("## 2026-08-06 — giovedì 6 agosto 2026, Orvieto"))
        assertTrue(diario.contains("- 14:12 · arrivo a Orvieto"))
    }

    @Test
    fun `ogni evento aggiorna il diario del suo giorno senza toccare gli altri`() {
        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 10, 0))
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 12))
        archivio.registraNota(slug, "comprato il pane", adesso = ora(6, 18, 5))

        val diario = archivio.diario(slug).testo()

        assertTrue(diario.contains("## 2026-08-05"))
        assertTrue(diario.contains("- 10:00 · arrivo a Firenze"))
        assertTrue(diario.contains("## 2026-08-06"))
        assertTrue(diario.contains("- 18:05 · comprato il pane"))
        assertTrue(
            "il 5 agosto viene prima del 6",
            diario.indexOf("## 2026-08-05") < diario.indexOf("## 2026-08-06"),
        )
    }

    @Test
    fun `rigenerare il diario da lo stesso file`() {
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 12))
        archivio.registraNota(slug, "una nota", adesso = ora(6, 18, 0))
        val prima = archivio.diario(slug).testo()

        archivio.rigeneraDiario(slug)

        assertEquals(prima, archivio.diario(slug).testo())
    }

    @Test
    fun `il diario si ricostruisce se il file viene perso`() {
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 12))
        val atteso = archivio.diario(slug).testo()

        File(archivio.cartellaViaggio(slug), "diario.md").delete()
        assertEquals("", archivio.diario(slug).testo())

        archivio.rigeneraDiario(slug)

        assertEquals(atteso, archivio.diario(slug).testo())
    }

    @Test
    fun `la cartella delle foto si crea quando serve`() {
        val cartella = archivio.cartellaFoto(slug)

        assertTrue(cartella.isDirectory)
        assertEquals("foto", cartella.name)
    }

    @Test
    fun `il luogo corrente e l'ultima tappa fatta`() {
        assertNull(archivio.luogo(slug))

        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 10, 0))
        assertEquals("Firenze", archivio.luogo(slug))

        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 14, 0))
        assertEquals("Orvieto", archivio.luogo(slug))
    }

    @Test
    fun `una virgola in una nota non spezza il file`() {
        archivio.registraNota(slug, "pane, latte, e una cosa; poi il resto", adesso = ora(6, 18, 0))

        val riga = archivio.tabellaNote(slug).vive().single()
        assertEquals("pane, latte, e una cosa; poi il resto", riga.testo(NoteTabella.TESTO))
    }

    @Test
    fun `una nota su piu righe resta un solo record`() {
        archivio.registraNota(slug, "prima riga\nseconda riga", adesso = ora(6, 18, 0))

        val file = File(archivio.cartellaViaggio(slug), NoteTabella.NOME_FILE)
        assertEquals("intestazione piu' una riga", 2, file.readLines().size)
        assertFalse(archivio.tabellaNote(slug).vive().single().testo(NoteTabella.TESTO)!!.contains('\n'))
    }

    // --- il luogo nell'intestazione della giornata ------------------------------

    @Test
    fun `un giorno senza arrivi resta dove eri, non dove sei adesso`() {
        // Il difetto che questo sorveglia, visto su un diario vero: il 6 agosto
        // non aveva arrivi, e l'intestazione portava il nome del posto in cui si
        // era arrivati **una settimana dopo** — perche' il ripiego era la tappa
        // corrente, cioe' l'adesso. Un diario di sei giorni prima non sa niente
        // dell'adesso: se non ti sei mosso, quel giorno eri dove eri arrivato.
        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 14, 0))
        archivio.registraNota(slug, "giornata di riposo", adesso = ora(6, 11, 0))
        archivio.checkin(slug, tappa("Roma"), adesso = ora(7, 18, 0))

        val diario = archivio.diario(slug).testo()
        assertTrue(diario, diario.contains("## 2026-08-05 — mercoledì 5 agosto 2026, Firenze"))
        // Il 6 si e' rimasti a Firenze, e l'intestazione lo dice.
        assertTrue(diario, diario.contains("## 2026-08-06 — giovedì 6 agosto 2026, Firenze"))
        assertTrue(diario, diario.contains("## 2026-08-07 — venerdì 7 agosto 2026, Roma"))
    }

    @Test
    fun `prima del primo arrivo l'intestazione non inventa un luogo`() {
        // Una nota registrata prima di qualunque check-in: dove si era non si sa,
        // e una riga senza luogo e' meglio di una con un luogo sbagliato.
        archivio.registraNota(slug, "partenza in ritardo", adesso = ora(5, 9, 0))
        archivio.checkin(slug, tappa("Orvieto"), adesso = ora(6, 17, 0))

        val diario = archivio.diario(slug).testo()
        assertTrue(diario, diario.contains("## 2026-08-05 — mercoledì 5 agosto 2026\n"))
        assertTrue(diario, diario.contains("## 2026-08-06 — giovedì 6 agosto 2026, Orvieto"))
    }

    @Test
    fun `rigenerare il diario non cambia i luoghi delle giornate`() {
        // La rigenerazione riscrive tutte le sezioni: se la regola del luogo
        // dipendesse dall'adesso, ogni rigenerazione le riscriverebbe diverse — ed
        // e' esattamente cosi' che il difetto si e' fatto notare, «anche dopo la
        // rigenerazione le tappe non corrispondono».
        archivio.checkin(slug, tappa("Firenze"), adesso = ora(5, 14, 0))
        archivio.registraNota(slug, "riposo", adesso = ora(6, 11, 0))
        val prima = archivio.diario(slug).testo()

        archivio.rigeneraDiario(slug)

        assertEquals(prima, archivio.diario(slug).testo())
    }

    private companion object {
        val ORA_CREAZIONE: OffsetDateTime = OffsetDateTime.parse("2026-08-05T08:00:00+02:00")
    }
}
