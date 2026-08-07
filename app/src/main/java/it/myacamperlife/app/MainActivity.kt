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
import it.myacamperlife.app.archivio.Documenti
import it.myacamperlife.app.archivio.Posizioni
import it.myacamperlife.app.avvisi.SvegliaBriefing
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
