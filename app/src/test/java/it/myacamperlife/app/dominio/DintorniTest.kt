package it.myacamperlife.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DintorniTest {

    private val orvieto = Coordinate(42.7185, 12.1112)

    private var contatore = 0

    private fun poi(
        nome: String?,
        categoria: CategoriaPoi,
        lat: Double,
        lon: Double,
        dettaglio: String? = null,
    ) = Poi("node/${contatore++}", nome, categoria, lat, lon, dettaglio)

    /** Un grado di latitudine sono circa 111 km: comodo per mettere le cose a distanze note. */
    private fun aNordDi(km: Double) = orvieto.lat + km / 111.0

    // --- la ricerca -----------------------------------------------------------

    @Test
    fun `i risultati escono dal piu vicino al piu lontano`() {
        val elenco = Dintorni.vicini(
            poi = listOf(
                poi("Lontano", CategoriaPoi.SOSTA, aNordDi(15.0), orvieto.lon),
                poi("Vicino", CategoriaPoi.SOSTA, aNordDi(2.0), orvieto.lon),
                poi("Medio", CategoriaPoi.SOSTA, aNordDi(8.0), orvieto.lon),
            ),
            lat = orvieto.lat,
            lon = orvieto.lon,
        )
        assertEquals(listOf("Vicino", "Medio", "Lontano"), elenco.map { it.poi.nome })
    }

    @Test
    fun `oltre il raggio non e' nei dintorni`() {
        val elenco = Dintorni.vicini(
            poi = listOf(poi("Roma", CategoriaPoi.SPESA, 41.9028, 12.4964)),
            lat = orvieto.lat,
            lon = orvieto.lon,
        )
        assertTrue(elenco.isEmpty())
    }

    @Test
    fun `la categoria filtra, senza categoria escono tutti`() {
        val tutti = listOf(
            poi("Area", CategoriaPoi.SOSTA, aNordDi(2.0), orvieto.lon),
            poi("Eni", CategoriaPoi.CARBURANTE, aNordDi(3.0), orvieto.lon),
        )
        assertEquals(2, Dintorni.vicini(tutti, orvieto.lat, orvieto.lon).size)
        assertEquals(
            listOf("Eni"),
            Dintorni.vicini(tutti, orvieto.lat, orvieto.lon, CategoriaPoi.CARBURANTE)
                .map { it.poi.nome },
        )
    }

    @Test
    fun `l'elenco si ferma a un numero che si puo scorrere`() {
        val molti = (1..50).map { poi("Posto $it", CategoriaPoi.ATTRAZIONE, aNordDi(it / 10.0), orvieto.lon) }
        assertEquals(Dintorni.QUANTI, Dintorni.vicini(molti, orvieto.lat, orvieto.lon).size)
    }

    @Test
    fun `il conteggio per categoria salta quelle vuote`() {
        val quanti = Dintorni.quanti(
            poi = listOf(
                poi("Area", CategoriaPoi.SOSTA, aNordDi(2.0), orvieto.lon),
                poi("Altra area", CategoriaPoi.SOSTA, aNordDi(4.0), orvieto.lon),
                poi("Roma", CategoriaPoi.SPESA, 41.9028, 12.4964),
            ),
            lat = orvieto.lat,
            lon = orvieto.lon,
        )
        assertEquals(mapOf(CategoriaPoi.SOSTA to 2), quanti)
    }

    // --- come si legge --------------------------------------------------------

    @Test
    fun `un posto senza nome prende quello della sua categoria`() {
        assertEquals("Camper service", poi(null, CategoriaPoi.SERVIZIO, 42.0, 12.0).etichetta())
        assertEquals("Fontana", poi("  ", CategoriaPoi.ACQUA, 42.0, 12.0).etichetta())
    }

    @Test
    fun `sotto il chilometro la distanza si dice in metri`() {
        assertEquals("450 m", PoiVicino(poi("x", CategoriaPoi.SOSTA, 42.0, 12.0), 0.45).distanza)
        assertEquals("3,2 km", PoiVicino(poi("x", CategoriaPoi.SOSTA, 42.0, 12.0), 3.24).distanza)
    }

    // --- le categorie ---------------------------------------------------------

    @Test
    fun `i tag di OpenStreetMap diventano categorie`() {
        assertEquals(CategoriaPoi.SOSTA, CategoriaPoi.daTag(mapOf("tourism" to "caravan_site")))
        assertEquals(CategoriaPoi.CAMPEGGIO, CategoriaPoi.daTag(mapOf("tourism" to "camp_site")))
        assertEquals(CategoriaPoi.SERVIZIO, CategoriaPoi.daTag(mapOf("amenity" to "sanitary_dump_station")))
        assertEquals(CategoriaPoi.CARBURANTE, CategoriaPoi.daTag(mapOf("amenity" to "fuel")))
        assertEquals(CategoriaPoi.SPESA, CategoriaPoi.daTag(mapOf("shop" to "supermarket")))
        assertEquals(CategoriaPoi.ATTRAZIONE, CategoriaPoi.daTag(mapOf("tourism" to "museum")))
    }

    @Test
    fun `un tag che non ci interessa non da una categoria`() {
        assertNull(CategoriaPoi.daTag(mapOf("amenity" to "bench")))
        assertNull(CategoriaPoi.daTag(emptyMap()))
    }

    @Test
    fun `il codice del file si rilegge, e uno sconosciuto no`() {
        assertEquals(CategoriaPoi.SOSTA, CategoriaPoi.da("sosta"))
        assertEquals(CategoriaPoi.ACQUA, CategoriaPoi.da(" Acqua "))
        assertNull(CategoriaPoi.da("piscina"))
        assertNull(CategoriaPoi.da(null))
    }
}
