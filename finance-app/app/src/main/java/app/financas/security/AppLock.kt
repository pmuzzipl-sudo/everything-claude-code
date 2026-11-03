package app.financas.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Digital, rosto ou o PIN/padrão/senha de tela do próprio aparelho. */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Tempo fora do app a partir do qual ele volta a pedir desbloqueio. */
private const val RELOCK_AFTER_MS = 30_000L

/**
 * Estado do bloqueio do app (vale para o processo inteiro, sobrevive à rotação da tela).
 * Começa bloqueado; volta a bloquear depois de [RELOCK_AFTER_MS] em segundo plano.
 */
object AppLock {
    var unlocked by mutableStateOf(false)
        private set

    private var authenticating = false
    private var backgroundSince = 0L

    /** O aparelho tem bloqueio de tela (ou biometria) configurado? Sem isso não dá para proteger o app. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onAppStopped() {
        // A tela de PIN do sistema tira o app do primeiro plano; isso não conta.
        if (!authenticating) backgroundSince = SystemClock.elapsedRealtime()
    }

    fun onAppStarted() {
        if (!authenticating && backgroundSince != 0L && SystemClock.elapsedRealtime() - backgroundSince > RELOCK_AFTER_MS) {
            unlocked = false
        }
        backgroundSince = 0L
    }

    /** Ao ativar o bloqueio, o usuário já está dentro do app: não pede de novo agora. */
    fun markUnlocked() {
        unlocked = true
    }

    fun authenticate(activity: FragmentActivity, onError: (String) -> Unit = {}) {
        if (authenticating) return
        authenticating = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    onError(errString.toString())
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Minhas Finanças")
                .setSubtitle("Desbloqueie para ver suas finanças")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
