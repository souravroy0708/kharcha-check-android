package com.kharchacheck.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the database passphrase, wrapped (AES-GCM) by a non-exportable Android Keystore key.
 * Stored in noBackupFilesDir so it is never copied by cloud backup or device transfer.
 */
class DbKeyStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "db.key")
    private val alias = "kharcha_db_wrap_v1"

    fun passphraseOrNull(): ByteArray? {
        if (!file.exists()) return null
        return runCatching {
            val blob = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, blob, 0, 12))
            cipher.doFinal(blob, 12, blob.size - 12)
        }.getOrNull()
    }

    fun createPassphrase(): ByteArray {
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val blob = cipher.iv + cipher.doFinal(pass)
        val tmp = File(file.parentFile, "db.key.tmp")
        tmp.writeBytes(blob)
        tmp.renameTo(file)
        return pass
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
