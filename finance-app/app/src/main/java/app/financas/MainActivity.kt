package app.financas

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import app.financas.reminders.EXTRA_OPEN_AREA
import app.financas.security.AppLock
import app.financas.ui.AppRoot
import app.financas.ui.theme.FinanceTheme

/** FragmentActivity: exigido pelo pedido de digital/PIN (BiometricPrompt). */
class MainActivity : FragmentActivity() {
    /** Área pedida por uma notificação (ex.: "investments"); consumida pela navegação. */
    private val openArea = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openArea.value = intent?.getStringExtra(EXTRA_OPEN_AREA)
        setContent {
            FinanceTheme {
                AppRoot(openArea = openArea.value, onAreaOpened = { openArea.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_AREA)?.let { openArea.value = it }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        // Rotação da tela não conta como sair do app.
        if (!isChangingConfigurations) AppLock.onAppStopped()
    }
}
