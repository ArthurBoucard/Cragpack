package app.cragpack.feature.account

import androidx.compose.runtime.Composable

/** An ID token from the phone's own Google sign-in, and the raw nonce whose hash Google signed into it. */
class IdToken(val token: String, val nonce: String)

/** Google's sign-in couldn't run on this phone (no Google account, no Play services); [message] says what to do. */
class GoogleSignInUnavailable(message: String) : Exception(message)

fun interface GoogleSignIn {
    /** Shows Google's account picker; null if the person closed it. Throws [GoogleSignInUnavailable]. */
    suspend fun idToken(): IdToken?
}

/**
 * Google's sign-in on this phone, or null where there isn't one: no client id in local.properties, or iOS.
 * ponytail: iOS needs the GoogleSignIn SDK added in Xcode (and Sign in with Apple a paid Apple account): next to set up.
 */
@Composable
expect fun rememberGoogleSignIn(): GoogleSignIn?
