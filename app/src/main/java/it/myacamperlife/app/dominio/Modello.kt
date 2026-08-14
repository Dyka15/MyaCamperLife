package it.myacamperlife.app.dominio

/**
 * I fornitori di modelli: uno principale, gli altri di riserva.
 *
 * **Perche' piu' di uno.** Una funzione che dipende da un solo fornitore e' una
 * funzione che sparisce quando quel fornitore ha una brutta giornata — e succede
 * a tutti. Il codice del client e' lo stesso; cambiano l'indirizzo, la forma del
 * corpo e dove sta la risposta dentro il JSON.
 *
 * **L'identificativo del modello e' un'impostazione, non una costante.** I nomi
 * dei modelli cambiano ogni pochi mesi e vengono ritirati; se fosse compilato
 * dentro, un ritiro renderebbe l'app muta finche' non se ne pubblica una nuova.
 * Scritto in `impostazioni.json` si corregge in dieci secondi, e l'errore del
 * servizio si legge in interfaccia invece di essere un fallimento silenzioso.
 */
enum class Modello(val codice: String, val nome: String, val modelloDiRiposo: String) {
    /**
     * Gemini, il principale: e' quello che il sistema n8n usava, quindi il
     * prompt di Esplora si trasporta senza riscritture.
     */
    GEMINI(
        codice = "gemini",
        nome = "Gemini",
        // Un alias di fascia Flash: quota gratuita generosa e ricerca inclusa.
        // Se l'alias cambiasse, si corregge dalle impostazioni.
        modelloDiRiposo = "gemini-flash-latest",
    ),

    /** Grok, la riserva. */
    GROK(
        codice = "grok",
        nome = "Grok",
        modelloDiRiposo = "grok-4-fast",
    ),

    /**
     * Groq: la fascia gratuita senza carta di credito.
     *
     * **Non e' Grok con una lettera diversa**: Grok e' il modello di xAI, Groq e'
     * un servizio che esegue modelli aperti su hardware proprio. I due nomi si
     * confondono a occhio e non c'entrano niente l'uno con l'altro.
     *
     * Di riposo un sistema `compound` e non un modello secco, e la ragione e' una
     * regola di questa app: Esplora mostra **le fonti** di una risposta, perche'
     * una risposta su un'area di sosta che non si puo' verificare vale meno di
     * nessuna risposta. Su Groq la ricerca web ce l'hanno solo i `compound`; un
     * `openai/gpt-oss-120b` risponderebbe a memoria, senza un link da
     * controllare. Chi preferisce la velocita' alla verificabilita' cambia
     * l'identificativo dalle impostazioni.
     */
    GROQ(
        codice = "groq",
        nome = "Groq",
        modelloDiRiposo = "groq/compound-mini",
    );

    companion object {
        fun da(codice: String?): Modello? =
            entries.firstOrNull { it.codice.equals(codice?.trim(), ignoreCase = true) }
    }
}

/**
 * Cosa e' tornato da un modello.
 *
 * [fonti] sono i link che il modello dichiara di aver consultato. Non sono un
 * ornamento: una risposta su un'area di sosta che non si puo' verificare vale
 * meno di nessuna risposta, e queste sono le righe che permettono di
 * controllare.
 */
data class RispostaModello(
    val testo: String,
    val fonti: List<Fonte> = emptyList(),
    val modello: Modello,
)

data class Fonte(val titolo: String?, val indirizzo: String) {
    /** Il dominio, che in un elenco dice piu' di un indirizzo lunghissimo. */
    val dominio: String
        get() = runCatching { java.net.URI(indirizzo).host?.removePrefix("www.") }
            .getOrNull() ?: indirizzo

    fun etichetta(): String = titolo?.takeUnless { it.isBlank() } ?: dominio
}

/** Perche' una richiesta non ha prodotto una risposta. */
sealed interface GuaioAi {
    /** Nessuna chiave configurata per quel modello. */
    data object SenzaChiave : GuaioAi

    /** Niente rete: non e' un errore da mostrare come tale, e' l'offline. */
    data object SenzaRete : GuaioAi

    /**
     * Il servizio ha risposto male. [messaggio] e' quello che ha detto lui, non
     * una parafrasi: un identificativo di modello ritirato o una chiave
     * scaduta si riconoscono solo leggendo l'originale.
     */
    data class Rifiutata(val modello: Modello, val codice: Int, val messaggio: String?) : GuaioAi

    /** Ha risposto, ma dentro non c'era testo. */
    data class Vuota(val modello: Modello) : GuaioAi
}
