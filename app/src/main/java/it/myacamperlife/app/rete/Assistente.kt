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
    data class Risposta(
        val risposta: RispostaModello,
        val diRiserva: Boolean,
        /**
         * Com'era fatta la risposta grezza: i nomi dei campi, non il contenuto.
         *
         * Serve a rispondere a distanza alla domanda «dove ha messo le fonti
         * questo fornitore», che e' costata un giro: le fonti non c'erano e
         * dall'esterno non si poteva sapere se mancavano nella risposta o se le
         * cercavo nel posto sbagliato.
         */
        val impronta: String? = null,
    ) : EsitoAi

    data class Guaio(val guaio: GuaioAi) : EsitoAi
}

/**
 * Chiama il modello: il principale, e se non risponde la riserva.
 *
 * **Un solo client per due usi.** Lo stesso codice serve Esplora e la prosa del
 * diario; cambiano il prompt di sistema e cosa gli si da' in pasto. Era il piano
 * dall'inizio ed e' quello che rende questa fase piccola.
 *
 * **La riserva scatta su un rifiuto, non su un silenzio dell'utente.** Se il
 * principale risponde 429 perche' la quota e' finita, o 404 perche' il nome del
 * modello e' stato ritirato, si provano le altre chiavi configurate in ordine e
 * l'interfaccia dice che ha risposto una riserva. Se manca la rete non si prova
 * nemmeno: sarebbero venti secondi di timeout per scoprire due volte la stessa
 * cosa.
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

        val primo = prova(principale, sistema, domanda, impostazioni, conRicerca)
        if (primo is EsitoAi.Risposta) return@withContext primo

        // **Tutte** le riserve configurate, non la prima che capita: da quando i
        // fornitori sono tre, fermarsi alla prima vorrebbe dire che la terza
        // chiave non serve a niente. Quelle senza chiave si saltano senza
        // spendere una richiesta.
        for (riserva in Modello.entries) {
            if (riserva == principale || !chiavi.configurato(riserva)) continue
            val esito = prova(riserva, sistema, domanda, impostazioni, conRicerca)
            if (esito is EsitoAi.Risposta) {
                return@withContext esito.copy(diRiserva = true)
            }
        }

        // Si riporta il guaio del **principale**: e' quello da sistemare.
        primo
    }

    /**
     * Quali modelli vede la chiave di un fornitore, adesso.
     *
     * **E' la risposta a "quale identificativo devo scrivere".** I nomi dei
     * modelli vengono ritirati ogni pochi mesi e un nome ritirato si presenta
     * come un 404 che sembra un problema di chiave; le guide in rete restano
     * ferme a nomi che non esistono piu'. Questo elenco lo dice il fornitore alla
     * chiave che ce l'ha davvero, dal telefono, senza intermediari.
     */
    suspend fun modelliVisibili(modello: Modello): EsitoModelli = withContext(Dispatchers.IO) {
        if (!Rete.disponibile(context)) {
            return@withContext EsitoModelli.Guaio(modello, GuaioAi.SenzaRete)
        }
        val chiave = chiavi.chiave(modello)
            ?: return@withContext EsitoModelli.Guaio(modello, GuaioAi.SenzaChiave)

        when (val esito = Rete.prendiConEsito(Ai.indirizzoModelli(modello), intestazioni(modello, chiave))) {
            is EsitoHttp.Riuscito -> {
                val elenco = Ai.leggiModelli(esito.corpo)
                if (elenco.isEmpty()) EsitoModelli.Guaio(modello, GuaioAi.Vuota(modello))
                else EsitoModelli.Riuscito(modello, elenco)
            }
            is EsitoHttp.Rifiutato -> EsitoModelli.Guaio(
                modello,
                GuaioAi.Rifiutata(modello, esito.codice, Ai.errore(esito.corpo)),
            )
            EsitoHttp.Muto -> EsitoModelli.Guaio(modello, GuaioAi.SenzaRete)
        }
    }

    /**
     * Come si presenta la chiave, per fornitore.
     *
     * **Mai nell'indirizzo**, in nessuno dei tre casi: un Uri finisce nei log di
     * sistema e nella cronologia dei proxy, un'intestazione molto meno.
     */
    private fun intestazioni(modello: Modello, chiave: String): Map<String, String> =
        when (modello) {
            Modello.GEMINI -> mapOf("x-goog-api-key" to chiave)
            Modello.GROK, Modello.GROQ -> mapOf("Authorization" to "Bearer $chiave")
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
                intestazioni = intestazioni(modello, chiave),
            )
            Modello.GROK -> Rete.postaConEsito(
                indirizzo = Ai.indirizzoGrok(),
                corpo = Ai.corpoGrok(nome, sistema, domanda, conRicerca),
                intestazioni = intestazioni(modello, chiave),
            )
            // Su Groq la ricerca web non e' un parametro della richiesta ma una
            // proprieta' del modello scelto: `conRicerca` qui non ha una leva da
            // muovere, e inventarne una darebbe un 400.
            Modello.GROQ -> Rete.postaConEsito(
                indirizzo = Ai.indirizzoGroq(),
                corpo = Ai.corpoGroq(nome, sistema, domanda),
                intestazioni = intestazioni(modello, chiave),
            )
        }

        return when (esito) {
            is EsitoHttp.Riuscito -> {
                val risposta = when (modello) {
                    Modello.GEMINI -> Ai.leggiGemini(esito.corpo)
                    Modello.GROK -> Ai.leggiGrok(esito.corpo)
                    Modello.GROQ -> Ai.leggiGroq(esito.corpo)
                }
                if (risposta == null) {
                    EsitoAi.Guaio(GuaioAi.Vuota(modello))
                } else {
                    EsitoAi.Risposta(
                        risposta = risposta,
                        diRiserva = false,
                        impronta = Ai.impronta(esito.corpo),
                    )
                }
            }
            is EsitoHttp.Rifiutato -> EsitoAi.Guaio(
                GuaioAi.Rifiutata(modello, esito.codice, Ai.errore(esito.corpo)),
            )
            EsitoHttp.Muto -> EsitoAi.Guaio(GuaioAi.SenzaRete)
        }
    }
}
