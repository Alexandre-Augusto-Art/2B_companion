package br.offgr1d.companion.desktop

import br.offgr1d.companion.store.BlobCipher
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM. A chave fica num arquivo protegido pela conta do Windows (DPAPI) e some no pânico. */
class DpapiCipher(private val keyFile: File) : BlobCipher {
    private val random = SecureRandom()

    @Synchronized
    private fun key(create: Boolean): SecretKeySpec {
        val raw = if (keyFile.exists()) {
            Crypt32Util.cryptUnprotectData(keyFile.readBytes())
        } else {
            if (!create) throw IOException("chave ausente")
            ByteArray(32).also {
                random.nextBytes(it)
                keyFile.parentFile?.mkdirs()
                keyFile.writeBytes(Crypt32Util.cryptProtectData(it))
            }
        }
        return SecretKeySpec(raw, "AES")
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV).also(random::nextBytes)
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.ENCRYPT_MODE, key(create = true), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > IV) { "arquivo incompleto" }
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, sealed, 0, IV))
        return c.doFinal(sealed, IV, sealed.size - IV)
    }

    @Synchronized
    override fun destroyKey() {
        if (keyFile.exists() && !keyFile.delete()) throw IOException("não foi possível apagar a chave")
    }

    private companion object {
        const val MODE = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
