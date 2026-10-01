package app.financas.ui.theme

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

val IncomeColor = Color(0xFF2E7D32)
val ExpenseColor = Color(0xFFC62828)
val WarningColor = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2CD),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD6B2),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFA6F2CD),
    secondary = Color(0xFFB3CCBF),
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
