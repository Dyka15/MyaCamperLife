package it.myacamperlife.app.dominio

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'itinerario raccontato per giornate.
 *
 * Le regole che contano sono tre, e nessuna e' ovvia: **l'ordine
 * dell'itinerario non si tocca**, le tappe senza data non spariscono, e il
 * meteo di una giornata e' quello di dove si dorme.
 */
class FiloTest {

    private val oggi: LocalDate = LocalDate.parse("2026-08-19")
    private val adesso: OffsetDateTime = OffsetDateTime.parse("2026-08-19T20:00:00+02:00")

    private var contatore = 0

    private fun tappa(
        nome: String,
        giorno: String?,
        lat: Double = 45.0,
        lon: Double = 11.0,
        stato: StatoTappa = StatoTappa.DA_FARE,
    ): Tappa {
        contatore += 1
        return Tappa(
            id = "t$contatore",
            ordine = contatore,
            nome = nome,
            lat = lat,
            lon = lon,
            giorno = giorno,
            stato = stato,
        )
    }

    @Test
    fun `le tappe si raggruppano sotto il loro giorno`() {
        val giornate = Filo.componi(
            listOf(
                tappa("Lonigo", "6/8"),
                tappa("Garmisch", "6/8"),
                tappa("Füssen", "8/8"),
            ),
            oggi,
        )
        assertEquals(2, giornate.size)
        assertEquals(listOf("Lonigo", "Garmisch"), giornate[0].fermate.map { it.tappa.nome })
        assertEquals(listOf("Füssen"), giornate[1].fermate.map { it.tappa.nome })
    }

    @Test
    fun `l'ordine dell'itinerario non si tocca`() {
        // Una data che ricompare piu' avanti fa un gruppo nuovo, non torna
        // indietro: riordinare le tappe per data mostrerebbe un viaggio diverso
        // da quello scritto nel file.
        val giornate = Filo.componi(
            listOf(
                tappa("Monaco", "9/8"),
                tappa("Augsburg", "10/8"),
                tappa("Monaco di nuovo", "9/8"),
            ),
            oggi,
        )
        assertEquals(3, giornate.size)
        assertEquals(
            listOf("Monaco", "Augsburg", "Monaco di nuovo"),
            giornate.flatMap { it.fermate }.map { it.tappa.nome },
        )
    }

    @Test
    fun `le tappe senza data finiscono in fondo, tutte insieme`() {
        val giornate = Filo.componi(
            listOf(
                tappa("Senza uno", null),
                tappa("Lonigo", "6/8"),
                tappa("Senza due", null),
            ),
            oggi,
        )
        // Non si buttano e non si mescolano: una tappa che sparisce dall'elenco
        // e' peggio di una tappa senza data.
        val ultima = giornate.last()
        assertNull(ultima.giorno)
        assertEquals(Filo.SENZA_DATA, ultima.etichetta)
        assertEquals(listOf("Senza uno", "Senza due"), ultima.fermate.map { it.tappa.nome })
        assertEquals(listOf("Lonigo"), giornate.first().fermate.map { it.tappa.nome })
    }

    @Test
    fun `oggi e domani si dicono, ma senza togliere la data`() {
        // "Oggi" da solo costringe a ricordare che giorno e', e in viaggio non
        // lo sa nessuno.
        assertTrue(Filo.etichetta(oggi, oggi).startsWith("Oggi · "))
        assertTrue(Filo.etichetta(oggi.plusDays(1), oggi).startsWith("Domani · "))
        assertEquals("Sabato 22 agosto", Filo.etichetta(oggi.plusDays(3), oggi))
    }

    @Test
    fun `ogni fermata porta quanto si guida per arrivarci`() {
        val tratte = Tratte(
            listOf(Tratta(daLat = 45.0, daLon = 11.0, aLat = 47.0, aLon = 11.0, km = 312.0, minuti = 220)),
        )
        val giornate = Filo.componi(
            listOf(
                tappa("Lonigo", "6/8", lat = 45.0, lon = 11.0),
                tappa("Garmisch", "6/8", lat = 47.0, lon = 11.0),
            ),
            oggi,
            tratte = tratte,
        )
        val fermate = giornate.single().fermate
        // La prima non ha un tratto: da nessun posto non si guida.
        assertNull(fermate[0].arrivoDa)
        assertEquals(312.0, fermate[1].arrivoDa!!.km, 0.001)
    }

    @Test
    fun `senza tratte precalcolate nessuna fermata inventa una distanza`() {
        val giornate = Filo.componi(
            listOf(tappa("Lonigo", "6/8"), tappa("Garmisch", "6/8", lat = 47.0)),
            oggi,
        )
        // La linea d'aria sembrerebbe una distanza di guida senza esserlo.
        assertTrue(giornate.single().fermate.all { it.arrivoDa == null })
    }

    @Test
    fun `il meteo di una giornata e' quello di dove si dorme`() {
        // Domani, non il 6 agosto: una data gia' passata si risolve **in avanti**
        // — e' la regola di GiornoTappa — e finirebbe nel 2027, dove la scorta
        // non ha previsioni. Il difetto stava nella prova, non nel codice.
        val meteo = Meteo(
            scaricatoIl = "2026-08-19T19:00:00+02:00",
            luoghi = listOf(
                MeteoLuogo("Partenza", 45.0, 11.0, listOf(Previsione("2026-08-20", massima = 30.0))),
                MeteoLuogo("Arrivo", 47.0, 11.0, listOf(Previsione("2026-08-20", massima = 21.0))),
            ),
        )
        val giornate = Filo.componi(
            listOf(
                tappa("Lonigo", "20/8", lat = 45.0, lon = 11.0),
                tappa("Garmisch", "20/8", lat = 47.0, lon = 11.0),
            ),
            oggi,
            meteo = meteo,
            adesso = adesso,
        )
        // L'ultima tappa del giorno: e' la previsione che decide come si passa
        // la sera, e in duecento chilometri il tempo cambia.
        assertEquals(21.0, giornate.single().previsione!!.massima!!, 0.001)
    }

    @Test
    fun `una scorta scaduta non da' nessuna previsione`() {
        val vecchio = Meteo(
            scaricatoIl = "2026-08-10T19:00:00+02:00",
            luoghi = listOf(
                MeteoLuogo("Lonigo", 45.0, 11.0, listOf(Previsione("2026-08-20", massima = 30.0))),
            ),
        )
        val giornate = Filo.componi(
            listOf(tappa("Lonigo", "20/8")),
            oggi,
            meteo = vecchio,
            adesso = adesso,
        )
        // Una previsione di nove giorni fa non e' un dato vecchio, e' un dato
        // sbagliato: stessa regola della scheda di tappa e del riepilogo.
        assertNull(giornate.single().previsione)
    }

    @Test
    fun `la tappa corrente e' segnata una volta sola`() {
        val giornate = Filo.componi(
            listOf(
                tappa("Lonigo", "6/8", stato = StatoTappa.FATTA),
                tappa("Garmisch", "7/8", stato = StatoTappa.FATTA),
                tappa("Füssen", "8/8"),
            ),
            oggi,
        )
        val correnti = giornate.flatMap { it.fermate }.filter { it.corrente }
        assertEquals(1, correnti.size)
        assertEquals("Garmisch", correnti.single().tappa.nome)
    }

    @Test
    fun `un itinerario vuoto non fa nessuna giornata`() {
        assertTrue(Filo.componi(emptyList(), oggi).isEmpty())
    }
}
