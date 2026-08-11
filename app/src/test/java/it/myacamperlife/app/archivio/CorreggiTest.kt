package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.Categoria
import it.myacamperlife.app.dominio.Genere
import it.myacamperlife.app.dominio.Modalita
import it.myacamperlife.app.dominio.StatoTappa
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

/**
 * Correggere e cancellare: il formato lo prevedeva dal primo giorno, queste
 * prove verificano che le funzioni lo usino come si deve.
 *
 * La regola che ogni prova sorveglia e' sempre la stessa: **la riga nuova
 * sostituisce la vecchia per intero**, quindi una correzione parziale deve
 * portarsi dietro tutto quello che non ha toccato.
 */
class CorreggiTest {

    private lateinit var radice: File
    private lateinit var archivio: Archivio
    private lateinit var slug: String

    private val ieri: OffsetDateTime = OffsetDateTime.parse("2026-08-07T11:00:00+02:00")
    private val oggi: OffsetDateTime = OffsetDateTime.parse("2026-08-08T18:00:00+02:00")

    @Before
    fun prepara() {
        radice = File.createTempFile("correggi", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
        archivio = Archivio(radice)
        archivio.prepara()
        slug = archivio.creaViaggio(
            nome = "Toscana",
            punti = listOf(Waypoint("Orvieto", 42.7185, 12.1112)),
            oggi = LocalDate.parse("2026-08-06"),
            adesso = OffsetDateTime.parse("2026-08-06T09:00:00+02:00"),
        ).slug
    }

    @After
    fun pulisci() {
        radice.deleteRecursively()
    }

    // --- cancellare -----------------------------------------------------------

    @Test
    fun `cancellare una nota la fa sparire dalle voci`() {
        val s = slug
        archivio.registraNota(s, "comprato il pane", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        assertTrue(archivio.cancellaVoce(s, Genere.NOTA, id, adesso = oggi))
        assertTrue(archivio.voci(s).none { it.genere == Genere.NOTA })
    }

    @Test
    fun `cancellare non distrugge la riga, ne accoda una lapide`() {
        val s = slug
        archivio.registraNota(s, "comprato il pane", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!
        archivio.cancellaVoce(s, Genere.NOTA, id, adesso = oggi)

        // Il testo originale e' ancora nel file: e' il punto del formato.
        val tutte = archivio.tabellaNote(s).righe()
        assertEquals(2, tutte.size)
        assertTrue(tutte.first().testo(NoteTabella.TESTO) == "comprato il pane")
        assertTrue(tutte.last().cancellata)
    }

    @Test
    fun `cancellare due volte non riesce la seconda`() {
        val s = slug
        archivio.registraNota(s, "una nota", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        assertTrue(archivio.cancellaVoce(s, Genere.NOTA, id, adesso = oggi))
        assertTrue(!archivio.cancellaVoce(s, Genere.NOTA, id, adesso = oggi))
    }

    @Test
    fun `un id inventato non cancella niente`() {
        assertTrue(!archivio.cancellaVoce(slug, Genere.SPESA, "non-esiste", adesso = oggi))
    }

    @Test
    fun `cancellare una spesa la toglie dal conto`() {
        val s = slug
        archivio.registraSpesa(s, Categoria.SOSTA, 18.0, Modalita.CONTANTI, adesso = oggi)
        archivio.registraSpesa(s, Categoria.RISTORANTE, 45.0, Modalita.CARTA, adesso = oggi)
        val id = archivio.spese(s).first { it.categoria == Categoria.RISTORANTE }.id

        archivio.cancellaVoce(s, Genere.SPESA, id, adesso = oggi)
        assertEquals(18.0, archivio.conto(s).totale, 1e-9)
    }

    @Test
    fun `cancellare un rifornimento lo toglie dai consumi`() {
        val s = slug
        archivio.registraRifornimento(s, km = 1000, euro = 100.0, prezzoLitro = 1.7, adesso = oggi)
        archivio.registraRifornimento(s, km = 1500, euro = 90.0, prezzoLitro = 1.7, adesso = oggi)
        val id = archivio.rifornimenti(s).last().id

        archivio.cancellaVoce(s, Genere.RIFORNIMENTO, id, adesso = oggi)
        assertEquals(1, archivio.rifornimenti(s).size)
    }

    @Test
    fun `cancellata dal diario, la giornata si riscrive`() {
        val s = slug
        archivio.registraNota(s, "una nota che sparira", adesso = oggi)
        assertTrue(archivio.diario(s).testo().contains("una nota che sparira"))

        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!
        archivio.cancellaVoce(s, Genere.NOTA, id, adesso = oggi)
        assertTrue(!archivio.diario(s).testo().contains("una nota che sparira"))
    }

    // --- l'orologio che va indietro -------------------------------------------

    /*
     * Due prove per un difetto che questa suite ha trovato nel codice appena
     * scritto: la lapide portava il `ts` di [adesso], e se quello era **piu'
     * vecchio** della riga da uccidere allora "vince l'ultima" la scartava. La
     * cancellazione non cancellava, e riferiva di essere riuscita.
     *
     * Non e' un caso di laboratorio: l'orologio di un telefono torna indietro
     * dopo una sincronizzazione, e in viaggio si cambia fuso.
     */

    @Test
    fun `cancellare funziona anche con l'orologio indietro`() {
        val s = slug
        archivio.registraNota(s, "una nota di oggi", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        // Ieri: piu' vecchio della riga che deve uccidere.
        assertTrue(archivio.cancellaVoce(s, Genere.NOTA, id, adesso = ieri))
        assertTrue(archivio.voci(s).none { it.genere == Genere.NOTA })
    }

    @Test
    fun `correggere funziona anche con l'orologio indietro`() {
        val s = slug
        archivio.registraNota(s, "prima", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        assertTrue(archivio.correggiNota(s, id, "dopo", adesso = ieri))
        assertEquals("dopo", archivio.voci(s).single { it.genere == Genere.NOTA }.testo)
    }

    // --- correggere -----------------------------------------------------------

    @Test
    fun `correggere una nota cambia il testo e tiene il resto`() {
        val s = slug
        // Il check-in serve: senza, "dove sei" non ha risposta e la colonna
        // `tappa` resta vuota — non avrei niente da verificare che sopravviva.
        archivio.checkin(s, archivio.tappe(s).first(), adesso = oggi)
        archivio.registraNota(
            s, "comprato il pane",
            posizione = Posizione(42.7185, 12.1112), adesso = oggi,
        )
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        assertTrue(archivio.correggiNota(s, id, "comprato il pane e il vino", adesso = oggi))

        val riga = archivio.voce(s, Genere.NOTA, id)!!
        assertEquals("comprato il pane e il vino", riga.testo(NoteTabella.TESTO))
        // **La prova che conta**: le coordinate non erano nella correzione e sono
        // ancora li'. Una riga parziale le avrebbe cancellate.
        assertEquals(42.7185, riga.numero(NoteTabella.LAT)!!, 1e-6)
        assertEquals("Orvieto", riga.testo(NoteTabella.TAPPA))
    }

    @Test
    fun `una nota corretta resta una sola voce`() {
        val s = slug
        archivio.registraNota(s, "prima", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!
        archivio.correggiNota(s, id, "dopo", adesso = oggi)

        val note = archivio.voci(s).filter { it.genere == Genere.NOTA }
        assertEquals(1, note.size)
        assertEquals("dopo", note.single().testo)
    }

    @Test
    fun `una nota vuota non e' una correzione`() {
        val s = slug
        archivio.registraNota(s, "prima", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.NOTA }.id!!

        assertTrue(!archivio.correggiNota(s, id, "   ", adesso = oggi))
        assertEquals("prima", archivio.voci(s).single { it.genere == Genere.NOTA }.testo)
    }

    @Test
    fun `correggere il chilometraggio di un rifornimento rifa' il consumo`() {
        val s = slug
        archivio.registraRifornimento(s, km = 1000, euro = 100.0, prezzoLitro = 1.7, adesso = oggi)
        // Mille e cinque invece di millecinquecento: il refuso da correggere.
        archivio.registraRifornimento(s, km = 1005, euro = 85.0, prezzoLitro = 1.7, adesso = oggi)
        val id = archivio.rifornimenti(s).last().id

        assertTrue(
            archivio.correggiRifornimento(
                s, id, km = 1500, euro = 85.0, prezzoLitro = 1.7,
                pieno = true, istante = oggi, adesso = oggi,
            ),
        )
        assertEquals(1500, archivio.rifornimenti(s).last().km)
    }

    @Test
    fun `correggere il prezzo rifa' i litri, che sono derivati`() {
        val s = slug
        archivio.registraRifornimento(s, km = 1000, euro = 107.16, prezzoLitro = 1.72, adesso = oggi)
        val id = archivio.rifornimenti(s).single().id

        archivio.correggiRifornimento(
            s, id, km = 1000, euro = 107.16, prezzoLitro = 1.60,
            pieno = true, istante = oggi, adesso = oggi,
        )

        // 107,16 / 1,60: la colonna si riscrive, e non resta al valore vecchio.
        assertEquals(66.975, archivio.rifornimenti(s).single().litri, 1e-3)
        assertEquals(66.98, archivio.voce(s, Genere.RIFORNIMENTO, id)!!.numero(RifornimentiTabella.LITRI)!!, 1e-2)
    }

    @Test
    fun `un rifornimento con prezzo zero non si corregge`() {
        val s = slug
        archivio.registraRifornimento(s, km = 1000, euro = 100.0, prezzoLitro = 1.7, adesso = oggi)
        val id = archivio.rifornimenti(s).single().id

        assertTrue(
            !archivio.correggiRifornimento(
                s, id, km = 1000, euro = 100.0, prezzoLitro = 0.0,
                pieno = true, istante = oggi, adesso = oggi,
            ),
        )
        assertEquals(1.7, archivio.rifornimenti(s).single().prezzoLitro!!, 1e-9)
    }

    @Test
    fun `correggere una spesa rifa' anche gli euro`() {
        val s = slug
        archivio.registraSpesa(
            s, Categoria.RISTORANTE, 45.0, Modalita.CARTA,
            valuta = "CHF", cambio = 1.06, adesso = oggi,
        )
        val id = archivio.spese(s).single().id

        archivio.correggiSpesa(
            s, id, categoria = Categoria.RISTORANTE, importo = 52.0,
            modalita = Modalita.CARTA, descrizione = "cena", valuta = "CHF",
            cambio = 1.06, istante = oggi, adesso = oggi,
        )

        val spesa = archivio.spese(s).single()
        assertEquals(52.0, spesa.importo, 1e-9)
        assertEquals(55.12, spesa.euro, 1e-6)
        assertEquals("cena", spesa.descrizione)
    }

    @Test
    fun `correggere una spesa non perde lo scontrino allegato`() {
        val s = slug
        archivio.registraSpesa(
            s, Categoria.SOSTA, 18.0, Modalita.CONTANTI,
            scontrino = "scontrino_20260808_180000.jpg", adesso = oggi,
        )
        val id = archivio.spese(s).single().id

        archivio.correggiSpesa(
            s, id, categoria = Categoria.SOSTA, importo = 20.0,
            modalita = Modalita.CONTANTI, descrizione = null, valuta = "EUR",
            cambio = null, istante = oggi, adesso = oggi,
        )

        assertEquals("scontrino_20260808_180000.jpg", archivio.spese(s).single().scontrino)
    }

    @Test
    fun `una spesa riportata in euro perde il cambio`() {
        val s = slug
        archivio.registraSpesa(
            s, Categoria.SPESA, 30.0, Modalita.POS,
            valuta = "CHF", cambio = 1.06, adesso = oggi,
        )
        val id = archivio.spese(s).single().id

        archivio.correggiSpesa(
            s, id, categoria = Categoria.SPESA, importo = 30.0,
            modalita = Modalita.POS, descrizione = null, valuta = "EUR",
            cambio = 1.06, istante = oggi, adesso = oggi,
        )

        val spesa = archivio.spese(s).single()
        assertTrue(!spesa.estera)
        assertNull(spesa.cambio)
        assertEquals(30.0, spesa.euro, 1e-9)
    }

    @Test
    fun `correggere la didascalia di una foto non tocca il file`() {
        val s = slug
        archivio.registraFoto(s, "foto_20260808_180000_Orvieto.jpg", "Duomo", adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.FOTO }.id!!

        archivio.correggiDidascalia(s, id, "Il duomo al tramonto", adesso = oggi)

        val voce = archivio.voci(s).single { it.genere == Genere.FOTO }
        assertEquals("Il duomo al tramonto", voce.testo)
        assertEquals("foto_20260808_180000_Orvieto.jpg", voce.allegato)
    }

    // --- la data che cambia ---------------------------------------------------

    @Test
    fun `spostare una spesa di giorno la sposta anche nel diario`() {
        val s = slug
        archivio.registraSpesa(s, Categoria.SOSTA, 18.0, Modalita.CONTANTI, adesso = oggi)
        val id = archivio.spese(s).single().id
        assertTrue(archivio.diario(s).testo().contains("2026-08-08"))

        archivio.correggiSpesa(
            s, id, categoria = Categoria.SOSTA, importo = 18.0,
            modalita = Modalita.CONTANTI, descrizione = null, valuta = "EUR",
            cambio = null, istante = ieri, adesso = oggi,
        )

        val diario = archivio.diario(s).testo()
        // **Due giornate riscritte, non una.** Rigenerando solo quella nuova, la
        // voce comparirebbe in entrambe.
        assertTrue(diario, diario.contains("2026-08-07"))
        val sezione = diario.substringAfter("2026-08-08").substringBefore("## ")
        assertTrue(diario, !sezione.contains("18,00"))
    }

    @Test
    fun `una correzione scritta oggi tiene la data di ieri`() {
        val s = slug
        archivio.registraSpesa(
            s, Categoria.SOSTA, 18.0, Modalita.CONTANTI,
            adesso = oggi, istante = ieri,
        )
        val id = archivio.spese(s).single().id

        archivio.correggiSpesa(
            s, id, categoria = Categoria.SOSTA, importo = 22.0,
            modalita = Modalita.CONTANTI, descrizione = null, valuta = "EUR",
            cambio = null, istante = ieri, adesso = oggi,
        )

        assertEquals(ieri.toLocalDate(), archivio.spese(s).single().istante.toLocalDate())
        assertEquals(22.0, archivio.spese(s).single().importo, 1e-9)
    }

    // --- gli spostamenti ------------------------------------------------------

    @Test
    fun `cancellare un arrivo dal diario non annulla il check-in`() {
        val s = slug
        val tappa = archivio.tappe(s).first()
        archivio.checkin(s, tappa, posizione = Posizione(42.7185, 12.1112), adesso = oggi)
        val id = archivio.voci(s).single { it.genere == Genere.ARRIVO }.id!!

        archivio.cancellaVoce(s, Genere.ARRIVO, id, adesso = oggi)

        // Sono due fatti distinti in due file distinti: la voce di diario se ne
        // va, e la tappa resta spuntata. Per disfare le due cose insieme c'e'
        // `annullaCheckin`.
        assertTrue(archivio.voci(s).none { it.genere == Genere.ARRIVO })
        assertEquals(StatoTappa.FATTA, archivio.tappe(s).first().stato)
    }

    // --- annullare un check-in ------------------------------------------------

    @Test
    fun `annullare un check-in disfa le due scritture che l'avevano fatto`() {
        val s = slug
        val tappa = archivio.tappe(s).first()
        archivio.checkin(s, tappa, posizione = Posizione(42.7185, 12.1112), adesso = oggi)

        assertTrue(archivio.annullaCheckin(s, archivio.tappe(s).first(), adesso = oggi))

        // La tappa torna da fare e perde l'ora d'arrivo...
        val tornata = archivio.tappe(s).first()
        assertEquals(StatoTappa.DA_FARE, tornata.stato)
        assertNull(tornata.checkinIl)
        // ...e l'arrivo esce dal diario. Lasciarne una sola sarebbe una mezza
        // verita': un diario che racconta un arrivo mai avvenuto, o una tappa da
        // fare con dentro l'ora in cui ci sei arrivato.
        assertTrue(archivio.voci(s).none { it.genere == Genere.ARRIVO })
        // E "dove sei" torna a non saperlo, che e' la ragione per cui il gesto
        // esiste: era questo a mandare fuori strada il riepilogo della sera.
        assertNull(archivio.luogo(s))
    }

    @Test
    fun `annullare non cancella niente dal file`() {
        val s = slug
        val tappa = archivio.tappe(s).first()
        archivio.checkin(s, tappa, posizione = Posizione(42.7185, 12.1112), adesso = oggi)
        archivio.annullaCheckin(s, archivio.tappe(s).first(), adesso = oggi)

        // Tutte aggiunte: la riga che diceva "fatta" e quella dell'arrivo sono
        // ancora scritte, e il file racconta ancora che quel check-in c'era
        // stato. E' la stessa promessa di correggere e cancellare una voce.
        val testo = File(archivio.cartellaViaggio(s), TappeTabella.NOME_FILE).readText()
        assertTrue(testo, testo.contains("fatta"))
        assertTrue(testo, testo.contains("da_fare"))
    }

    @Test
    fun `annullare un check-in che non c'e' non fa niente e lo dice`() {
        val s = slug
        // Nessun check-in su questa tappa: non c'e' niente da disfare, e la
        // funzione lo dice invece di scrivere una riga inutile.
        assertTrue(!archivio.annullaCheckin(s, archivio.tappe(s).first(), adesso = oggi))
        assertEquals(StatoTappa.DA_FARE, archivio.tappe(s).first().stato)
    }

    @Test
    fun `con l'orologio indietro l'annullamento vale comunque`() {
        val s = slug
        val tappa = archivio.tappe(s).first()
        archivio.checkin(s, tappa, posizione = Posizione(42.7185, 12.1112), adesso = oggi)

        // Il telefono ha l'ora sbagliata e "adesso" cade prima del check-in: la
        // lapide con un `ts` piu' vecchio non cancellerebbe niente, e la
        // funzione riferirebbe un successo che non c'e' stato.
        archivio.annullaCheckin(s, archivio.tappe(s).first(), adesso = ieri)

        assertTrue(archivio.voci(s).none { it.genere == Genere.ARRIVO })
        assertEquals(StatoTappa.DA_FARE, archivio.tappe(s).first().stato)
    }
}
