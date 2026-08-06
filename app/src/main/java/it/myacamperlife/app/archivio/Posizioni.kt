package it.myacamperlife.app.archivio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * La posizione, al meglio di quello che il telefono sa in questo momento.
 *
 * **Il GPS non ha bisogno di rete**: i satelliti si ricevono in mezzo al
 * nulla, ed e' esattamente il caso d'uso. Serve rete solo per il primo
 * agganciamento veloce, e per quello ci sono i ripieghi qui sotto.
 *
 * Si usa `LocationManager` e non il fused provider di Google: fa il suo
 * lavoro, non aggiunge una dipendenza, e funziona anche su un telefono senza
 * i servizi Google.
 */
class Posizioni(private val context: Context) {

    fun permessoConcesso(): Boolean = PERMESSI.any { permesso ->
        ContextCompat.checkSelfPermission(context, permesso) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Prova nell'ordine: una posizione fresca dal GPS, poi dalla rete, poi
     * l'ultima nota. Torna `null` solo se il telefono non sa proprio niente.
     *
     * L'attesa e' limitata: un fix a freddo puo' richiedere minuti, e far
     * aspettare l'utente mentre e' fermo in piazzola non ha senso quando
     * l'ultima posizione nota e' probabilmente quella giusta.
     */
    suspend fun attuale(attesa: Long = ATTESA_MS): Posizione? {
        if (!permessoConcesso()) return null
        val gestore = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val fresca = PROVIDER_ATTIVI
            .filter { gestore.isProviderEnabled(it) }
            .firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(attesa) { corrente(gestore, provider) }
            }
        if (fresca != null) return fresca.let { Posizione(it.latitude, it.longitude) }

        return ultimaNota(gestore)?.let { Posizione(it.latitude, it.longitude) }
    }

    /** L'ultima posizione che il sistema ha in cache, senza attendere niente. */
    fun ultimaNota(): Posizione? {
        if (!permessoConcesso()) return null
        val gestore = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return ultimaNota(gestore)?.let { Posizione(it.latitude, it.longitude) }
    }

    private fun ultimaNota(gestore: LocationManager): Location? = PROVIDER_TUTTI
        .asSequence()
        .filter { gestore.allProviders.contains(it) }
        .mapNotNull { provider ->
            runCatching { gestore.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }

    private suspend fun corrente(gestore: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuazione ->
            val annulla = CancellationSignal()
            continuazione.invokeOnCancellation { annulla.cancel() }
            runCatching {
                gestore.getCurrentLocation(
                    provider,
                    annulla,
                    context.mainExecutor,
                ) { posizione ->
                    if (continuazione.isActive) continuazione.resume(posizione)
                }
            }.onFailure {
                if (continuazione.isActive) continuazione.resume(null)
            }
        }

    companion object {
        val PERMESSI = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /** Venti secondi: oltre, l'ultima posizione nota e' la risposta migliore. */
        private const val ATTESA_MS = 20_000L

        private val PROVIDER_ATTIVI = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        private val PROVIDER_TUTTI = PROVIDER_ATTIVI + LocationManager.PASSIVE_PROVIDER
    }
}
