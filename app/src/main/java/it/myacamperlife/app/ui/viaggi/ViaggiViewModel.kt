package it.myacamperlife.app.ui.viaggi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.Viaggio
import it.myacamperlife.app.dominio.Itinerario
import it.myacamperlife.app.dominio.Tappa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo stato dell'elenco dei viaggi e del viaggio aperto.
 *
 * Un solo ViewModel per le due schermate: lavorano sugli stessi dati e
 * passare dall'una all'altra non deve ricaricare niente.
 */
class ViaggiViewModel(
    private val archivio: Archivio,
    private val documenti: Documenti,
) : ViewModel() {

    data class Stato(
        val caricamento: Boolean = true,
        val viaggi: List<Viaggio> = emptyList(),
        val aperto: Viaggio? = null,
        val tappe: List<Tappa> = emptyList(),
        val esitoImport: EsitoImport? = null,
    )

    /** Da mostrare una volta e poi scartare. */
    sealed interface EsitoImport {
        data class Riuscito(val tappe: Int, val scartate: Int) : EsitoImport
        data class Fallito(val motivo: Itinerario.Motivo?) : EsitoImport
    }

    private val _stato = MutableStateFlow(Stato())
    val stato: StateFlow<Stato> = _stato.asStateFlow()

    init {
        ricarica()
    }

    private fun ricarica(apri: Viaggio? = null) = viewModelScope.launch {
        val elenco = withContext(Dispatchers.IO) {
            archivio.prepara()
            archivio.viaggi()
        }
        val daAprire = apri ?: _stato.value.aperto?.let { aperto -> elenco.find { it.slug == aperto.slug } }
        val tappe = daAprire?.let { withContext(Dispatchers.IO) { archivio.tappe(it.slug) } }.orEmpty()
        _stato.update {
            it.copy(caricamento = false, viaggi = elenco, aperto = daAprire, tappe = tappe)
        }
    }

    fun apri(viaggio: Viaggio) = viewModelScope.launch {
        val tappe = withContext(Dispatchers.IO) { archivio.tappe(viaggio.slug) }
        _stato.update { it.copy(aperto = viaggio, tappe = tappe) }
    }

    fun chiudi() = _stato.update { it.copy(aperto = null, tappe = emptyList()) }

    fun elimina(viaggio: Viaggio) = viewModelScope.launch {
        withContext(Dispatchers.IO) { archivio.elimina(viaggio.slug) }
        _stato.update { it.copy(aperto = null, tappe = emptyList()) }
        ricarica()
    }

    /**
     * Importa un itinerario e apre il viaggio appena creato.
     *
     * Il nome del viaggio viene dal primo titolo del Markdown; se non c'e', dal
     * nome del file senza estensione. Un file senza ne' l'uno ne' l'altro
     * resta comunque importabile.
     */
    fun importa(uri: Uri) = viewModelScope.launch {
        _stato.update { it.copy(caricamento = true, esitoImport = null) }

        val creato = withContext(Dispatchers.IO) {
            val documento = documenti.leggi(uri)
                ?: return@withContext Risultato(esito = EsitoImport.Fallito(null))

            when (val letto = Itinerario.leggi(documento.testo)) {
                is Itinerario.Esito.Fallito ->
                    Risultato(esito = EsitoImport.Fallito(letto.motivo))

                is Itinerario.Esito.Riuscito -> {
                    val nome = letto.nome
                        ?: documento.nome?.substringBeforeLast('.')?.trim()?.takeUnless { it.isEmpty() }
                        ?: "Viaggio senza nome"
                    archivio.prepara()
                    val viaggio = archivio.creaViaggio(
                        nome = nome,
                        punti = letto.tappe,
                        importatoDa = documento.nome,
                    )
                    Risultato(
                        viaggio = viaggio,
                        esito = EsitoImport.Riuscito(letto.tappe.size, letto.scartati),
                    )
                }
            }
        }

        _stato.update { it.copy(esitoImport = creato.esito) }
        ricarica(apri = creato.viaggio)
    }

    fun esitoVisto() = _stato.update { it.copy(esitoImport = null) }

    private data class Risultato(val viaggio: Viaggio? = null, val esito: EsitoImport)
}
