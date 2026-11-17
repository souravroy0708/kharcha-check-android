package com.kharchacheck.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets (the auto-backup password) stored encrypted with a non-exportable Android Keystore key,
 * in noBackupFilesDir. Lets backups run unattended without ever storing the password in plain text.
 */
class SecretBox(context: Context, private val name: String) {
    private val file = File(context.noBackupFilesDir, "$name.secret")
    private val alias = "kharcha_secret_$name"

    fun put(value: CharArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = String(value).toByteArray(Charsets.UTF_8)
        file.writeBytes(cipher.iv + cipher.doFinal(bytes))
        bytes.fill(0)
    }

    fun get(): CharArray? = runCatching {
        val blob = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        String(cipher.doFinal(blob, 12, blob.size - 12), Charsets.UTF_8).toCharArray()
    }.getOrNull()

    fun clear() { file.delete() }
    val isSet: Boolean get() = file.exists()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }
}
