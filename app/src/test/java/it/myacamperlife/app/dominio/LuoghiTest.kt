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
}
