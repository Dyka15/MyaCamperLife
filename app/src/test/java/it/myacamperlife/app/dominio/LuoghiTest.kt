package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoghiTest {

    private val orvieto = Luogo("Orvieto", 42.7185, 12.1112, abitanti = 20_000)
    private val sugano = Luogo("Sugano", 42.7350, 12.0900, abitanti = 200)
    private val bolsena = Luogo("Bolsena", 42.6437, 11.9871, abitanti = 4_000)

    private val scorta = Luoghi(listOf(orvieto, sugano, bolsena))

    /** Un grado di latitudine sono circa 111 km. */
    private fun aNordDi(luogo: Luogo, km: Double) = luogo.lat + km / 111.0

    // --- chi vince ------------------------------------------------------------

    @Test
    fun `dentro il paese si dice il nome del paese`() {
        assertEquals("Orvieto", scorta.descrizione(42.7190, 12.1120))
    }

    @Test
    fun `nei paraggi si dice la distanza`() {
        assertEquals("8 km da Orvieto", scorta.descrizione(aNordDi(orvieto, 8.0), orvieto.lon))
    }

    @Test
    fun `fra due nomi ugualmente vicini vince il paese piu grande`() {
        // A meta' strada fra Orvieto e la sua frazione: la frazione e' piu'
        // vicina, ma "3 km da Sugano" non dice niente a nessuno.
        val luogo = scorta.piuVicino(42.7280, 12.1000)
        assertEquals("Orvieto", luogo!!.nome)
    }

    @Test
    fun `oltre il pari merito la vicinanza torna a comandare`() {
        // Un casale a dieci chilometri da Orvieto: la preferenza per il paese
        // grande vale solo fra toponimi ugualmente vicini, e qui non lo sono.
        val casale = Luogo("Casale", 42.8100, 12.1112, abitanti = 30)
        val conCasale = Luoghi(scorta.tutti + casale)

        assertEquals("Casale", conCasale.piuVicino(casale.lat, casale.lon)!!.nome)
        // Sugano e Orvieto distano fra loro solo due chilometri e mezzo, quindi
        // stando a Sugano vince comunque Orvieto: e' la regola di sopra.
        assertEquals("Orvieto", scorta.piuVicino(sugano.lat, sugano.lon)!!.nome)
    }

    @Test
    fun `lontano da tutto non si dice niente`() {
        // In mezzo al Tirreno: nessun toponimo entro venticinque chilometri.
        assertNull(scorta.descrizione(41.5, 10.5))
        assertNull(scorta.nome(41.5, 10.5))
    }

    @Test
    fun `una scorta vuota non da nomi e lo dice`() {
        assertTrue(Luoghi().vuoto)
        assertNull(Luoghi().descrizione(42.7185, 12.1112))
    }

    @Test
    fun `il nome per un file non porta la distanza`() {
        assertEquals("Orvieto", scorta.nome(aNordDi(orvieto, 8.0), orvieto.lon))
    }

    @Test
    fun `senza popolazione il confronto non cade`() {
        val senzaDati = Luoghi(
            listOf(Luogo("Ignoto", 42.7185, 12.1112), Luogo("Altro", 42.7200, 12.1120)),
        )
        assertTrue(senzaDati.piuVicino(42.7190, 12.1115) != null)
    }

    // --- cercare per nome -----------------------------------------------------

    @Test
    fun `un paese si trova scrivendone il nome`() {
        assertEquals(listOf("Bolsena"), scorta.cerca("bolsena").map { it.nome })
    }

    @Test
    fun `basta l'inizio del nome`() {
        assertEquals(listOf("Bolsena"), scorta.cerca("bols").map { it.nome })
    }

    @Test
    fun `le maiuscole e gli accenti non contano`() {
        val conAccento = Luoghi(listOf(Luogo("Città di Castello", 43.45, 12.24, abitanti = 40_000)))
        assertEquals(1, conAccento.cerca("citta").size)
        assertEquals(1, conAccento.cerca("CITTÀ").size)
    }

    @Test
    fun `chi comincia col testo viene prima di chi lo contiene`() {
        val elenco = Luoghi(
            listOf(
                Luogo("Borgo San Lorenzo", 43.95, 11.38, abitanti = 18_000),
                Luogo("San Casciano", 43.66, 11.19, abitanti = 17_000),
            ),
        )
        assertEquals(
            listOf("San Casciano", "Borgo San Lorenzo"),
            elenco.cerca("san").map { it.nome },
        )
    }

    @Test
    fun `a pari merito vince il paese piu grande`() {
        val elenco = Luoghi(
            listOf(
                Luogo("Castelnuovo Berardenga", 43.35, 11.50, abitanti = 9_000),
                Luogo("Castelnuovo di Garfagnana", 44.10, 10.41, abitanti = 5_500),
            ),
        )
        assertEquals("Castelnuovo Berardenga", elenco.cerca("castelnuovo").first().nome)
    }

    @Test
    fun `una lettera sola non e' una ricerca`() {
        assertTrue(scorta.cerca("b").isEmpty())
        assertTrue(scorta.cerca("").isEmpty())
        assertTrue(scorta.cerca(null).isEmpty())
    }

    @Test
    fun `un nome che non c'e' non da risultati`() {
        assertTrue(scorta.cerca("Reykjavik").isEmpty())
    }

    @Test
    fun `l'elenco si ferma a cinque`() {
        val molti = Luoghi((1..20).map { Luogo("Villa $it", 42.0 + it / 100.0, 12.0) })
        assertEquals(Luoghi.QUANTI, molti.cerca("villa").size)
    }
}
