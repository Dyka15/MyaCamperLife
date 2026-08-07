package it.myacamperlife.app.rete

import android.content.Context
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.dominio.Indirizzo
import it.myacamperlife.app.dominio.Luoghi
import it.myacamperlife.app.dominio.RispostaIndirizzo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Da dove e' arrivato un risultato: cambia cosa mostrare all'utente. */
enum class Provenienza { SCORTA, RETE, NIENTE }

data class RicercaIndirizzo(val risultati: List<Indirizzo>, val provenienza: Provenienza)

/**
 * Trova le coordinate di un posto dal suo nome o dal suo indirizzo.
 *
 * **Due strati, come Esplora.** Sotto ci sono i toponimi gia' scaricati nella
 * scorta del viaggio, che rispondono senza rete: scrivendo "Bolsena" la
 * richiesta non parte nemmeno. Sopra c'e' Nominatim, che sa gli indirizzi
 * civici e i nomi dei campeggi, e serve quando sotto non c'e' niente.
 *
 * Non e' geocodifica **inversa** — quella e' coordinate verso nome, e l'app la
 * fa da se' con gli stessi toponimi. Questa e' la direzione opposta: da un
 * nome alle coordinate.
 */
class Geocodifica(private val context: Context, private val archivio: Archivio) {

    /**
     * @param slug il viaggio in cui cercare fra i toponimi. Senza, si va
     *   direttamente in rete.
     */
    suspend fun cerca(testo: String, slug: String? = null): RicercaIndirizzo =
        withContext(Dispatchers.IO) {
            val cercato = testo.trim()
            if (cercato.length < 2) return@withContext RicercaIndirizzo(emptyList(), Provenienza.NIENTE)

            val locali = slug?.let { archivio.luoghi(it) } ?: Luoghi()
            val dallaScorta = locali.cerca(cercato).map { luogo ->
                Indirizzo(
                    nome = luogo.nome,
                    // Si dichiara da dove viene: e' un paese dell'itinerario,
                    // non un indirizzo trovato in rete.
                    descrizione = null,
                    lat = luogo.lat,
                    lon = luogo.lon,
                )
            }
            if (dallaScorta.isNotEmpty()) {
                return@withContext RicercaIndirizzo(dallaScorta, Provenienza.SCORTA)
            }

            if (!Rete.disponibile(context)) {
                return@withContext RicercaIndirizzo(emptyList(), Provenienza.NIENTE)
            }

            val corpo = Rete.prendi(RispostaIndirizzo.indirizzo(cercato))
                ?: return@withContext RicercaIndirizzo(emptyList(), Provenienza.NIENTE)

            val dallaRete = RispostaIndirizzo.leggi(corpo)
            RicercaIndirizzo(
                risultati = dallaRete,
                provenienza = if (dallaRete.isEmpty()) Provenienza.NIENTE else Provenienza.RETE,
            )
        }
}
