package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Riscrivere il seguito di un viaggio.
 *
 * La regola che ogni prova sorveglia: **le tappe fatte sono storia, quelle da
 * fare sono ipotesi.** Le prime restano, le seconde si sostituiscono. Le saltate
 * stanno con le fatte, perche' saltare e' una decisione presa e non un'ipotesi.
 */
class RinnovoTest {

    private var contatore = 0

    private fun tappa(
        nome: String,
        ordine: Int,
        stato: StatoTappa = StatoTappa.DA_FARE,
        giorno: String? = null,
    ) = Tappa(
        id = nome.lowercase(),
        ordine = ordine,
        nome = nome,
        lat = 42.0,
        lon = 12.0,
        giorno = giorno,
        stato = stato,
    )

    /** Il viaggio a meta': tre fatte, una saltata, tre ancora da fare. */
    private val itinerario = listOf(
        tappa("Lonigo", 1, StatoTappa.FATTA, "2026-08-06"),
        tappa("Garmisch", 2, StatoTappa.FATTA, "2026-08-07"),
        tappa("Nordlingen", 3, StatoTappa.SALTATA, "2026-08-09"),
        tappa("Rothenburg", 4, StatoTappa.FATTA, "2026-08-11"),
        tappa("Wurzburg", 5, giorno = "2026-08-13"),
        tappa("Norimberga", 6, giorno = "2026-08-14"),
        tappa("Praga", 7, giorno = "2026-08-15"),
    )

    private val nuovi = listOf(
        Waypoint("Bamberga", 49.8988, 10.9028, giorno = "2026-08-13"),
        Waypoint("Bayreuth", 49.9456, 11.5713, giorno = "2026-08-14"),
    )

    private fun rinnovo() = Rinnovi.componi(itinerario, nuovi) { "n${contatore++}" }

    @Test
    fun `escono le tappe da fare, restano le fatte e le saltate`() {
        val rinnovo = rinnovo()
        assertEquals(
            listOf("Wurzburg", "Norimberga", "Praga"),
            rinnovo.sostituite.map { it.nome },
        )
        assertEquals(
            listOf("Lonigo", "Garmisch", "Nordlingen", "Rothenburg"),
            rinnovo.tenute.map { it.nome },
        )
    }

    @Test
    fun `una tappa saltata resta, perche' e' una decisione e non un'ipotesi`() {
        // Cancellarla vorrebbe dire dimenticare una scelta: Nordlingen l'hai
        // saltata tu, e sul file nuovo non c'e' scritto niente al riguardo.
        assertTrue(rinnovo().tenute.any { it.nome == "Nordlingen" })
        assertTrue(rinnovo().sostituite.none { it.nome == "Nordlingen" })
    }

    @Test
    fun `le nuove arrivano dopo quelle tenute, in fila`() {
        val rinnovo = rinnovo()
        assertEquals(listOf("Bamberga", "Bayreuth"), rinnovo.nuove.map { it.nome })
        assertEquals(listOf(5, 6), rinnovo.nuove.map { it.ordine })
        // I numeri delle tenute si rifanno densi da 1: togliendo le tappe da
        // fare in mezzo restano dei buchi, e sull'ordine si legge l'itinerario.
        assertEquals(listOf(1, 2, 3, 4), rinnovo.tenute.map { it.ordine })
    }

    @Test
    fun `si riscrivono solo le tenute che hanno cambiato numero`() {
        // Qui nessuna: le quattro tenute erano gia' 1-2-3-4, e riscrivere una
        // riga identica gonfia il file senza aggiungere niente.
        assertTrue(rinnovo().rinumerate.isEmpty())

        // Con una tappa da fare **in mezzo**, quelle dopo si spostano.
        val conBuco = listOf(
            tappa("Lonigo", 1, StatoTappa.FATTA),
            tappa("Salta questa", 2),
            tappa("Garmisch", 3, StatoTappa.FATTA),
        )
        val rinnovo = Rinnovi.componi(conBuco, nuovi) { "n${contatore++}" }
        assertEquals(listOf("Garmisch"), rinnovo.rinumerate.map { it.nome })
        assertEquals(2, rinnovo.rinumerate.single().ordine)
    }

    @Test
    fun `del file nuovo si tiene tutto — giorno, descrizione, campi extra`() {
        val ricco = listOf(
            Waypoint(
                nome = "Bamberga",
                lat = 49.8988,
                lon = 10.9028,
                giorno = "2026-08-13",
                descrizione = "Centro storico, patrimonio UNESCO",
                altro = listOf("orario" to "09:30"),
            ),
        )
        val nuova = Rinnovi.componi(itinerario, ricco) { "n0" }.nuove.single()
        assertEquals("2026-08-13", nuova.giorno)
        assertEquals("Centro storico, patrimonio UNESCO", nuova.descrizione)
        assertEquals(listOf("orario" to "09:30"), nuova.altro)
        // Nasce da fare: e' un pezzo di viaggio che devi ancora vivere.
        assertEquals(StatoTappa.DA_FARE, nuova.stato)
    }

    @Test
    fun `un file senza tappe non sostituisce niente`() {
        // Sostituire con niente non e' un piano: chi chiama non deve scrivere.
        val rinnovo = Rinnovi.componi(itinerario, emptyList()) { "n0" }
        assertTrue(rinnovo.vuoto)
    }

    @Test
    fun `su un viaggio non cominciato si sostituisce tutto`() {
        val daFare = listOf(tappa("Wurzburg", 1), tappa("Praga", 2))
        val rinnovo = Rinnovi.componi(daFare, nuovi) { "n${contatore++}" }
        assertTrue(rinnovo.tenute.isEmpty())
        assertEquals(2, rinnovo.sostituite.size)
        assertEquals(listOf(1, 2), rinnovo.nuove.map { it.ordine })
    }

    @Test
    fun `gli identificativi delle nuove sono nuovi`() {
        val rinnovo = rinnovo()
        val vecchi = itinerario.map { it.id }.toSet()
        assertTrue(rinnovo.nuove.none { it.id in vecchi })
        assertEquals(2, rinnovo.nuove.map { it.id }.distinct().size)
    }
}
