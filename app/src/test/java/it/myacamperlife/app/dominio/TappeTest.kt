package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TappeTest {

    private fun tappa(id: String, ordine: Int, stato: StatoTappa = StatoTappa.DA_FARE) =
        Tappa(id = id, ordine = ordine, nome = id, lat = 43.0, lon = 11.0, stato = stato)

    private val itinerario = listOf(
        tappa("a", 1, StatoTappa.FATTA),
        tappa("b", 2, StatoTappa.FATTA),
        tappa("c", 3),
        tappa("d", 4, StatoTappa.SALTATA),
        tappa("e", 5),
    )

    @Test
    fun `la tappa corrente e l'ultima fatta per ordine`() {
        assertEquals("b", Tappe.corrente(itinerario)?.id)
    }

    @Test
    fun `la prossima salta quelle saltate`() {
        // Dopo la b viene la c, che e' da fare.
        assertEquals("c", Tappe.prossima(itinerario)?.id)
    }

    @Test
    fun `la prossima dopo l'ultima fatta scavalca i salti`() {
        val dopoC = itinerario.map { if (it.id == "c") it.copy(stato = StatoTappa.FATTA) else it }

        // La d e' saltata, quindi si passa alla e.
        assertEquals("e", Tappe.prossima(dopoC)?.id)
    }

    @Test
    fun `senza nessuna tappa fatta la prossima e la prima`() {
        val nuovo = listOf(tappa("a", 1), tappa("b", 2))

        assertNull(Tappe.corrente(nuovo))
        assertEquals("a", Tappe.prossima(nuovo)?.id)
    }

    @Test
    fun `a itinerario finito non c'e' una prossima`() {
        val finito = listOf(tappa("a", 1, StatoTappa.FATTA), tappa("b", 2, StatoTappa.FATTA))

        assertNull(Tappe.prossima(finito))
    }

    @Test
    fun `il check-in marca fatta e registra l'istante`() {
        val fatta = Tappe.checkin(tappa("c", 3), OffsetDateTime.parse("2026-08-06T14:12:00+02:00"))

        assertEquals(StatoTappa.FATTA, fatta.stato)
        assertEquals("2026-08-06T14:12:00+02:00", fatta.checkinIl)
    }

    @Test
    fun `salta e ripristina sono lo stesso comando`() {
        val daFare = tappa("c", 3)

        val saltata = Tappe.alterna(daFare)
        assertEquals(StatoTappa.SALTATA, saltata.stato)

        val ripristinata = Tappe.alterna(saltata)
        assertEquals(StatoTappa.DA_FARE, ripristinata.stato)
    }

    @Test
    fun `una tappa fatta non si salta`() {
        val fatta = tappa("a", 1, StatoTappa.FATTA)

        assertEquals(fatta, Tappe.alterna(fatta))
    }

    @Test
    fun `un check-in si disfa, e la tappa perde l'ora d'arrivo`() {
        // Il gesto che mancava: `alterna` non tocca una tappa fatta — e ha
        // ragione, saltare un posto dove sei stato non vuol dire niente — ma
        // cosi' un check-in dato per sbaglio non aveva nessun rimedio.
        val fatta = Tappe.checkin(tappa("a", 1), OffsetDateTime.parse("2026-08-06T14:00:00+02:00"))
        assertEquals(StatoTappa.FATTA, fatta.stato)

        val disfatta = Tappe.annullaCheckin(fatta)
        assertEquals(StatoTappa.DA_FARE, disfatta.stato)
        // L'ora d'arrivo va via con lo stato: una tappa da fare che porta l'ora
        // in cui ci sei arrivato e' una mezza verita' peggiore del difetto.
        assertNull(disfatta.checkinIl)
    }

    @Test
    fun `su una tappa non fatta annullare il check-in non fa niente`() {
        val daFare = tappa("a", 1)
        assertEquals(daFare, Tappe.annullaCheckin(daFare))
        val saltata = tappa("b", 2, StatoTappa.SALTATA)
        assertEquals(saltata, Tappe.annullaCheckin(saltata))
    }

    @Test
    fun `inserire in fondo aggiunge e non tocca gli altri numeri`() {
        val tappe = listOf(tappa("a", 1), tappa("b", 2))

        val dopo = Tappe.inserisci(tappe, tappa("nuova", 0))

        assertEquals(listOf("a", "b", "nuova"), dopo.map { it.id })
        assertEquals(listOf(1, 2, 3), dopo.map { it.ordine })
    }

    @Test
    fun `inserire in mezzo rinumera quelle successive`() {
        val tappe = listOf(tappa("a", 1), tappa("b", 2), tappa("c", 3))

        val dopo = Tappe.inserisci(tappe, tappa("nuova", 0), primaDi = "b")

        assertEquals(listOf("a", "nuova", "b", "c"), dopo.map { it.id })
        assertEquals(listOf(1, 2, 3, 4), dopo.map { it.ordine })
    }

    @Test
    fun `inserire prima della prima funziona`() {
        val tappe = listOf(tappa("a", 1), tappa("b", 2))

        val dopo = Tappe.inserisci(tappe, tappa("nuova", 0), primaDi = "a")

        assertEquals(listOf("nuova", "a", "b"), dopo.map { it.id })
    }

    @Test
    fun `un riferimento inesistente mette la tappa in fondo invece di perderla`() {
        val tappe = listOf(tappa("a", 1))

        val dopo = Tappe.inserisci(tappe, tappa("nuova", 0), primaDi = "non-esiste")

        assertEquals(listOf("a", "nuova"), dopo.map { it.id })
    }

    @Test
    fun `inserire in un itinerario vuoto da la prima tappa`() {
        val dopo = Tappe.inserisci(emptyList(), tappa("nuova", 0))

        assertEquals(1, dopo.single().ordine)
    }

    @Test
    fun `i numeri d'ordine si compattano anche se partivano con buchi`() {
        val conBuchi = listOf(tappa("a", 10), tappa("b", 50))

        val dopo = Tappe.inserisci(conBuchi, tappa("nuova", 0), primaDi = "b")

        assertEquals(listOf(1, 2, 3), dopo.map { it.ordine })
    }

    @Test
    fun `cambiate elenca solo le righe da riscrivere`() {
        val prima = listOf(tappa("a", 1), tappa("b", 2), tappa("c", 3))
        val dopo = Tappe.inserisci(prima, tappa("nuova", 0), primaDi = "c")

        val daScrivere = Tappe.cambiate(prima, dopo)

        // a e b non si muovono; cambiano solo la nuova e la c che scala.
        assertEquals(setOf("nuova", "c"), daScrivere.map { it.id }.toSet())
    }
}
