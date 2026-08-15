package it.myacamperlife.app.dominio

/**
 * Com'e' finita l'ultima volta che il riepilogo doveva partire.
 *
 * **Nasce da un guasto che non si poteva guardare.** «Non mi arriva la
 * notifica» ha almeno cinque spiegazioni — la sveglia non e' scattata, non
 * c'era niente da dire, manca il permesso di notificare, il riepilogo e' spento,
 * nessun viaggio e' corrente — e da fuori sono indistinguibili. Nessuna di
 * queste lascia una traccia: e' esattamente la forma di difetto che questa app
 * si e' impegnata a non avere.
 *
 * Ogni caso porta **il rimedio con se'**, perche' chi legge la riga e' chi deve
 * agire, e sta in mezzo a un campeggio col telefono in mano.
 */
sealed interface EsitoBriefing {

    fun riassunto(): String

    /** Mandata. Il titolo serve a riconoscere *quale* riepilogo era. */
    data class Mandato(val titolo: String) : EsitoBriefing {
        override fun riassunto(): String = "mandato: $titolo"
    }

    /**
     * Composto, ma non aveva niente da dire.
     *
     * **Non e' un guasto**, ed e' importante che si distingua da uno: un
     * riepilogo vuoto la sera dell'ultima tappa e' il comportamento giusto — una
     * notifica vuota insegna a ignorare le notifiche.
     */
    data object NienteDaDire : EsitoBriefing {
        override fun riassunto(): String =
            "niente da dire: nessuna tappa da fare, nessun rifornimento da segnalare"
    }

    /** Il permesso di notificare non c'e': da Android 13 la notifica sparisce. */
    data object SenzaPermesso : EsitoBriefing {
        override fun riassunto(): String =
            "scartata dal sistema: manca il permesso di notificare — concedilo qui sotto"
    }

    /** Nessun viaggio da cui comporre: l'archivio e' vuoto o sono tutti chiusi. */
    data object SenzaViaggio : EsitoBriefing {
        override fun riassunto(): String = "nessun viaggio corrente da cui comporre"
    }

    /** L'interruttore e' spento. Detto, perche' si dimentica. */
    data object Spento : EsitoBriefing {
        override fun riassunto(): String = "riepilogo spento nelle impostazioni"
    }
}
