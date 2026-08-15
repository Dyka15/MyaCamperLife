package it.myacamperlife.app

import android.app.Application
import it.myacamperlife.app.archivio.Archivio
import it.myacamperlife.app.avvisi.Avvisi
import it.myacamperlife.app.avvisi.GuardianoBriefing
import it.myacamperlife.app.avvisi.SvegliaBriefing
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyaApplication : Application() {

    /**
     * L'archivio nell'area privata dell'app: funziona sempre e non chiede
     * permessi. Lo specchio nella cartella scelta dall'utente arriva alla
     * fase 9.
     *
     * Qui non si tocca il disco: creare le cartelle e' lavoro da fare fuori
     * dal thread principale, e lo fa chi usa l'archivio.
     */
    val archivio: Archivio by lazy {
        Archivio(File(filesDir, Archivio.NOME_CARTELLA))
    }

    override fun onCreate() {
        super.onCreate()
        // Il canale si dichiara subito: crearlo non costa niente e non
        // richiede il permesso di notificare. Senza, la prima notifica
        // verrebbe scartata.
        Avvisi(this).preparaCanale()

        // Riarmare a ogni avvio e' la rete di sicurezza piu' economica: se una
        // sveglia e' andata persa, aprire l'app la rimette. Su un thread di
        // I/O, perche' le impostazioni stanno in un file.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val impostazioni = archivio.impostazioni()
            val prossima = SvegliaBriefing.programma(
                context = this@MyaApplication,
                attivo = impostazioni.briefingAttivo,
                ora = impostazioni.ora,
            )
            // Scrive solo se e' cambiata: aprire l'app dieci volte non deve
            // toccare il file dieci volte.
            archivio.annotaSveglia(prossima)
            GuardianoBriefing.programma(this@MyaApplication)
        }
    }
}
