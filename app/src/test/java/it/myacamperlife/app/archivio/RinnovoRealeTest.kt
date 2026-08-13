package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.GiornoTappa
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.StatoTappa
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Riscrivere il seguito del viaggio, **sui due file veri**.
 *
 * Le altre prove del rinnovo lavorano su tre tappe inventate, e vanno bene per le
 * regole. Questa lavora sui documenti che chi viaggia ha caricato davvero — un
 * itinerario di diciotto giorni e la sua riprogrammazione dal 13 agosto, cinquanta
 * kilobyte di Markdown scritti da un altro — e verifica la cosa che si e' chiesta
 * a parole: *arrivato al 13/8 riscrivo il seguito, e quello che ho registrato non
 * si perde.*
 *
 * E' la prova che mancava quando la funzione «non funzionava»: il difetto non era
 * nella scrittura, era nel gesto per arrivarci. Averla scritta prima non l'avrebbe
 * trovato — ma averla adesso significa che la scrittura non tornera' a rompersi in
 * silenzio.
 */
class RinnovoRealeTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio
    private lateinit var slug: String

    /** Il giorno in cui si riscrive: si e' arrivati a Rothenburg il 12. */
    private val tredici: LocalDate = LocalDate.parse("2026-08-13")

    private fun documento(nome: String): String = requireNotNull(
        javaClass.classLoader?.getResourceAsStream(nome),
    ) { "manca l'itinerario di prova: $nome" }.use { it.readBytes().toString(Charsets.UTF_8) }

    private val vecchio: String get() = documento("baviera-bratislava-istria-2026.md")
    private val nuovo: String get() = documento("baviera-udine-umago-dal-13-8.md")

    @Before
    fun prepara() {
        radice = File.createTempFile("rinnovo", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        archivio = Archivio(radice)
        archivio.prepara()

        val letto = Itinerario.leggi(vecchio) as Itinerario.Esito.Riuscito
        slug = archivio.creaViaggio(
            nome = "Baviera, Bratislava e Istria",
            punti = letto.tappe,
            oggi = LocalDate.parse("2026-08-06"),
            adesso = OffsetDateTime.parse("2026-08-06T09:00:00+02:00"),
            documento = vecchio,
        ).slug

        // Il viaggio vissuto fino al 12: check-in su tutto quello che e' passato,
        // piu' una nota e una spesa — le cose che non devono sparire.
        archivio.tappe(slug)
            .filter { tappa ->
                GiornoTappa.leggi(tappa.giorno, tredici)?.isBefore(tredici) == true
            }
            .forEach { tappa ->
                archivio.checkin(slug, tappa, adesso = OffsetDateTime.parse("2026-08-12T18:00:00+02:00"))
            }
        archivio.registraNota(
            slug = slug,
            testo = "Birra a Rothenburg",
            adesso = OffsetDateTime.parse("2026-08-12T20:00:00+02:00"),
        )
        archivio.registraSpesa(
            slug = slug,
            categoria = Categoria.SOSTA,
            importo = 24.0,
            modalita = Modalita.CONTANTI,
            adesso = OffsetDateTime.parse("2026-08-12T21:00:00+02:00"),
        )
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    private fun rinnova() = archivio.sostituisciTappe(
        slug = slug,
        punti = (Itinerario.leggi(nuovo) as Itinerario.Esito.Riuscito).tappe,
        documento = nuovo,
        adesso = OffsetDateTime.parse("2026-08-13T08:00:00+02:00"),
    )

    // --- l'itinerario, prima e dopo --------------------------------------------

    @Test
    fun `il file vecchio si legge per intero`() {
        val letto = Itinerario.leggi(vecchio) as Itinerario.Esito.Riuscito
        assertEquals(21, letto.tappe.size)
        assertEquals(0, letto.scartati)
    }

    @Test
    fun `il file nuovo si legge per intero`() {
        val letto = Itinerario.leggi(nuovo) as Itinerario.Esito.Riuscito
        assertEquals(13, letto.tappe.size)
        assertEquals(0, letto.scartati)
        assertEquals("13/8", letto.tappe.first().giorno)
    }

    @Test
    fun `le tappe del piano vecchio escono, quelle del nuovo entrano`() {
        val prima = archivio.tappe(slug).map { it.nome }
        assertTrue(prima.any { it.contains("Norimberga") })

        rinnova()

        val dopo = archivio.tappe(slug)
        // Il piano vecchio, dal 13 in poi, non c'e' piu': Norimberga era il 14/8,
        // Bratislava il 17, Graz il 20.
        assertTrue(
            dopo.map { it.nome }.toString(),
            dopo.none {
                it.nome.contains("Norimberga") ||
                    it.nome.contains("Bratislava") ||
                    it.nome.contains("Graz")
            },
        )
        // Il piano nuovo c'e'.
        assertTrue(dopo.any { it.nome.contains("Bamberga") })
        assertTrue(dopo.any { it.nome.contains("Udine") })
        assertTrue(dopo.any { it.nome.contains("Umago") })
    }

    @Test
    fun `le tappe fatte restano, con i loro giorni`() {
        rinnova()

        val fatte = archivio.tappe(slug).filter { it.stato == StatoTappa.FATTA }
        assertEquals(9, fatte.size)
        assertTrue(fatte.map { it.nome }.toString(), fatte.any { it.nome.contains("Monaco") })
        // Il giorno di una tappa fatta non si tocca: e' storia, non un'ipotesi.
        assertEquals("9/8", fatte.first { it.nome.contains("Monaco") }.giorno)
    }

    @Test
    fun `i numeri d'ordine restano densi e in fila`() {
        rinnova()
        val ordini = archivio.tappe(slug).map { it.ordine }
        assertEquals((1..ordini.size).toList(), ordini)
    }

    // --- quello che non deve andare perso --------------------------------------

    @Test
    fun `il diario, la nota e la spesa sopravvivono al rinnovo`() {
        val vociPrima = archivio.voci(slug).size
        rinnova()

        // **La promessa che conta.** Gli eventi vivono in altre tabelle e non
        // hanno niente da cambiare: un check-in dice «sono arrivato a Rothenburg
        // il 12 agosto», e resta vero qualunque cosa dica l'itinerario di domani.
        assertEquals(vociPrima, archivio.voci(slug).size)
        assertTrue(archivio.voci(slug).any { it.testo.contains("Birra a Rothenburg") })
        assertEquals(24.0, archivio.spese(slug).single().importo, 1e-9)
        assertEquals(9, archivio.tappe(slug).count { it.stato == StatoTappa.FATTA })
    }

    @Test
    fun `nel file non si cancella niente`() {
        rinnova()
        val testo = File(archivio.cartellaViaggio(slug), TappeTabella.NOME_FILE)
            .readText(Charsets.UTF_8)
        // La riga di Norimberga resta scritta, con la sua lapide accanto: sparisce
        // dall'itinerario, non dall'archivio.
        assertTrue(testo.contains("Norimberga"))
    }

    // --- la traccia di cosa e' successo ----------------------------------------

    @Test
    fun `la sostituzione lascia scritto cosa ha fatto e su quale viaggio`() {
        // **La riga che risponde alla domanda «cos'ha fatto?»**. Un file caricato
        // puo' diventare un viaggio nuovo o il seguito di uno che c'era, e quando
        // sullo schermo compare altro da quello che si aspettava non c'e' nessun
        // altro posto dove guardare — non su un telefono, in viaggio.
        archivio.annotaImport("seguito di «Baviera» ($slug): 12 fuori, 13 dentro, 9 restate")

        val impostazioni = archivio.impostazioni()
        assertTrue(impostazioni.importEsito!!, impostazioni.importEsito!!.contains("12 fuori"))
        assertTrue(impostazioni.importEsito!!.contains(slug))
        assertNotNull(impostazioni.importProvatoIl)
    }

    @Test
    fun `annotare l'import non cancella le altre impostazioni`() {
        archivio.salvaImpostazioni(archivio.impostazioni().copy(kmConUnPieno = 700))
        archivio.annotaImport("viaggio nuovo")
        assertEquals(700, archivio.impostazioni().kmConUnPieno)
    }

    // --- il programma delle giornate -------------------------------------------

    @Test
    fun `dal 13 in poi il programma e' quello del file nuovo`() {
        rinnova()
        val programma = archivio.programma(slug, tredici)

        val quattordici = programma.first { it.giorno == LocalDate.parse("2026-08-14") }
        // Il 14 agosto il piano vecchio diceva Norimberga, il nuovo dice Bamberga.
        assertTrue(quattordici.testo, quattordici.testo.contains("Bamberga"))
        assertTrue(quattordici.testo, !quattordici.testo.contains("Norimberga"))
    }

    @Test
    fun `i giorni passati restano raccontati dal file vecchio`() {
        rinnova()
        val programma = archivio.programma(slug, tredici)

        // Il file nuovo comincia il 13: del 10 agosto a Monaco parla solo quello
        // vecchio, e quel racconto e' ancora l'unico che c'e'. Buttarlo perche' e'
        // arrivato un file nuovo vorrebbe dire perdere il diario del viaggio.
        val dieci = programma.firstOrNull { it.giorno == LocalDate.parse("2026-08-10") }
        assertNotNull("il 10 agosto non deve sparire dal programma", dieci)
        assertTrue(dieci!!.testo, dieci.testo.contains("Marienplatz"))
    }
}
