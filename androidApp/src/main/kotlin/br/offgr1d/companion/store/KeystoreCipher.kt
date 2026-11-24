package br.offgr1d.companion.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM com chave presa ao Keystore: ela não sai do aparelho e some no pânico. */
class KeystoreCipher : BlobCipher {
    private val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    @Synchronized
    private fun key(): SecretKey = (keyStore.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV) { "arquivo incompleto" }
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV))
        return c.doFinal(sealed, IV, sealed.size - IV)
    }

    @Synchronized
    override fun destroyKey() = keyStore.deleteEntry(ALIAS)

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "2b-data"
        const val MODE = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
