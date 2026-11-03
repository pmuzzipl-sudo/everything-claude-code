package app.financas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tons suaves, legíveis sobre fundo escuro sem chamar atenção demais.
val IncomeColor = Color(0xFF7FB89A)
val ExpenseColor = Color(0xFFD98A8A)
val WarningColor = Color(0xFFD9B26F)

/** Tema escuro e sóbrio: grafite com detalhes em verde-acinzentado. Usado sempre, independente do sistema. */
private val SoberDark = darkColorScheme(
    primary = Color(0xFF9FB8AD),
    onPrimary = Color(0xFF0F1F19),
    primaryContainer = Color(0xFF26332E),
    onPrimaryContainer = Color(0xFFD6E5DE),
    secondary = Color(0xFFAAB4BE),
    onSecondary = Color(0xFF1B2229),
    secondaryContainer = Color(0xFF2A3138),
    onSecondaryContainer = Color(0xFFDCE3EA),
    tertiary = Color(0xFFC8B68E),
    onTertiary = Color(0xFF2B2413),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE2E3E4),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFE2E3E4),
    surfaceVariant = Color(0xFF2A2E33),
    onSurfaceVariant = Color(0xFFA6ACB3),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF16181B),
    surfaceContainer = Color(0xFF1A1D20),
    surfaceContainerHigh = Color(0xFF1F2226),
    surfaceContainerHighest = Color(0xFF25292D),
    outline = Color(0xFF5C636B),
    outlineVariant = Color(0xFF33383E),
    error = Color(0xFFE08A8A),
    onError = Color(0xFF3A1010),
    errorContainer = Color(0xFF3A2526),
    onErrorContainer = Color(0xFFF2D4D4),
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SoberDark, content = content)
}
