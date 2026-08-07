package it.myacamperlife.app.archivio

import kotlinx.serialization.Serializable

/**
 * Le impostazioni, in `impostazioni.json`.
 *
 * Stanno nella cartella d'archivio e non nelle preferenze dell'app perche'
 * l'archivio deve bastare a se stesso: reinstallando l'app, i km con un pieno
 * tornano da soli invece di lasciare l'autonomia muta senza spiegazione.
 *
 * Le chiavi API **non stanno qui**: quelle vanno nell'archivio cifrato
 * dell'app. Questa cartella puo' finire dentro una cartella sincronizzata su
 * un cloud, e una chiave in chiaro li' dentro sarebbe un errore difficile da
 * accorgersi.
 *
 * Ogni campo ha un valore di riposo, cosi' un file scritto da una versione
 * piu' vecchia si legge comunque: e' la stessa tolleranza dell'intestazione
 * dei CSV.
 */
@Serializable
data class Impostazioni(
    /** Quanti chilometri fa il mezzo con un serbatoio pieno. */
    val kmConUnPieno: Int? = null,
    /**
     * Se il riepilogo della sera deve arrivare. Acceso di riposo: e' la
     * funzione, e chi non la vuole la spegne in due tocchi.
     */
    val briefingAttivo: Boolean = true,
    /** L'ora del riepilogo, 0-23. Le 19:00 come nel sistema di prima. */
    val oraBriefing: Int = 19,
) {
    /** L'ora riportata dentro il quadrante, comunque sia scritta nel file. */
    val ora: Int get() = oraBriefing.coerceIn(0, 23)
}
