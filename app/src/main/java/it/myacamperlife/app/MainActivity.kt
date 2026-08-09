package it.myacamperlife.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.myacamperlife.app.archivio.AlberoSpecchio
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.EsitoFusione
import it.myacamperlife.app.archivio.Fusione
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.archivio.Specchio
import it.myacamperlife.app.archivio.SpecchioLavoro
import it.myacamperlife.app.avvisi.SvegliaBriefing
import it.myacamperlife.app.rete.Assistente
import it.myacamperlife.app.rete.Geocodifica
import it.myacamperlife.app.rete.Scorte
import it.myacamperlife.app.ui.MyaApp
import it.myacamperlife.app.ui.theme.MyaTheme
import it.myacamperlife.app.ui.viaggi.ViaggiViewModel

class MainActivity : ComponentActivity() {

    private val vista: ViaggiViewModel by viewModels {
        viewModelFactory {
            initializer {
                ViaggiViewModel(
                    archivio = (application as MyaApplication).archivio,
                    documenti = Documenti(applicationContext),
                    posizioni = Posizioni(applicationContext),
                    riarma = { impostazioni ->
                        SvegliaBriefing.programma(
                            context = applicationContext,
                            attivo = impostazioni.briefingAttivo,
                            ora = impostazioni.ora,
                        )
                    },
                    scorte = Scorte(
                        context = applicationContext,
                        archivio = (application as MyaApplication).archivio,
                    ),
                    geocodifica = Geocodifica(
                        context = applicationContext,
                        archivio = (application as MyaApplication).archivio,
                    ),
                    rispecchia = { SpecchioLavoro.programma(applicationContext) },
                    esportaTutto = { esportaArchivio() },
                    fondiDallaCartella = { fondiDallaCartella() },
                    assistente = Assistente(applicationContext),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solo alla prima creazione: a schermo ruotato `onCreate` gira di
        // nuovo con lo stesso intent, e senza questa guardia l'itinerario
        // verrebbe importato una seconda volta.
        if (savedInstanceState == null) {
            itinerarioCondiviso(intent)?.let(vista::importa)
        }

        setContent {
            MyaTheme {
                MyaApp(vista)
            }
        }
    }

    /**
     * Copia tutto l'archivio nella cartella scelta, subito.
     *
     * Sta qui e non nel ViewModel perche' e' l'unico pezzo che ha bisogno di un
     * `Context` e del content resolver. Restituisce quanti file ha toccato, o
     * `null` se la cartella non c'e' piu': dopo una reinstallazione il permesso
     * e' perso e va riscelta.
     */
    private suspend fun esportaArchivio(): Int? {
        val archivio = (application as MyaApplication).archivio
        val salvata = archivio.impostazioni().cartellaSpecchio ?: return null
        val uri = runCatching { Uri.parse(salvata) }.getOrNull() ?: return null
        if (!Specchio.accessibile(applicationContext, uri)) return null

        val esito = Specchio(applicationContext, uri).rispecchia(archivio.radiceArchivio())
        return if (esito.riuscito) esito.toccati else null
    }

    /**
     * Legge l'archivio che c'e' gia' nella cartella scelta e lo fonde con
     * questo.
     *
     * L'unico verso in cui si legge da SAF, e succede una volta: quando la
     * cartella viene assegnata, o quando lo si chiede dalle impostazioni. Sta
     * qui per la stessa ragione dell'esportazione — serve un `Context` — e
     * restituisce `null` se la cartella non c'e' o non e' piu' nostra.
     */
    private suspend fun fondiDallaCartella(): EsitoFusione? {
        val archivio = (application as MyaApplication).archivio
        val salvata = archivio.impostazioni().cartellaSpecchio ?: return null
        val uri = runCatching { Uri.parse(salvata) }.getOrNull() ?: return null
        if (!Specchio.accessibile(applicationContext, uri)) return null

        return runCatching {
            Fusione(archivio).fondi(AlberoSpecchio(applicationContext, uri))
        }.getOrNull()
    }

    /**
     * L'Uri di un itinerario arrivato da un'altra app. L'avvio normale non
     * porta documenti: solo `SEND` e `VIEW`.
     */
    private fun itinerarioCondiviso(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}
