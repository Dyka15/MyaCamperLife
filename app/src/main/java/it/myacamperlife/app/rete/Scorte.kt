package it.myacamperlife.app.rete

import android.content.Context
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.dominio.Meteo
import it.myacamperlife.app.dominio.Overpass
import it.myacamperlife.app.dominio.RispostaMeteo
import it.myacamperlife.app.dominio.RispostaOsrm
import it.myacamperlife.app.dominio.Tratta
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Riempie la scorta: le due cose che si prendono dalla rete in anticipo.
 *
 * **Nessuna di queste due chiamate puo' far fallire qualcos'altro.** Il meteo
 * si scarica prima del riepilogo serale, e se non arriva il riepilogo esce
 * comunque con le tappe — che e' gia' meta' del suo valore. Le tratte si
 * chiedono all'import dell'itinerario, e se non arrivano le distanze restano in
 * linea d'aria, dichiarate come tali.
 *
 * Ogni funzione dice solo se ha aggiornato qualcosa. Non ci sono errori da
 * mostrare: "non c'era campo" non e' un'informazione su cui l'utente possa
 * fare niente di diverso da quello che sta gia' facendo.
 */
class Scorte(private val context: Context, private val archivio: Archivio) {

    /**
     * Scarica le previsioni per le tappe dei prossimi giorni.
     *
     * Una richiesta sola per tutte le tappe: Open-Meteo accetta le coordinate
     * in fila. In una finestra di campo incerto e' la differenza fra avere il
     * meteo e non averlo.
     */
    suspend fun aggiornaMeteo(
        slug: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext false

        val punti = archivio.puntiMeteo(slug, adesso.toLocalDate())
        if (punti.isEmpty()) return@withContext false

        val corpo = Rete.prendi(RispostaMeteo.indirizzo(punti)) ?: return@withContext false
        val luoghi = RispostaMeteo.leggi(corpo, punti)
        if (luoghi.isEmpty()) return@withContext false

        archivio.salvaMeteo(
            slug = slug,
            meteo = Meteo(
                scaricatoIl = adesso.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                luoghi = luoghi,
            ),
        )
        true
    }

    /**
     * Precalcola le distanze su strada fra tappe consecutive.
     *
     * Si chiama quando si importa un itinerario e quando si aggiunge una tappa:
     * sono i due momenti in cui la catena cambia, e sono anche i due momenti in
     * cui verosimilmente c'e' ancora campo, perche' li fai a casa.
     *
     * L'itinerario lungo si spezza in richieste piu' corte: il server pubblico
     * ha un tetto, e mezze tratte sono meglio di nessuna — quelle che mancano
     * ripiegano da sole sulla linea d'aria.
     */
    suspend fun aggiornaTratte(
        slug: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext false

        val punti = archivio.puntiTratte(slug)
        if (punti.size < 2) return@withContext false

        val trovate = mutableListOf<Tratta>()
        // Le fette si sovrappongono di un punto: l'ultima tappa di una e' la
        // prima della successiva, altrimenti la tratta di mezzo sparirebbe.
        punti.windowed(
            size = RispostaOsrm.PUNTI_PER_RICHIESTA,
            step = RispostaOsrm.PUNTI_PER_RICHIESTA - 1,
            partialWindows = true,
        ).forEach { fetta ->
            if (fetta.size < 2) return@forEach
            val corpo = Rete.prendi(RispostaOsrm.indirizzo(fetta)) ?: return@forEach
            trovate += RispostaOsrm.leggi(corpo, fetta)
        }

        if (trovate.isEmpty()) return@withContext false
        archivio.salvaTratte(slug, trovate, adesso)
        true
    }

    /**
     * Scarica i dintorni: punti di interesse e toponimi lungo l'itinerario.
     *
     * **Una richiesta sola**, con un corridoio di quindici chilometri intorno
     * alla polilinea delle tappe. Sette categorie e i nomi dei paesi arrivano
     * insieme e si separano leggendo i tag: su Overpass, che e' un servizio di
     * cortesia, chiedere una volta invece di otto e' buona educazione.
     *
     * E' la richiesta piu' pesante che l'app fa — decine o centinaia di
     * kilobyte — e per questo si fa **una volta per viaggio**, quando si importa
     * l'itinerario, o quando la si chiede.
     */
    suspend fun aggiornaDintorni(
        slug: String,
        adesso: OffsetDateTime = OffsetDateTime.now(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext false

        val punti = archivio.puntiDintorni(slug)
        if (punti.isEmpty()) return@withContext false

        val corpo = Rete.posta(
            indirizzo = Overpass.SERVIZIO,
            corpo = Overpass.query(punti),
            massimoCaratteri = Rete.MASSIMO_DINTORNI,
        ) ?: return@withContext false

        val dintorno = Overpass.leggi(corpo)
        if (dintorno.poi.isEmpty() && dintorno.luoghi.isEmpty()) return@withContext false

        archivio.salvaDintorni(slug, dintorno, adesso)
        true
    }
}
