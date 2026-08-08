package it.myacamperlife.app.rete

import android.content.Context
import it.myacamperlife.app.archivio.Chiavi
import it.myacamperlife.app.archivio.Impostazioni
import it.myacamperlife.app.dominio.Ai
import it.myacamperlife.app.dominio.GuaioAi
import it.myacamperlife.app.dominio.Modello
import it.myacamperlife.app.dominio.RispostaModello
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Com'e' andata: una risposta, o il motivo per cui non c'e'. */
sealed interface EsitoAi {
    data class Risposta(val risposta: RispostaModello, val diRiserva: Boolean) : EsitoAi
    data class Guaio(val guaio: GuaioAi) : EsitoAi
}

/**
 * Chiama il modello: il principale, e se non risponde la riserva.
 *
 * **Un solo client per due usi.** Lo stesso codice serve Esplora e la prosa del
 * diario; cambiano il prompt di sistema e cosa gli si da' in pasto. Era il piano
 * dall'inizio ed e' quello che rende questa fase piccola.
 *
 * **La riserva scatta su un rifiuto, non su un silenzio dell'utente.** Se Gemini
 * risponde 429 perche' la quota e' finita, o 404 perche' il nome del modello e'
 * stato ritirato, si prova Grok e l'interfaccia dice che ha risposto la riserva.
 * Se manca la rete non si prova nemmeno: sarebbero venti secondi di timeout per
 * scoprire due volte la stessa cosa.
 */
class Assistente(private val context: Context) {

    private val chiavi = Chiavi(context)

    fun configurato(modello: Modello): Boolean = chiavi.configurato(modello)

    fun chiaviDisponibili(): Boolean = chiavi.disponibile

    fun coda(modello: Modello): String? = chiavi.coda(modello)

    fun salvaChiave(modello: Modello, chiave: String?) = chiavi.salva(modello, chiave)

    /**
     * @param conRicerca la ricerca web serve a Esplora e non alla prosa del
     *   diario: quella lavora su una cronaca che e' gia' tutta nel prompt, e
     *   lasciarla cercare in rete sarebbe un invito ad aggiungere dettagli che
     *   nella giornata non c'erano.
     */
    suspend fun chiedi(
        sistema: String,
        domanda: String,
        impostazioni: Impostazioni,
        conRicerca: Boolean = true,
    ): EsitoAi = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) return@withContext EsitoAi.Guaio(GuaioAi.SenzaRete)

        val principale = impostazioni.modelloPrincipale
        val riserva = Modello.entries.firstOrNull { it != principale }

        val primo = prova(principale, sistema, domanda, impostazioni, conRicerca)
        if (primo is EsitoAi.Risposta) return@withContext primo

        // Senza chiave sul principale non e' un guasto: si prova la riserva, e
        // se nemmeno quella e' configurata lo si dice una volta sola.
        val secondo = riserva
            ?.takeIf { chiavi.configurato(it) }
            ?.let { prova(it, sistema, domanda, impostazioni, conRicerca) }
            ?: return@withContext primo

        if (secondo is EsitoAi.Risposta) {
            EsitoAi.Risposta(secondo.risposta, diRiserva = true)
        } else {
            // Si riporta il guaio del **principale**: e' quello da sistemare.
            primo
        }
    }

    private suspend fun prova(
        modello: Modello,
        sistema: String,
        domanda: String,
        impostazioni: Impostazioni,
        conRicerca: Boolean,
    ): EsitoAi {
        val chiave = chiavi.chiave(modello) ?: return EsitoAi.Guaio(GuaioAi.SenzaChiave)
        val nome = impostazioni.modello(modello)

        val esito = when (modello) {
            Modello.GEMINI -> Rete.postaConEsito(
                indirizzo = Ai.indirizzoGemini(nome),
                corpo = Ai.corpoGemini(sistema, domanda, conRicerca),
                // La chiave in intestazione e non nell'indirizzo: un Uri finisce
                // nei log, un'intestazione molto meno.
                intestazioni = mapOf("x-goog-api-key" to chiave),
            )
            Modello.GROK -> Rete.postaConEsito(
                indirizzo = Ai.indirizzoGrok(),
                corpo = Ai.corpoGrok(nome, sistema, domanda, conRicerca),
                intestazioni = mapOf("Authorization" to "Bearer $chiave"),
            )
        }

        return when (esito) {
            is EsitoHttp.Riuscito -> {
                val risposta = when (modello) {
                    Modello.GEMINI -> Ai.leggiGemini(esito.corpo)
                    Modello.GROK -> Ai.leggiGrok(esito.corpo)
                }
                if (risposta == null) EsitoAi.Guaio(GuaioAi.Vuota(modello))
                else EsitoAi.Risposta(risposta, diRiserva = false)
            }
            is EsitoHttp.Rifiutato -> EsitoAi.Guaio(
                GuaioAi.Rifiutata(modello, esito.codice, Ai.errore(esito.corpo)),
            )
            EsitoHttp.Muto -> EsitoAi.Guaio(GuaioAi.SenzaRete)
        }
    }
}
