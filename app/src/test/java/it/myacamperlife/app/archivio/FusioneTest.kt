package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.Waypoint
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
 * La fusione di due archivi.
 *
 * E' la parte piu' delicata dell'app: tocca dati che l'utente non puo'
 * ricostruire. Ogni prova qui dentro sorveglia una cosa che, sbagliata, si
 * scoprirebbe solo dopo aver perso qualcosa — e in particolare le tre che
 * temo: **una riga cancellata che torna in vita**, **una correzione perduta**,
 * **impostazioni sovrascritte**.
 *
 * Si lavora su due cartelle vere e su `AlberoDiFile`: e' esattamente la stessa
 * logica che sul telefono legge da SAF, ed e' il motivo per cui quell'astrazione
 * esiste.
 */
class FusioneTest {

    private lateinit var qui: File
    private lateinit var fuori: File
    private lateinit var app: Archivio
    private lateinit var altro: Archivio

    private val ieri: OffsetDateTime = OffsetDateTime.parse("2026-08-07T10:00:00+02:00")
    private val oggi: OffsetDateTime = OffsetDateTime.parse("2026-08-08T10:00:00+02:00")
    private val domani: OffsetDateTime = OffsetDateTime.parse("2026-08-09T10:00:00+02:00")

    @Before
    fun prepara() {
        qui = cartella("qui")
        fuori = cartella("fuori")
        app = Archivio(qui).also { it.prepara() }
        altro = Archivio(fuori).also { it.prepara() }
    }

    @After
    fun pulisci() {
        qui.deleteRecursively()
        fuori.deleteRecursively()
    }

    private fun cartella(nome: String): File = File.createTempFile(nome, "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private fun fondi(): EsitoFusione = Fusione(app).fondi(AlberoDiFile(fuori))

    /** Crea lo stesso viaggio nei due archivi, con lo stesso slug. */
    private fun viaggioInEntrambi(): String {
        val creato = altro.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = ieri,
        )
        app.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = ieri,
        )
        return creato.slug
    }

    // --- il caso che ha motivato tutto -----------------------------------------

    @Test
    fun `un'app vuota adotta i viaggi della cartella`() {
        val slug = altro.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = ieri,
        ).slug
        altro.registraNota(slug, "una nota del telefono vecchio", adesso = ieri)
        altro.registraSpesa(slug, Categoria.SOSTA, 18.0, Modalita.CONTANTI, adesso = ieri)

        assertTrue(app.viaggi().isEmpty())
        val esito = fondi()

        assertEquals(1, esito.viaggiNuovi)
        assertEquals(1, app.viaggi().size)
        assertEquals("Toscana", app.viaggi().single().nome)
        assertEquals(1, app.tappe(slug).size)
        assertEquals(18.0, app.conto(slug).totale, 1e-9)
        assertTrue(app.voci(slug).any { it.testo == "una nota del telefono vecchio" })
    }

    @Test
    fun `il diario si rigenera dalle tabelle fuse`() {
        val slug = altro.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = ieri,
        ).slug
        altro.registraNota(slug, "vino a Orvieto", adesso = ieri)

        fondi()
        assertTrue(app.diario(slug).testo().contains("vino a Orvieto"))
    }

    @Test
    fun `una cartella vuota non fa niente`() {
        val esito = Fusione(app).fondi(AlberoDiFile(cartella("niente")))
        assertTrue(!esito.qualcosa)
        assertEquals(0, esito.falliti)
    }

    // --- le tre cose che temo -------------------------------------------------

    @Test
    fun `una riga cancellata qui non torna in vita dalla cartella`() {
        val slug = viaggioInEntrambi()
        // La stessa nota nei due archivi: stesso id, perche' la cartella e' una
        // copia di questo archivio fatta prima della cancellazione.
        app.registraNota(slug, "da cancellare", adesso = ieri)
        val id = app.voci(slug).single { it.genere == Genere.NOTA }.id!!
        copiaTabella(slug, NoteTabella.NOME_FILE)

        // Qui la si cancella; nella cartella e' ancora viva.
        app.cancellaVoce(slug, Genere.NOTA, id, adesso = oggi)
        assertTrue(app.voci(slug).none { it.genere == Genere.NOTA })

        fondi()

        // **La prova che conta.** Buttando le lapidi tornerebbe, e tornerebbe a
        // ogni fusione successiva.
        assertTrue(app.voci(slug).none { it.genere == Genere.NOTA })
        // E la lapide resta scritta, cosi' la nega anche la prossima volta.
        assertTrue(app.tabellaNote(slug).righe().any { it.id == id && it.cancellata })
        fondi()
        assertTrue(app.voci(slug).none { it.genere == Genere.NOTA })
    }

    @Test
    fun `una riga cancellata nella cartella resta cancellata anche qui`() {
        val slug = viaggioInEntrambi()
        altro.registraNota(slug, "cancellata di la", adesso = ieri)
        val id = altro.voci(slug).single { it.genere == Genere.NOTA }.id!!
        // Prima si copia la nota viva dentro l'app, poi di la' la si cancella.
        copiaVerso(slug, NoteTabella.NOME_FILE, da = fuori, a = qui)
        altro.cancellaVoce(slug, Genere.NOTA, id, adesso = oggi)

        assertTrue(app.voci(slug).any { it.genere == Genere.NOTA })
        fondi()
        assertTrue(app.voci(slug).none { it.genere == Genere.NOTA })
    }

    @Test
    fun `fra due versioni della stessa riga vince la piu' recente`() {
        val slug = viaggioInEntrambi()
        app.registraSpesa(slug, Categoria.SOSTA, 18.0, Modalita.CONTANTI, adesso = ieri)
        val id = app.spese(slug).single().id
        copiaTabella(slug, SpeseTabella.NOME_FILE)

        // Correzione **piu' vecchia** nella cartella, e una piu' nuova qui.
        altro.correggiSpesa(
            slug, id, Categoria.SOSTA, 20.0, Modalita.CONTANTI, null, "EUR", null,
            istante = ieri, adesso = oggi,
        )
        app.correggiSpesa(
            slug, id, Categoria.SOSTA, 25.0, Modalita.CONTANTI, null, "EUR", null,
            istante = ieri, adesso = domani,
        )

        fondi()
        assertEquals(25.0, app.spese(slug).single().importo, 1e-9)
    }

    @Test
    fun `una correzione fatta fuori arriva dentro`() {
        val slug = viaggioInEntrambi()
        app.registraSpesa(slug, Categoria.SOSTA, 18.0, Modalita.CONTANTI, adesso = ieri)
        val id = app.spese(slug).single().id
        copiaTabella(slug, SpeseTabella.NOME_FILE)

        altro.correggiSpesa(
            slug, id, Categoria.SOSTA, 22.0, Modalita.CONTANTI, "corretta fuori", "EUR", null,
            istante = ieri, adesso = domani,
        )

        fondi()
        assertEquals(22.0, app.spese(slug).single().importo, 1e-9)
        assertEquals("corretta fuori", app.spese(slug).single().descrizione)
    }

    @Test
    fun `le impostazioni della cartella si prendono solo se qui sono intatte`() {
        altro.salvaImpostazioni(Impostazioni(kmConUnPieno = 850, oraBriefing = 20))

        val esito = fondi()
        assertTrue(esito.impostazioni)
        assertEquals(850, app.impostazioni().kmConUnPieno)
        assertEquals(20, app.impostazioni().oraBriefing)
    }

    @Test
    fun `le impostazioni gia' scelte qui non si toccano`() {
        app.salvaImpostazioni(Impostazioni(kmConUnPieno = 600))
        altro.salvaImpostazioni(Impostazioni(kmConUnPieno = 850))

        val esito = fondi()
        assertTrue(!esito.impostazioni)
        // Sovrascriverle sarebbe decidere al posto dell'utente, e sceglierebbe
        // male tanto quanto il codice di prima che le cancellava.
        assertEquals(600, app.impostazioni().kmConUnPieno)
    }

    @Test
    fun `la cartella scelta resta quella nuova, non quella di un'installazione morta`() {
        app.salvaImpostazioni(Impostazioni(cartellaSpecchio = "content://nuova"))
        altro.salvaImpostazioni(
            Impostazioni(kmConUnPieno = 850, cartellaSpecchio = "content://vecchia-e-inaccessibile"),
        )

        fondi()
        // I km arrivano — qui non erano stati scelti — ma l'Uri no: su quello il
        // permesso e' perduto insieme all'installazione di prima.
        assertEquals(850, app.impostazioni().kmConUnPieno)
        assertEquals("content://nuova", app.impostazioni().cartellaSpecchio)
    }

    @Test
    fun `una traccia di diagnostica non conta come impostazione toccata`() {
        // Le righe di esito le scrive l'app, non l'utente: contarle renderebbe
        // l'archivio "gia' toccato" sempre, e la fusione non scatterebbe mai —
        // il difetto peggiore, perche' silenzioso.
        app.annotaModelli("Groq: 3 visibili — groq/compound")
        altro.salvaImpostazioni(Impostazioni(kmConUnPieno = 850))

        val esito = fondi()
        assertTrue(esito.impostazioni)
        assertEquals(850, app.impostazioni().kmConUnPieno)
        // E la traccia resta quella di **questa** installazione: e' la
        // diagnostica di questo telefono, non di quello di prima.
        assertTrue(app.impostazioni().modelliEsito!!.startsWith("Groq: 3 visibili"))
    }

    // --- gli allegati ---------------------------------------------------------

    @Test
    fun `le foto mancanti si copiano dentro`() {
        val slug = viaggioInEntrambi()
        val nome = "foto_20260807_100000_Orvieto.jpg"
        File(altro.cartellaFoto(slug), nome).writeBytes(byteArrayOf(1, 2, 3))
        altro.registraFoto(slug, nome, "Duomo", adesso = ieri)

        val esito = fondi()
        assertTrue(esito.allegati > 0)
        val copiata = File(app.cartellaFoto(slug), nome)
        assertTrue(copiata.isFile)
        assertEquals(3, copiata.length().toInt())
        assertEquals(nome, app.voci(slug).single { it.genere == Genere.FOTO }.allegato)
    }

    @Test
    fun `una foto che c'e' gia' non si sovrascrive`() {
        val slug = viaggioInEntrambi()
        val nome = "foto_20260807_100000_Orvieto.jpg"
        // Qui la foto vera, fuori una versione diversa e piu' corta: non c'e'
        // ragione di preferirla, e sovrascrivere un file che non si puo' rifare
        // e' l'unico errore davvero irreparabile.
        File(app.cartellaFoto(slug), nome).writeBytes(ByteArray(500) { 9 })
        File(altro.cartellaFoto(slug), nome).writeBytes(byteArrayOf(1))
        altro.registraFoto(slug, nome, null, adesso = ieri)

        fondi()
        assertEquals(500, File(app.cartellaFoto(slug), nome).length().toInt())
    }

    @Test
    fun `i dossier arrivano con il loro file`() {
        val slug = viaggioInEntrambi()
        val nome = "20260807_100000_dove-dormiamo.md"
        File(altro.cartellaDossier(slug), nome).writeText("# Dove dormiamo\n\nAll'area del lago.")
        altro.tabellaDossier(slug).accoda(
            mapOf(
                Csv.ID to "d1",
                Csv.TS to "2026-08-07T10:00:00+02:00",
                DossierTabella.ISTANTE to "2026-08-07T10:00:00+02:00",
                DossierTabella.DOMANDA to "Dove dormiamo?",
                DossierTabella.FILE to nome,
            ),
        )

        fondi()
        assertEquals(1, app.dossier(slug).size)
        assertNotNull(app.testoDossier(slug, nome))
        assertTrue(app.testoDossier(slug, nome)!!.contains("area del lago"))
    }

    @Test
    fun `la scorta si fonde come le altre tabelle`() {
        val slug = viaggioInEntrambi()
        altro.salvaDintorni(
            slug,
            it.myacamperlife.app.dominio.Dintorno(
                poi = listOf(
                    it.myacamperlife.app.dominio.Poi(
                        "node/1", "Area Lido",
                        it.myacamperlife.app.dominio.CategoriaPoi.SOSTA, 42.647, 11.99,
                    ),
                ),
                luoghi = listOf(it.myacamperlife.app.dominio.Luogo("Bolsena", 42.6437, 11.9871)),
            ),
            adesso = ieri,
        )

        fondi()
        assertEquals(1, app.poi(slug).size)
        assertEquals("Area Lido", app.poi(slug).single().nome)
    }

    // --- prudenza -------------------------------------------------------------

    @Test
    fun `un csv che questa versione non conosce non si tocca`() {
        val slug = viaggioInEntrambi()
        File(altro.cartellaViaggio(slug), "misteri.csv")
            .writeText("id;ts;cancellato;chissa\nx1;2026-08-07T10:00:00+02:00;;boh\n")

        val esito = fondi()
        // Si conta fra i falliti — e' un file che non siamo riusciti a trattare —
        // ma non lo si scrive da nessuna parte: potrebbe venire da una versione
        // piu' nuova dell'app, e fonderlo senza sapere che colonne abbia
        // significherebbe rovinarlo.
        assertTrue(esito.falliti > 0)
        assertTrue(!File(app.cartellaViaggio(slug), "misteri.csv").exists())
    }

    @Test
    fun `fondere due volte non cambia niente la seconda`() {
        val slug = viaggioInEntrambi()
        altro.registraNota(slug, "una nota", adesso = ieri)
        altro.registraSpesa(slug, Categoria.SPESA, 12.0, Modalita.POS, adesso = ieri)

        val prima = fondi()
        assertTrue(prima.qualcosa)

        val righePrima = app.tabellaNote(slug).righe().size
        val seconda = fondi()

        // Idempotente: nessuna riga nuova, e il file non e' cresciuto.
        assertEquals(0, seconda.righeNuove)
        assertEquals(0, seconda.allegati)
        assertEquals(righePrima, app.tabellaNote(slug).righe().size)
        assertEquals(1, app.voci(slug).count { it.genere == Genere.NOTA })
    }

    @Test
    fun `una colonna che l'app non conosce ancora sopravvive alla fusione`() {
        val slug = viaggioInEntrambi()
        app.registraNota(slug, "una nota", adesso = ieri)
        val id = app.voci(slug).single { it.genere == Genere.NOTA }.id!!

        // La copia di fuori viene da una versione futura: ha una colonna in piu'
        // e un `ts` piu' recente, quindi la sua riga vince.
        File(altro.cartellaViaggio(slug), NoteTabella.NOME_FILE).also { it.parentFile?.mkdirs() }
            .writeText(
                "id;ts;cancellato;testo;tappa;lat;lon;umore\n" +
                    "$id;2026-08-09T10:00:00+02:00;;una nota;;;;contento\n",
            )

        fondi()
        val riga = app.tabellaNote(slug).vive().single { it.id == id }
        assertEquals("contento", riga.testo("umore"))
        assertTrue(app.tabellaNote(slug).file.readText().contains("umore"))
    }

    @Test
    fun `un viaggio solo nostro non viene toccato`() {
        val nostro = app.creaViaggio(
            nome = "Puglia",
            punti = listOf(Waypoint("Bari", 41.1171, 16.8719)),
            oggi = LocalDate.parse("2026-09-01"),
            adesso = oggi,
        ).slug
        app.registraNota(nostro, "resta qui", adesso = oggi)

        altro.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = ieri,
        )

        fondi()
        assertEquals(2, app.viaggi().size)
        assertTrue(app.voci(nostro).any { it.testo == "resta qui" })
    }

    // --- appoggi --------------------------------------------------------------

    /** Copia una tabella dall'app alla cartella: simula uno specchio fatto prima. */
    private fun copiaTabella(slug: String, nome: String) = copiaVerso(slug, nome, qui, fuori)

    private fun copiaVerso(slug: String, nome: String, da: File, a: File) {
        val sorgente = File(File(File(da, "viaggi"), slug), nome)
        val destinazione = File(File(File(a, "viaggi"), slug), nome)
        destinazione.parentFile?.mkdirs()
        sorgente.copyTo(destinazione, overwrite = true)
    }
}

/** La regola di fusione da sola, che e' quindici righe e regge tutto il resto. */
class FondiTest {

    private fun riga(id: String, ts: String, testo: String, cancellata: Boolean = false) = Riga(
        mapOf(
            Csv.ID to id,
            Csv.TS to ts,
            Csv.CANCELLATO to if (cancellata) "si" else "",
            "testo" to testo,
        ),
    )

    @Test
    fun `per ogni id resta la versione piu' recente`() {
        val fuse = Tabella.fondi(
            listOf(riga("a", "2026-08-07T10:00:00+02:00", "vecchia")),
            listOf(riga("a", "2026-08-08T10:00:00+02:00", "nuova")),
        )
        assertEquals(1, fuse.size)
        assertEquals("nuova", fuse.single().testo("testo"))
    }

    @Test
    fun `l'ordine degli elenchi non conta, conta il ts`() {
        val vecchia = listOf(riga("a", "2026-08-07T10:00:00+02:00", "vecchia"))
        val nuova = listOf(riga("a", "2026-08-08T10:00:00+02:00", "nuova"))
        assertEquals("nuova", Tabella.fondi(vecchia, nuova).single().testo("testo"))
        assertEquals("nuova", Tabella.fondi(nuova, vecchia).single().testo("testo"))
    }

    @Test
    fun `a pari ts vince chi passa per secondo`() {
        val fuse = Tabella.fondi(
            listOf(riga("a", "2026-08-08T10:00:00+02:00", "loro")),
            listOf(riga("a", "2026-08-08T10:00:00+02:00", "nostra")),
        )
        assertEquals("nostra", fuse.single().testo("testo"))
    }

    @Test
    fun `le lapidi si tengono, ed e' tutta la differenza con risolvi`() {
        val righe = listOf(
            riga("a", "2026-08-07T10:00:00+02:00", "viva"),
            riga("a", "2026-08-08T10:00:00+02:00", "", cancellata = true),
        )
        // risolvi la butta, perche' serve a chi legge.
        assertTrue(Tabella.risolvi(righe).isEmpty())
        // fondi la tiene, perche' serve alla prossima fusione: senza, la riga
        // tornerebbe in vita dall'altra copia.
        assertEquals(1, Tabella.fondi(righe).size)
        assertTrue(Tabella.fondi(righe).single().cancellata)
    }

    @Test
    fun `una riga senza id non si fonde, perche' non si sa con cosa`() {
        val senzaId = Riga(mapOf(Csv.TS to "2026-08-08T10:00:00+02:00", "testo" to "orfana"))
        assertTrue(Tabella.fondi(listOf(senzaId)).isEmpty())
    }

    @Test
    fun `gli id che stanno solo in una copia arrivano tutti`() {
        val fuse = Tabella.fondi(
            listOf(riga("a", "2026-08-07T10:00:00+02:00", "loro")),
            listOf(riga("b", "2026-08-07T10:00:00+02:00", "nostra")),
        )
        assertEquals(setOf("a", "b"), fuse.mapNotNull { it.id }.toSet())
    }

    @Test
    fun `fondere una copia con se stessa non la raddoppia`() {
        val righe = listOf(
            riga("a", "2026-08-07T10:00:00+02:00", "una"),
            riga("b", "2026-08-07T10:00:00+02:00", "due"),
        )
        assertEquals(2, Tabella.fondi(righe, righe).size)
    }
}
