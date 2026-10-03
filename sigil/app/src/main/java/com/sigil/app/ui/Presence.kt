package com.sigil.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * "Presence, not passwords": anything sealed needs the person holding the phone to prove
 * it's them - fingerprint, face, or the screen lock - before it's shown or copied. A success
 * counts for a minute, so revealing two things in a row asks once (the prototype's Face ID HUD).
 */
class Presence(private val activity: FragmentActivity, private val vm: SigilViewModel) {

    fun require(reason: String, onPresent: () -> Unit) {
        if (vm.isPresent()) {
            onPresent()
            return
        }
        authenticate(reason, onPresent)
    }

    /** Always asks, ignoring a recent success - for unlocking the app. */
    fun authenticate(reason: String, onPresent: () -> Unit) {
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock at all: nothing to check against, so let them through and say why.
            vm.noScreenLock()
            onPresent()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.markPresent()
                    onPresent()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setSubtitle("Sigil")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}

val LocalPresence = staticCompositionLocalOf<Presence> { error("Presence not provided") }
