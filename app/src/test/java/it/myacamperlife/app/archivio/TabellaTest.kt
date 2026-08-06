package it.myacamperlife.app.archivio

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La tabella usa `java.io.File`, quindi si verifica con test unitari normali
 * su una cartella temporanea: nessun emulatore, nessun Robolectric.
 */
class TabellaTest {

    private lateinit var cartella: File

    @Before
    fun prepara() {
        cartella = Files.createTempDirectory("tabella").toFile()
    }

    @After
    fun pulisci() {
        cartella.deleteRecursively()
    }

    private fun tabella(vararg extra: String) = Tabella(
        File(cartella, "prova.csv"),
        listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, "nome", "importo") + extra,
    )

    @Test
    fun `il primo accodamento crea il file con l'intestazione`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sosta", "importo" to "18,00"))

        val linee = t.file.readLines()
        assertEquals("id;ts;cancellato;nome;importo", linee[0])
        assertEquals("a1;$TS1;;sosta;18,00", linee[1])
    }

    @Test
    fun `si rilegge quello che si e scritto`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sosta", "importo" to "18,00"))

        val riga = t.vive().single()
        assertEquals("a1", riga.id)
        assertEquals("sosta", riga.testo("nome"))
        assertEquals(18.0, riga.numero("importo")!!, 1e-9)
        assertNull("una colonna assente e' nulla, non vuota", riga.testo("inesistente"))
    }

    @Test
    fun `una correzione e una riga nuova con lo stesso id e vince la piu recente`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sbagliato"))
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS2, "nome" to "giusto"))

        assertEquals(2, t.righe().size)
        assertEquals("giusto", t.vive().single().testo("nome"))
    }

    @Test
    fun `una lapide toglie il record senza toglierlo dal file`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sosta"))
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS2, Csv.CANCELLATO to "si"))

        assertTrue(t.vive().isEmpty())
        assertEquals("il file conserva la storia", 2, t.righe().size)
    }

    @Test
    fun `correggere non sposta la riga in fondo all'elenco`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a", Csv.TS to TS1, "nome" to "prima"))
        t.accoda(mapOf(Csv.ID to "b", Csv.TS to TS1, "nome" to "seconda"))
        t.accoda(mapOf(Csv.ID to "a", Csv.TS to TS2, "nome" to "prima corretta"))

        assertEquals(listOf("prima corretta", "seconda"), t.vive().map { it.testo("nome") })
    }

    @Test
    fun `fra due fusi orari vince l'istante non la stringa`() {
        // 13:30+01:00 e' 14:30 UTC: viene DOPO 14:00+02:00, che e' 12:00 UTC.
        // Confrontando i testi sembrerebbe il contrario.
        val righe = listOf(
            Riga(mapOf(Csv.ID to "a", Csv.TS to "2026-08-06T14:00:00+02:00", "nome" to "vecchia")),
            Riga(mapOf(Csv.ID to "a", Csv.TS to "2026-08-06T13:30:00+01:00", "nome" to "nuova")),
        )

        assertEquals("nuova", Tabella.risolvi(righe).single().testo("nome"))
    }

    @Test
    fun `a pari istante vince chi e scritto dopo`() {
        val righe = listOf(
            Riga(mapOf(Csv.ID to "a", Csv.TS to TS1, "nome" to "prima")),
            Riga(mapOf(Csv.ID to "a", Csv.TS to TS1, "nome" to "dopo")),
        )

        assertEquals("dopo", Tabella.risolvi(righe).single().testo("nome"))
    }

    @Test
    fun `una colonna nuova allarga l'intestazione e le righe vecchie restano leggibili`() {
        tabella().accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sosta"))

        val allargata = tabella("modalita")
        allargata.accoda(
            mapOf(Csv.ID to "a2", Csv.TS to TS2, "nome" to "spesa", "modalita" to "contanti"),
        )

        val linee = allargata.file.readLines()
        assertEquals("id;ts;cancellato;nome;importo;modalita", linee[0])

        val vive = allargata.vive()
        assertEquals(2, vive.size)
        assertEquals("sosta", vive[0].testo("nome"))
        assertNull("la riga vecchia non ha la colonna nuova", vive[0].testo("modalita"))
        assertEquals("contanti", vive[1].testo("modalita"))
    }

    @Test
    fun `una riga tronca da uno spegnimento non si incolla a quella nuova`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a1", Csv.TS to TS1, "nome" to "sosta"))

        // Simula un'interruzione a meta' scrittura: manca il ritorno a capo.
        t.file.appendText("a2;$TS2;;tronc")

        t.accoda(mapOf(Csv.ID to "a3", Csv.TS to TS2, "nome" to "dopo"))

        val righe = t.righe()
        assertEquals(3, righe.size)
        assertEquals("tronc", righe[1].testo("nome"))
        assertEquals("dopo", righe[2].testo("nome"))
    }

    @Test
    fun `compattare tiene solo le righe vive`() {
        val t = tabella()
        t.accoda(mapOf(Csv.ID to "a", Csv.TS to TS1, "nome" to "sbagliato"))
        t.accoda(mapOf(Csv.ID to "a", Csv.TS to TS2, "nome" to "giusto"))
        t.accoda(mapOf(Csv.ID to "b", Csv.TS to TS1, "nome" to "da togliere"))
        t.accoda(mapOf(Csv.ID to "b", Csv.TS to TS2, Csv.CANCELLATO to "si"))

        t.compatta()

        assertEquals(2, t.file.readLines().size)
        assertEquals(listOf("giusto"), t.vive().map { it.testo("nome") })
    }

    @Test
    fun `due copie si fondono concatenandole`() {
        // La proprieta' che rende possibile sincronizzare due dispositivi
        // senza scrivere codice di fusione.
        val telefono = listOf(
            Riga(mapOf(Csv.ID to "a", Csv.TS to TS1, "nome" to "da telefono")),
            Riga(mapOf(Csv.ID to "b", Csv.TS to TS1, "nome" to "solo telefono")),
        )
        val tablet = listOf(
            Riga(mapOf(Csv.ID to "a", Csv.TS to TS2, "nome" to "corretta su tablet")),
            Riga(mapOf(Csv.ID to "c", Csv.TS to TS1, "nome" to "solo tablet")),
        )

        val fuse = Tabella.risolvi(telefono + tablet)

        assertEquals(
            listOf("corretta su tablet", "solo telefono", "solo tablet"),
            fuse.map { it.testo("nome") },
        )
    }

    @Test
    fun `una tabella deve cominciare con le colonne riservate`() {
        val errore = runCatching {
            Tabella(File(cartella, "x.csv"), listOf("nome", "importo"))
        }.exceptionOrNull()

        assertTrue(errore is IllegalArgumentException)
    }

    private companion object {
        const val TS1 = "2026-08-06T10:00:00+02:00"
        const val TS2 = "2026-08-06T11:00:00+02:00"
    }
}
