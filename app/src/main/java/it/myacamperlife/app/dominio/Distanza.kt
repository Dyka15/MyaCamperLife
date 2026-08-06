package it.myacamperlife.app.dominio

import java.time.OffsetDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Un punto registrato: una posizione con l'ora in cui e' stata presa. */
data class Punto(val istante: OffsetDateTime, val lat: Double, val lon: Double)

/**
 * Distanze fra coordinate, in linea d'aria.
 *
 * L'emisenoverso e non la distanza su strada: quella richiede un servizio di
 * instradamento, che e' rete, e la rete e' esattamente quello che qui non c'e'.
 * Il numero che ne esce e' quindi una **sottostima** di quanto si e' guidato —
 * su strada italiana il rapporto reale sta di solito fra 1,2 e 1,4 — e chi lo
 * mostra deve dirlo.
 */
object Distanza {

    /** Chilometri in linea d'aria fra due coordinate. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * RAGGIO_TERRESTRE_KM * asin(sqrt(a).coerceAtMost(1.0))
    }

    fun km(da: Punto, a: Punto): Double = km(da.lat, da.lon, a.lat, a.lon)

    /**
     * Somma le distanze fra punti consecutivi, in ordine di ora.
     *
     * @param soglia sotto questa distanza lo spostamento si ignora. Senza,
     *   il rumore del GPS accumulerebbe chilometri stando fermi in piazzola:
     *   dieci posizioni registrate nello stesso posto darebbero qualche
     *   centinaio di metri ciascuna.
     */
    fun percorsi(punti: List<Punto>, soglia: Double = SOGLIA_KM): Double {
        val ordinati = punti.sortedBy { it.istante }
        var totale = 0.0
        var precedente: Punto? = null
        ordinati.forEach { punto ->
            val da = precedente
            if (da != null) {
                val tratto = km(da, punto)
                // Un punto sotto soglia non diventa il nuovo riferimento: si
                // resta ancorati al precedente. Cosi' il rumore del GPS attorno
                // a un punto fermo non accumula mai nulla, ma una guida lenta
                // con registrazioni ravvicinate viene contata comunque, appena
                // la somma dall'ancora supera la soglia.
                if (tratto >= soglia) {
                    totale += tratto
                    precedente = punto
                }
            } else {
                precedente = punto
            }
        }
        return totale
    }

    /** Trecento metri: sotto, e' fermo. */
    const val SOGLIA_KM = 0.3

    private const val RAGGIO_TERRESTRE_KM = 6371.0088
}
