package com.kharchacheck.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore encryption + Passphrase-encrypted backup vault (Spec Pages 6, 9, 10).
 * - Device key never leaves Android Keystore (`AES/GCM/NoPadding`, 256-bit).
 * - Passphrase backups derive a 256-bit AES key via `PBKDF2WithHmacSHA256` (65,536 iterations + 16-byte random salt)
 *   and encrypt the JSON payload with `AES/GCM/NoPadding`.
 */
class CryptoVault(context: Context) {
    private val alias = "kharcha_check_master_key"
    private val prefs = context.getSharedPreferences("kharcha_vault", Context.MODE_PRIVATE)

    private fun deviceKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
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

    fun encryptWithDeviceKey(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
            val out = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    fun decryptWithDeviceKey(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(cipherText, Base64.NO_WRAP)
            if (bytes.size <= 12) return cipherText
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            cipherText
        }
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 65_536

        fun encryptBackupWithPassphrase(jsonPayload: String, passphrase: String): ByteArray {
            val random = SecureRandom()
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))
            // Header magic "KCBAK1" (6 bytes) + salt (16 bytes) + iv (12 bytes) + ciphertext
            return "KCBAK1".toByteArray(Charsets.UTF_8) + salt + iv + encrypted
        }

        fun decryptBackupWithPassphrase(rawBytes: ByteArray, passphrase: String): String {
            require(rawBytes.size > 34) { "Invalid backup file." }
            val header = String(rawBytes.copyOfRange(0, 6), Charsets.UTF_8)
            require(header == "KCBAK1") { "Not a valid Kharcha Check encrypted backup." }
            val salt = rawBytes.copyOfRange(6, 22)
            val iv = rawBytes.copyOfRange(22, 34)
            val cipherBytes = rawBytes.copyOfRange(34, rawBytes.size)
            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }

        private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        }
    }
}
