package it.myacamperlife.app.archivio

import it.myacamperlife.app.dominio.StatoTappa
import it.myacamperlife.app.dominio.Tappa

/**
 * Conversione fra [Tappa] e riga di `tappe.csv`.
 *
 * Sta qui e non nel dominio perche' il dominio non deve sapere che esistono
 * i file. Le coordinate si scrivono con sei decimali: sotto il metro non
 * serve, e piu' cifre renderebbero il file illeggibile a occhio.
 */
object TappeTabella {

    const val NOME_FILE = "tappe.csv"

    const val ORDINE = "ordine"
    const val NOME = "nome"
    const val LAT = "lat"
    const val LON = "lon"
    const val TIPO = "tipo"
    const val GIORNO = "giorno"
    const val DESCRIZIONE = "descrizione"
    const val STATO = "stato"
    const val CHECKIN = "checkin"

    val COLONNE = listOf(
        Csv.ID, Csv.TS, Csv.CANCELLATO,
        ORDINE, NOME, LAT, LON, TIPO, GIORNO, DESCRIZIONE, STATO, CHECKIN,
    )

    fun riga(tappa: Tappa, ts: String, cancellata: Boolean = false): Map<String, String> = mapOf(
        Csv.ID to tappa.id,
        Csv.TS to ts,
        Csv.CANCELLATO to if (cancellata) Csv.booleano(true) else "",
        ORDINE to tappa.ordine.toString(),
        NOME to Csv.testo(tappa.nome),
        LAT to Csv.numero(tappa.lat, DECIMALI_COORDINATE),
        LON to Csv.numero(tappa.lon, DECIMALI_COORDINATE),
        TIPO to Csv.testo(tappa.tipo),
        GIORNO to Csv.testo(tappa.giorno),
        DESCRIZIONE to Csv.testo(tappa.descrizione),
        STATO to tappa.stato.codice,
        CHECKIN to Csv.testo(tappa.checkinIl),
    )

    /**
     * Torna `null` se la riga non ha il minimo indispensabile: identita', nome
     * e coordinate. Una riga rovinata da una modifica a mano viene saltata,
     * non fa fallire la lettura dell'itinerario.
     */
    fun tappa(riga: Riga): Tappa? {
        val id = riga.id ?: return null
        val nome = riga.testo(NOME) ?: return null
        val lat = riga.numero(LAT) ?: return null
        val lon = riga.numero(LON) ?: return null
        return Tappa(
            id = id,
            ordine = riga.intero(ORDINE) ?: 0,
            nome = nome,
            lat = lat,
            lon = lon,
            tipo = riga.testo(TIPO),
            giorno = riga.testo(GIORNO),
            descrizione = riga.testo(DESCRIZIONE),
            stato = StatoTappa.da(riga.testo(STATO)),
            checkinIl = riga.testo(CHECKIN),
        )
    }

    private const val DECIMALI_COORDINATE = 6
}
