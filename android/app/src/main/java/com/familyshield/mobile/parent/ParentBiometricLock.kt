package com.familyshield.mobile.parent

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.familyshield.mobile.R

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

sealed class ParentBiometricAvailability {
    data class Available(val activity: FragmentActivity) : ParentBiometricAvailability()
    object NotEnrolled : ParentBiometricAvailability()
    object Unavailable : ParentBiometricAvailability()
}

fun parentBiometricAvailability(context: Context): ParentBiometricAvailability {
    val app = context.applicationContext
    val activity = context.findFragmentActivity() ?: return ParentBiometricAvailability.Unavailable
    return when (BiometricManager.from(app).canAuthenticate(AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> ParentBiometricAvailability.Available(activity)
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> ParentBiometricAvailability.NotEnrolled
        else -> ParentBiometricAvailability.Unavailable
    }
}

fun showParentBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onCancelOrError: () -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onCancelOrError()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.parent_lock_prompt_title))
        .setSubtitle(activity.getString(R.string.parent_lock_prompt_body))
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

fun openDeviceSecuritySettings(context: Context) {
    val app = context.applicationContext
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS)
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching {
            app.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
