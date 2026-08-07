package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.CategoriaPoi
import it.myacamperlife.app.dominio.Luogo
import it.myacamperlife.app.dominio.Poi
import it.myacamperlife.app.dominio.Tratta
import it.myacamperlife.app.dominio.Tratte

/**
 * Le colonne di `scorta/tratte.csv`.
 *
 * L'`id` e' costruito dalle coordinate dei due capi: cosi' reimportare lo
 * stesso itinerario **corregge** le tratte invece di duplicarle, con la stessa
 * regola "vince l'ultima" di tutte le altre tabelle.
 *
 * I nomi delle tappe ci sono per chi apre il file, non per il codice: cercare
 * per nome si romperebbe appena una tappa viene rinominata, mentre le
 * coordinate restano.
 */
object TratteTabella {
    const val NOME_FILE = "tratte.csv"
    const val CARTELLA = "scorta"

    const val DA = "da"
    const val A = "a"
    const val DA_LAT = "da_lat"
    const val DA_LON = "da_lon"
    const val A_LAT = "a_lat"
    const val A_LON = "a_lon"
    const val KM = "km"
    const val MINUTI = "minuti"

    val COLONNE = listOf(
        Csv.ID, Csv.TS, Csv.CANCELLATO,
        DA, A, DA_LAT, DA_LON, A_LAT, A_LON, KM, MINUTI,
    )

    /** Le coordinate arrotondate ai quattro decimali: una decina di metri. */
    fun chiave(tratta: Tratta): String = listOf(
        tratta.daLat, tratta.daLon, tratta.aLat, tratta.aLon,
    ).joinToString(">") { String.format(java.util.Locale.ROOT, "%.4f", it) }

    fun riga(tratta: Tratta, ts: String): Map<String, String> = mapOf(
        Csv.ID to chiave(tratta),
        Csv.TS to ts,
        DA to Csv.testo(tratta.da),
        A to Csv.testo(tratta.a),
        DA_LAT to Csv.numero(tratta.daLat, 6),
        DA_LON to Csv.numero(tratta.daLon, 6),
        A_LAT to Csv.numero(tratta.aLat, 6),
        A_LON to Csv.numero(tratta.aLon, 6),
        KM to Csv.numero(tratta.km, 1),
        MINUTI to tratta.minuti.toString(),
    )

    fun tratta(riga: Riga): Tratta? {
        val daLat = riga.numero(DA_LAT) ?: return null
        val daLon = riga.numero(DA_LON) ?: return null
        val aLat = riga.numero(A_LAT) ?: return null
        val aLon = riga.numero(A_LON) ?: return null
        val km = riga.numero(KM) ?: return null
        return Tratta(
            daLat = daLat,
            daLon = daLon,
            aLat = aLat,
            aLon = aLon,
            km = km,
            minuti = riga.intero(MINUTI) ?: 0,
            da = riga.testo(DA),
            a = riga.testo(A),
        )
    }

    fun tratte(righe: List<Riga>): Tratte = Tratte(righe.mapNotNull { tratta(it) })

    /** Il nome del file del meteo, nella stessa cartella. */
    const val NOME_METEO = "meteo.json"
}

/**
 * Le colonne di `scorta/poi.csv`: i dintorni del viaggio, scaricati in anticipo.
 *
 * L'`id` e' quello di OpenStreetMap — `node/123456` — quindi riscaricare i
 * dintorni **aggiorna** i posti invece di duplicarli, e un posto che nel
 * frattempo e' stato cancellato da OSM resta nel file finche' non lo si toglie
 * a mano. Va bene: un'area di sosta che non c'e' piu' e' un'informazione meno
 * dannosa di un'area di sosta che non compare.
 */
object PoiTabella {
    const val NOME_FILE = "poi.csv"

    const val NOME = "nome"
    const val CATEGORIA = "categoria"
    const val LAT = "lat"
    const val LON = "lon"
    const val DETTAGLIO = "dettaglio"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, NOME, CATEGORIA, LAT, LON, DETTAGLIO)

    fun riga(poi: Poi, ts: String): Map<String, String> = mapOf(
        Csv.ID to poi.id,
        Csv.TS to ts,
        NOME to Csv.testo(poi.nome),
        CATEGORIA to poi.categoria.codice,
        LAT to Csv.numero(poi.lat, 6),
        LON to Csv.numero(poi.lon, 6),
        DETTAGLIO to Csv.testo(poi.dettaglio),
    )

    fun poi(riga: Riga): Poi? {
        val id = riga.id ?: return null
        val categoria = CategoriaPoi.da(riga.testo(CATEGORIA)) ?: return null
        val lat = riga.numero(LAT) ?: return null
        val lon = riga.numero(LON) ?: return null
        return Poi(
            id = id,
            nome = riga.testo(NOME),
            categoria = categoria,
            lat = lat,
            lon = lon,
            dettaglio = riga.testo(DETTAGLIO),
        )
    }
}

/**
 * Le colonne di `scorta/luoghi.csv`: i toponimi con cui l'app dice dove sei
 * senza rete.
 *
 * L'`id` viene dal nome piu' le coordinate arrotondate, non da OSM: due paesi
 * omonimi restano due righe, e lo stesso paese riscaricato ne resta una.
 */
object LuoghiTabella {
    const val NOME_FILE = "luoghi.csv"

    const val NOME = "nome"
    const val LAT = "lat"
    const val LON = "lon"
    const val ABITANTI = "abitanti"

    val COLONNE = listOf(Csv.ID, Csv.TS, Csv.CANCELLATO, NOME, LAT, LON, ABITANTI)

    fun chiave(luogo: Luogo): String =
        luogo.nome.lowercase() + "@" + String.format(java.util.Locale.ROOT, "%.3f,%.3f", luogo.lat, luogo.lon)

    fun riga(luogo: Luogo, ts: String): Map<String, String> = mapOf(
        Csv.ID to chiave(luogo),
        Csv.TS to ts,
        NOME to Csv.testo(luogo.nome),
        LAT to Csv.numero(luogo.lat, 6),
        LON to Csv.numero(luogo.lon, 6),
        ABITANTI to (luogo.abitanti?.toString() ?: ""),
    )

    fun luogo(riga: Riga): Luogo? {
        val nome = riga.testo(NOME) ?: return null
        val lat = riga.numero(LAT) ?: return null
        val lon = riga.numero(LON) ?: return null
        return Luogo(nome = nome, lat = lat, lon = lon, abitanti = riga.intero(ABITANTI))
    }
}
