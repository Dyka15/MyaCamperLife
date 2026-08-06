package it.myacamperlife.app

import android.app.Application
import it.myacamperlife.app.archivio.Archivio
import java.io.File

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
}
