package com.sigil.app

import com.sigil.app.security.SealedSecret
import com.sigil.app.security.SecretVault

/**
 * Robolectric has no Android Keystore, so screenshot tests seal with a pass-through vault.
 * It pads like AES-GCM's 16-byte tag so masked fields keep their real lengths.
 */
class TestSigilApplication : SigilApplication() {
    override fun createVault(): SecretVault = object : SecretVault {
        override fun seal(plaintext: String) = SealedSecret(plaintext.toByteArray() + ByteArray(16), ByteArray(12))
        override fun open(sealed: SealedSecret) = String(sealed.ciphertext, 0, sealed.ciphertext.size - 16)
    }
}
