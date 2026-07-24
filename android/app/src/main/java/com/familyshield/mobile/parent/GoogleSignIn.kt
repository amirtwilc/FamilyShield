package com.familyshield.mobile.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.familyshield.mobile.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

/**
 * Returns a lambda that runs the Google sign-in (Credential Manager) flow: it asks
 * for a Google ID token scoped to our Web client id, then hands it to the
 * ViewModel to exchange for our session. Cancellation is silent; other failures
 * surface as an error.
 */
@Composable
fun rememberGoogleSignIn(vm: ParentViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val response = CredentialManager.create(context).getCredential(context, request)
                val cred = response.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    vm.googleSignIn(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                } else {
                    vm.showError("Unexpected sign-in credential")
                }
            } catch (_: GetCredentialCancellationException) {
                // user dismissed the picker — not an error
            } catch (e: GetCredentialException) {
                vm.showError(e.message ?: "Google sign-in failed")
            } catch (_: GoogleIdTokenParsingException) {
                vm.showError("Could not read the Google credential")
            }
        }
    }
}
