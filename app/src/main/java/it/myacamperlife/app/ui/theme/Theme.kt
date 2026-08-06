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

private val Terra40 = Color(0xFF9C4221)
private val Terra80 = Color(0xFFFFB59B)
private val TerraScura = Color(0xFF5C2410)
private val Verde40 = Color(0xFF4F6A52)
private val Verde80 = Color(0xFFB8CDBA)

private val ColoriChiari = lightColorScheme(
    primary = Terra40,
    onPrimary = Color.White,
    primaryContainer = Terra80,
    onPrimaryContainer = TerraScura,
    secondary = Verde40,
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
)

private val ColoriScuri = darkColorScheme(
    primary = Terra80,
    onPrimary = TerraScura,
    primaryContainer = Terra40,
    onPrimaryContainer = Terra80,
    secondary = Verde80,
    background = Color(0xFF16120F),
    surface = Color(0xFF16120F),
)

/**
 * @param coloriDinamici su Android 12+ adatta i colori allo sfondo del
 *   telefono. Attivo di default: sul Poco F7 e' il comportamento che l'utente
 *   si aspetta.
 */
@Composable
fun MyaTheme(
    temaScuro: Boolean = isSystemInDarkTheme(),
    coloriDinamici: Boolean = true,
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
