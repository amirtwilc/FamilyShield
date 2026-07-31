package com.familyshield.mobile.parent

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.familyshield.mobile.BuildConfig
import com.familyshield.mobile.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

internal fun googleSignInRequest(serverClientId: String): GetCredentialRequest {
    val option = GetSignInWithGoogleOption.Builder(serverClientId)
        .build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
}

internal suspend fun <T> withFreshCredentialSelection(
    clearCredentialState: suspend () -> Unit,
    selectCredential: suspend () -> T,
): T {
    runCatching { clearCredentialState() }
    return selectCredential()
}

/**
 * Returns a lambda that runs the Google sign-in (Credential Manager) flow: it asks
 * for a Google ID token scoped to our Web client id, then hands it to the
 * ViewModel to exchange for our session. Cancellation is silent; other failures
 * surface as an error.
 */
@Composable
private fun rememberGoogleCredential(
    onToken: (String) -> Unit,
    onError: (String?) -> Unit,
): () -> Unit {
    // LocalContext is localized by MainActivity and is a configuration context,
    // not an Activity. Credential Manager needs the real Activity to show UI.
    val activity = LocalActivityResultRegistryOwner.current as? Activity
    val unavailableMessage = stringResource(R.string.google_signin_unavailable)
    val scope = rememberCoroutineScope()
    return googleSignIn@{
        if (activity == null) {
            onError(unavailableMessage)
            return@googleSignIn
        }
        scope.launch {
            try {
                // This flow starts from an explicit "Continue with Google" button.
                // The dedicated button option shows Google's account-selection UI
                // instead of preferring a previously authorized account.
                val credentialManager = CredentialManager.create(activity)
                // Google can retain an active credential after Firebase signs out
                // and then limit the picker to that account. Clear the provider's
                // app session so an explicit button press offers every eligible
                // account on the device. Failure to clear must not block sign-in.
                val request = googleSignInRequest(BuildConfig.GOOGLE_CLIENT_ID)
                val response = withFreshCredentialSelection(
                    clearCredentialState = {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    },
                    selectCredential = {
                        credentialManager.getCredential(activity, request)
                    },
                )
                val cred = response.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    onToken(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                } else {
                    onError("Unexpected sign-in credential")
                }
            } catch (_: GetCredentialCancellationException) {
                // user dismissed the picker — not an error
            } catch (e: GetCredentialException) {
                onError(e.message ?: "Google sign-in failed")
            } catch (_: GoogleIdTokenParsingException) {
                onError("Could not read the Google credential")
            }
        }
    }
}

@Composable
fun rememberGoogleSignIn(vm: ParentViewModel): () -> Unit =
    rememberGoogleCredential(vm::googleSignIn, vm::showError)

@Composable
fun rememberGoogleReauthentication(vm: ParentViewModel): () -> Unit =
    rememberGoogleCredential(vm::signOutAllDevicesWithGoogle, vm::showError)
