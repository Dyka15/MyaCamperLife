package it.myacamperlife.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * I colori dell'app, presi dall'illustrazione dell'icona.
 *
 * Carrozzeria, cartello, pino, asfalto: sono i colori del camper disegnato, ed
 * erano gia' la cosa piu' riconoscibile che l'app avesse. Prima la tavolozza era
 * un'altra — terracotta e verde — e **non l'ha mai vista nessuno**, perche' il
 * colore dinamico la sostituiva con quella dello sfondo del telefono.
 *
 * Il **mattone** e' l'unico che non viene dal disegno, ed e' entrato per un
 * lavoro preciso: le date che dividono l'itinerario in giornate. In crema si
 * confondevano con i nomi delle tappe — stesso colore, peso diverso, e il peso
 * da solo non segna dove finisce un giorno. E' il rosso della strada italiana:
 * cartelli, mattoni, tetti. **Non e' il rosso d'errore**, che resta quello di
 * Material e vuol dire un'altra cosa.
 */
private val Carrozzeria = Color(0xFFE7D9BC)
private val CarrozzeriaChiara = Color(0xFFF3EADA)
private val Cartello = Color(0xFF8A5A33)
private val CartelloScuro = Color(0xFF4A2E17)
private val Pino = Color(0xFF2F5D50)
private val PinoChiaro = Color(0xFF8FBFAE)
private val Mattone = Color(0xFFB4432A)
private val MattoneChiaro = Color(0xFFF0977F)
private val Asfalto = Color(0xFF16120F)
private val AsfaltoTiepido = Color(0xFF2A231D)
private val Carta = Color(0xFFFFFBF5)

/**
 * Il chiaro: fondo carta, accento cartello.
 *
 * L'accento e' il marrone dei cartelli stradali e non il cielo: su un fondo
 * chiaro l'azzurro dell'illustrazione perde contrasto, e un accento che non si
 * legge non e' un accento.
 */
private val ColoriChiari = lightColorScheme(
    primary = Cartello,
    onPrimary = Color.White,
    primaryContainer = CarrozzeriaChiara,
    onPrimaryContainer = CartelloScuro,
    secondary = Pino,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E6DE),
    onSecondaryContainer = Color(0xFF17352C),
    tertiary = Mattone,
    onTertiary = Color.White,
    background = Carta,
    onBackground = Color(0xFF1E1913),
    surface = Carta,
    onSurface = Color(0xFF1E1913),
    surfaceVariant = Color(0xFFEDE3D4),
    onSurfaceVariant = Color(0xFF5C5346),
    outline = Color(0xFF8C8172),
)

/**
 * Lo scuro: fondo asfalto, accento carrozzeria.
 *
 * E' il tema in cui l'app si usa davvero — di sera, dentro un camper — e la
 * carrozzeria chiara sull'asfalto e' esattamente il contrasto dell'illustrazione.
 */
private val ColoriScuri = darkColorScheme(
    primary = Carrozzeria,
    onPrimary = CartelloScuro,
    primaryContainer = AsfaltoTiepido,
    onPrimaryContainer = Carrozzeria,
    secondary = PinoChiaro,
    onSecondary = Color(0xFF10281F),
    secondaryContainer = Color(0xFF23453A),
    onSecondaryContainer = Color(0xFFCDE7DC),
    tertiary = MattoneChiaro,
    onTertiary = Color(0xFF3A1207),
    background = Asfalto,
    onBackground = Color(0xFFECE3D6),
    surface = Asfalto,
    onSurface = Color(0xFFECE3D6),
    surfaceVariant = Color(0xFF352D25),
    onSurfaceVariant = Color(0xFFB6A997),
    outline = Color(0xFF8A7D6C),
)

/**
 * @param coloriDinamici prende i colori dallo sfondo del telefono.
 *
 * **Adesso e' spento.** Era acceso, e il risultato era che l'app non aveva un
 * aspetto suo: cambiava con il wallpaper, e la tavolozza scritta qui sopra non
 * compariva mai — nemmeno una volta, da quando esiste. Ora che c'e' un'icona con
 * un mondo suo, l'app le somiglia su qualunque telefono. Resta un parametro e non
 * una costante perche' un domani puo' tornare una scelta; dev'essere una scelta.
 */
@Composable
fun MyaTheme(
    temaScuro: Boolean = isSystemInDarkTheme(),
    coloriDinamici: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colori = when {
        coloriDinamici && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contesto = LocalContext.current
            if (temaScuro) dynamicDarkColorScheme(contesto) else dynamicLightColorScheme(contesto)
        }
        temaScuro -> ColoriScuri
        else -> ColoriChiari
    }

    MaterialTheme(colorScheme = colori, content = content)
}
