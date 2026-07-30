package com.familyshield.mobile.parent

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.Locale

sealed interface ParentAuthState {
    data object SignedOut : ParentAuthState
    data class AwaitingVerification(val email: String) : ParentAuthState
    data class SignedIn(val uid: String, val email: String) : ParentAuthState
    data object Expired : ParentAuthState
}

class ProviderLinkRequiredException(message: String) : Exception(message)

interface ParentAuthGateway {
    fun currentState(): ParentAuthState
    suspend fun register(email: String, password: String): ParentAuthState
    suspend fun login(email: String, password: String): ParentAuthState
    suspend fun google(idToken: String): ParentAuthState
    suspend fun signInWithCustomToken(customToken: String): ParentAuthState
    suspend fun sendPasswordReset(email: String)
    suspend fun resendVerification()
    suspend fun refreshVerification(): ParentAuthState
    suspend fun reauthenticateWithPassword(password: String)
    suspend fun reauthenticateWithGoogle(idToken: String)
    suspend fun idToken(forceRefresh: Boolean = false): String
    suspend fun appCheckToken(forceRefresh: Boolean = false): String?
    fun logout()
}

class FirebaseParentAuthGateway : ParentAuthGateway {
    private val auth: FirebaseAuth
    private var pendingGoogleCredential: AuthCredential? = null

    init {
        FirebaseApp.getInstance()
        auth = FirebaseAuth.getInstance()
        setEmailLanguage()
    }

    private fun setEmailLanguage() {
        auth.setLanguageCode(if (Locale.getDefault().language in setOf("he", "iw")) "he" else "en")
    }

    override fun currentState(): ParentAuthState {
        val user = auth.currentUser ?: return ParentAuthState.SignedOut
        val email = user.email.orEmpty()
        return if (user.isEmailVerified) ParentAuthState.SignedIn(user.uid, email)
        else ParentAuthState.AwaitingVerification(email)
    }

    override suspend fun register(email: String, password: String): ParentAuthState {
        setEmailLanguage()
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Firebase did not create the account")
        user.sendEmailVerification().await()
        return ParentAuthState.AwaitingVerification(user.email ?: email)
    }

    override suspend fun login(email: String, password: String): ParentAuthState {
        setEmailLanguage()
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase did not return an account")
        pendingGoogleCredential?.let { credential ->
            user.linkWithCredential(credential).await()
            pendingGoogleCredential = null
        }
        user.reload().await()
        return currentState()
    }

    override suspend fun google(idToken: String): ParentAuthState {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        try {
            auth.signInWithCredential(credential).await()
        } catch (collision: FirebaseAuthUserCollisionException) {
            pendingGoogleCredential = credential
            throw ProviderLinkRequiredException(
                "Sign in once with the existing method to securely link Google.",
            )
        }
        return currentState()
    }

    override suspend fun signInWithCustomToken(customToken: String): ParentAuthState {
        auth.signInWithCustomToken(customToken).await()
        return currentState()
    }

    override suspend fun sendPasswordReset(email: String) {
        setEmailLanguage()
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun resendVerification() {
        setEmailLanguage()
        auth.currentUser?.sendEmailVerification()?.await()
            ?: error("No account is awaiting verification")
    }

    override suspend fun refreshVerification(): ParentAuthState {
        auth.currentUser?.reload()?.await() ?: return ParentAuthState.SignedOut
        return currentState()
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: error("Session expired")
        val email = user.email ?: error("This account has no email address")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    override suspend fun reauthenticateWithGoogle(idToken: String) {
        val user = auth.currentUser ?: error("Session expired")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun idToken(forceRefresh: Boolean): String =
        auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
            ?: throw IllegalStateException("Session expired")

    override suspend fun appCheckToken(forceRefresh: Boolean): String? =
        runCatching { FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await().token }
            .getOrNull()

    override fun logout() {
        pendingGoogleCredential = null
        auth.signOut()
    }
}
