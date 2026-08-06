package it.myacamperlife.app.dominio

import java.time.OffsetDateTime

/**
 * Un rifornimento di carburante.
 *
 * `pieno` non e' un dettaglio: **solo i pieni permettono di calcolare un
 * consumo**, perche' solo con il serbatoio riempito fino in cima si sa quanto
 * carburante e' entrato per fare quei chilometri.
 */
data class Rifornimento(
    val id: String,
    val istante: OffsetDateTime,
    /** Il contachilometri al momento del rifornimento. */
    val km: Int,
    val litri: Double,
    /** Facoltativo: si puo' registrare un rifornimento senza ricordare la spesa. */
    val euro: Double? = null,
    val pieno: Boolean,
    val luogo: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val punto: Punto?
        get() = if (lat != null && lon != null) Punto(istante, lat, lon) else null
}
