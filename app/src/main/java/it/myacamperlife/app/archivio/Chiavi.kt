package it.myacamperlife.app.archivio

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import it.myacamperlife.app.dominio.Modello

/**
 * Le chiavi API dei modelli, cifrate.
 *
 * **Non stanno in `impostazioni.json`, e adesso il motivo e' urgente.** Da
 * quando esiste lo specchio, quel file viene ricopiato nella cartella scelta
 * dall'utente — che spesso e' una cartella sincronizzata su un cloud. Una chiave
 * in chiaro finirebbe su Drive, in un file di testo, senza che nessuno se ne
 * accorga.
 *
 * Qui vivono in `EncryptedSharedPreferences`, cifrate con una chiave custodita
 * dal Keystore del dispositivo. Restano dentro l'installazione dell'app: **una
 * reinstallazione le porta via**, e vanno reinserite. E' il compromesso giusto —
 * l'alternativa sarebbe renderle esportabili, cioe' copiabili.
 */
class Chiavi(context: Context) {

    /**
     * Se il Keystore non collabora — succede su qualche ROM modificata — si
     * ripiega su preferenze in chiaro **senza chiavi**: l'app resta usabile e
     * Esplora con l'AI semplicemente non si configura. Meglio una funzione in
     * meno che un avvio che non parte.
     */
    private val preferenze: SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context,
            NOME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    val disponibile: Boolean get() = preferenze != null

    fun chiave(modello: Modello): String? =
        preferenze?.getString(modello.codice, null)?.takeUnless { it.isBlank() }

    fun salva(modello: Modello, chiave: String?) {
        val preferenze = preferenze ?: return
        val pulita = chiave?.trim()
        preferenze.edit().apply {
            if (pulita.isNullOrEmpty()) remove(modello.codice) else putString(modello.codice, pulita)
        }.apply()
    }

    fun configurato(modello: Modello): Boolean = chiave(modello) != null

    /**
     * Le ultime quattro cifre, per far vedere che c'e' senza mostrarla.
     *
     * Mostrare una chiave intera in una schermata di impostazioni e' un invito
     * a fotografarla per sbaglio insieme al resto.
     */
    fun coda(modello: Modello): String? = chiave(modello)?.takeLast(4)

    private companion object {
        const val NOME = "chiavi"
    }
}
