package app.cragpack.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import app.cragpack.data.SupabaseConfig
import app.cragpack.data.newId
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest

/** Credential Manager's "Sign in with Google" sheet (the official Android SDK, tech spec §2). */
@Composable
actual fun rememberGoogleSignIn(): GoogleSignIn? {
    val context = LocalContext.current
    if (SupabaseConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return null
    return remember(context) {
        GoogleSignIn {
            val nonce = newId()
            val hashed = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).joinToString("") { "%02x".format(it) }
            val option = GetSignInWithGoogleOption.Builder(SupabaseConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
            try {
                val result = CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
                IdToken(GoogleIdTokenCredential.createFrom(result.credential.data).idToken, nonce)
            } catch (_: GetCredentialCancellationException) {
                null
            }
        }
    }
}
