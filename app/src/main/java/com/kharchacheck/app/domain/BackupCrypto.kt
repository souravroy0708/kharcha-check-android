package com.kharchacheck.app.domain

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backup file (.kcbak). Format:
 *   "KCB2" | 4-byte iterations | 16-byte salt | 12-byte IV | AES-256-GCM ciphertext+tag
 * The password never leaves the phone and is never stored. Lose it and the backup can't be opened, by design.
 */
object BackupCrypto {
    private val MAGIC = "KCB2".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000

    class WrongPasswordException : Exception("Wrong password or damaged backup file")
    class NotABackupException : Exception("This isn't a Kharcha Check backup file")

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val ct = cipher.doFinal(plain)
        return MAGIC + intBytes(iterations) + salt + iv + ct
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < 4 + 4 + 16 + 12 + 16 || !blob.copyOfRange(0, 4).contentEquals(MAGIC)) throw NotABackupException()
        val iterations = bytesInt(blob, 4)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw NotABackupException()
        val salt = blob.copyOfRange(8, 24)
        val iv = blob.copyOfRange(24, 36)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return try { cipher.doFinal(blob, 36, blob.size - 36) } catch (e: AEADBadTagException) { throw WrongPasswordException() }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally { spec.clearPassword() }
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun bytesInt(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
