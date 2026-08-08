package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EsploraTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-06")
    private val adesso: OffsetDateTime = OffsetDateTime.parse("2026-08-06T18:30:00+02:00")

    private fun poi(nome: String, categoria: CategoriaPoi) =
        PoiVicino(Poi("node/1", nome, categoria, 42.6, 11.9), 3.4)

    // --- il contesto ----------------------------------------------------------

    @Test
    fun `il contesto dice dove sei e quando`() {
        val contesto = Esplora.contesto(
            dove = "3 km da Bolsena",
            posizione = Coordinate(42.6437, 11.9871),
            oggi = oggi,
        )
        assertTrue(contesto, contesto.contains("oggi e' 2026-08-06"))
        assertTrue(contesto, contesto.contains("ci troviamo a 3 km da Bolsena"))
        assertTrue(contesto, contesto.contains("42.6437"))
    }

    @Test
    fun `quello che non si sa non si nomina`() {
        // Una riga "non disponibile" il modello la leggerebbe come un fatto.
        val contesto = Esplora.contesto(dove = null, posizione = null, oggi = oggi)
        assertTrue(contesto, !contesto.contains("ci troviamo"))
        assertTrue(contesto, !contesto.contains("coordinate"))
        assertTrue(contesto, !contesto.contains("previsione"))
    }

    @Test
    fun `il meteo entra nel contesto quando c'e'`() {
        val contesto = Esplora.contesto(
            dove = "Bolsena",
            posizione = null,
            oggi = oggi,
            meteo = Previsione("2026-08-06", codice = 61, minima = 17.0, massima = 24.0),
        )
        assertTrue(contesto, contesto.contains("previsione: Pioggia, 17–24°"))
    }

    @Test
    fun `i dintorni entrano con la distanza e con l'avvertenza`() {
        val contesto = Esplora.contesto(
            dove = "Bolsena",
            posizione = null,
            oggi = oggi,
            vicini = listOf(poi("Area Lido", CategoriaPoi.SOSTA)),
        )
        assertTrue(contesto, contesto.contains("Area Lido (Area di sosta), 3,4 km"))
        // L'elenco viene da OSM e puo' essere vecchio: il modello deve saperlo.
        assertTrue(contesto, contesto.contains("incompleto o invecchiato"))
    }

    @Test
    fun `la domanda mette il contesto davanti`() {
        val completa = Esplora.domanda("Contesto: qui", "dove dormo?")
        assertTrue(completa, completa.startsWith("Contesto: qui"))
        assertTrue(completa, completa.endsWith("Domanda: dove dormo?"))
    }

    @Test
    fun `senza contesto la domanda e' solo la domanda`() {
        assertEquals("dove dormo?", Esplora.domanda("", "  dove dormo?  "))
    }

    // --- il prompt ------------------------------------------------------------

    @Test
    fun `il prompt di riposo dice le cose che contano`() {
        val prompt = Esplora.PROMPT_DI_RIPOSO
        assertTrue(prompt, prompt.contains("italiano"))
        assertTrue(prompt, prompt.contains("Non inventare"))
        assertTrue(prompt, prompt.contains("distanza"))
        assertTrue(prompt, prompt.contains("camper"))
    }

    @Test
    fun `il prompt del diario vieta di aggiungere`() {
        assertTrue(Esplora.PROMPT_DIARIO.contains("Non aggiungere niente"))
    }

    // --- il dossier -----------------------------------------------------------

    @Test
    fun `il nome del file porta data, ora e domanda`() {
        assertEquals(
            "20260806_183000_dove-dormiamo-stanotte.md",
            Esplora.nomeFile(adesso, "Dove dormiamo stanotte?"),
        )
    }

    @Test
    fun `una domanda senza lettere lascia solo data e ora`() {
        assertEquals("20260806_183000.md", Esplora.nomeFile(adesso, "???"))
    }

    @Test
    fun `gli accenti non entrano nel nome del file`() {
        assertTrue(Esplora.nomeFile(adesso, "Dov'è l'area più vicina?").contains("dov-e-l-area-piu"))
    }

    @Test
    fun `un titolo lungo si accorcia`() {
        val lungo = "a".repeat(100)
        val titolo = Esplora.titolo(lungo)
        // Il contratto e' "al massimo sessanta", non "esattamente sessanta":
        // troncare a 57 e aggiungere i puntini ne fa 58, e va bene cosi'.
        assertTrue(titolo, titolo.length <= 60)
        assertTrue(titolo, titolo.endsWith("…"))
    }

    @Test
    fun `il dossier porta domanda, risposta, fonti e contesto`() {
        val testo = Esplora.dossier(
            domanda = "Dove dormiamo?",
            contesto = "Contesto: siamo a Bolsena",
            risposta = RispostaModello(
                testo = "All'area del lago.",
                fonti = listOf(Fonte("Aree", "https://esempio.it/aree")),
                modello = Modello.GEMINI,
            ),
            istante = adesso,
        )

        assertTrue(testo, testo.startsWith("# Dove dormiamo?"))
        assertTrue(testo, testo.contains("6 agosto 2026, 18:30 · Gemini"))
        assertTrue(testo, testo.contains("All'area del lago."))
        assertTrue(testo, testo.contains("[Aree](https://esempio.it/aree)"))
        // Il contesto si salva: fra sei mesi la domanda e' "cosa sapeva l'app".
        assertTrue(testo, testo.contains("Cosa sapeva l'app"))
        assertTrue(testo, testo.contains("Contesto: siamo a Bolsena"))
    }

    @Test
    fun `un dossier senza fonti non ha la sezione delle fonti`() {
        val testo = Esplora.dossier(
            domanda = "Domanda",
            contesto = "",
            risposta = RispostaModello("Risposta", emptyList(), Modello.GROK),
            istante = adesso,
        )
        assertTrue(testo, !testo.contains("## Fonti"))
        assertTrue(testo, !testo.contains("Cosa sapeva"))
    }
}
