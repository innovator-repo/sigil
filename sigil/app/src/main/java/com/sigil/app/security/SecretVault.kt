package com.sigil.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts every secret value (passwords, card numbers, TOTP seeds, PINs...) with an
 * AES-256-GCM key that lives only in the Android Keystore and never leaves it - Sigil
 * itself never sees the raw key material, and it isn't extractable even by a rooted device.
 * Only ciphertext + IV are ever written to the Room database; plaintext exists in memory
 * only for as long as something is actively revealed on screen.
 */
class SecretVault {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Encrypts [plaintext], returning ciphertext and the IV GCM needs to decrypt it. */
    fun seal(plaintext: String): SealedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return SealedSecret(ciphertext, cipher.iv)
    }

    /** Decrypts a value sealed with [seal]. Only ever called right before a reveal/copy/compare. */
    fun open(sealed: SealedSecret): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, sealed.iv))
        return String(cipher.doFinal(sealed.ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sigil_secret_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

data class SealedSecret(val ciphertext: ByteArray, val iv: ByteArray)
