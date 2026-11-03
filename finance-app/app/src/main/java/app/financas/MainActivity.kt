package app.financas

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import app.financas.security.AppLock
import app.financas.ui.AppRoot
import app.financas.ui.theme.FinanceTheme

/** FragmentActivity: exigido pelo pedido de digital/PIN (BiometricPrompt). */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceTheme {
                AppRoot()
            }
        }
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
