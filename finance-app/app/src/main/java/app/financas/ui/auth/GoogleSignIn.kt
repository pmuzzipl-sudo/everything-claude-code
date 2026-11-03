package app.financas.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Usuário conectado (ou null), atualizado quando entra ou sai. */
fun FirebaseAuth.userFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class Error(val message: String) : SignInResult
}

/** Login com a conta Google (seletor de contas do Android) e autenticação no Firebase. */
suspend fun signInWithGoogle(activityContext: Context): SignInResult {
    // Gerado pelo plugin google-services a partir do google-services.json.
    val clientIdRes = activityContext.resources.getIdentifier("default_web_client_id", "string", activityContext.packageName)
    if (clientIdRes == 0) return SignInResult.Error("Login com Google não configurado no Firebase.")

    return try {
        val option = GetSignInWithGoogleOption.Builder(activityContext.getString(clientIdRes)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return SignInResult.Error("Tipo de credencial inesperado.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        SignInResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        SignInResult.Cancelled
    } catch (e: Exception) {
        SignInResult.Error(e.localizedMessage ?: "Falha no login.")
    }
}
