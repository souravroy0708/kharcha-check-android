package com.kharchacheck.app.domain

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming, password-protected backup encryption ("KCB3"), for backups that include large files.
 * Encrypts in 1 MiB chunks so a backup of any size never has to fit in memory.
 *
 *   "KCB3" | iterations (4) | salt (16) | nonce prefix (8) | chunk* 
 *   chunk  = ciphertext length (4) | AES-256-GCM(chunk) with IV = prefix ‖ counter,
 *            AAD = "KCB3" ‖ counter ‖ isLast
 *
 * The counter stops chunks being reordered or swapped; the isLast flag in the final chunk means a
 * truncated file is detected instead of being silently half-restored.
 */
object StreamCrypto {
    val MAGIC = "KCB3".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    const val CHUNK = 1 shl 20

    class TruncatedException : Exception("This backup file is incomplete or damaged")

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }

    private fun iv(prefix: ByteArray, counter: Int) = prefix + byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte())
    private fun aad(counter: Int, last: Boolean) = MAGIC + iv(ByteArray(0), counter) + byteArrayOf(if (last) 1 else 0)
    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    /** Wrap [out]; everything written is encrypted. Call close() to write the final chunk. */
    fun encrypting(out: OutputStream, password: CharArray, iterations: Int = ITERATIONS): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val prefix = ByteArray(8).also(rnd::nextBytes)
        val k = key(password, salt, iterations)
        out.write(MAGIC); out.write(int(iterations)); out.write(salt); out.write(prefix)
        return object : OutputStream() {
            private val buf = ByteArrayOutputStream(CHUNK)
            private var counter = 0
            private var closed = false
            private fun emit(last: Boolean) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv(prefix, counter)))
                c.updateAAD(aad(counter, last))
                val ct = c.doFinal(buf.toByteArray())
                out.write(int(ct.size)); out.write(ct)
                buf.reset(); counter++
            }
            override fun write(b: Int) { buf.write(b); if (buf.size() >= CHUNK) emit(false) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off; var n = len
                while (n > 0) {
                    val take = minOf(n, CHUNK - buf.size())
                    buf.write(b, o, take); o += take; n -= take
                    if (buf.size() >= CHUNK) emit(false)
                }
            }
            override fun flush() = out.flush()
            override fun close() { if (closed) return; closed = true; emit(true); out.flush(); out.close() }
        }
    }

    /** True if [head] (the first bytes of a file) is a KCB3 backup. */
    fun isKcb3(head: ByteArray) = head.size >= 4 && head.copyOfRange(0, 4).contentEquals(MAGIC)

    /** Wrap [input] (positioned at the start of a KCB3 file); reading yields the decrypted bytes. */
    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val din = DataInputStream(input)
        val magic = ByteArray(4); din.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw BackupCrypto.NotABackupException()
        val iterations = din.readInt()
        if (iterations !in 100_000..2_000_000) throw BackupCrypto.NotABackupException()
        val salt = ByteArray(16); din.readFully(salt)
        val prefix = ByteArray(8); din.readFully(prefix)
        val k = key(password, salt, iterations)
        return object : InputStream() {
            private var plain = ByteArray(0); private var pos = 0
            private var counter = 0; private var done = false
            private fun next(): Boolean {
                if (done) return false
                val len = try { din.readInt() } catch (e: EOFException) { throw TruncatedException() }
                if (len < 16 || len > CHUNK + 16) throw TruncatedException()
                val ct = ByteArray(len)
                try { din.readFully(ct) } catch (e: EOFException) { throw TruncatedException() }
                // Try as a middle chunk, then as the last one: the flag is authenticated, so only the true one verifies.
                for (last in listOf(false, true)) {
                    try {
                        val c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv(prefix, counter)))
                        c.updateAAD(aad(counter, last))
                        plain = c.doFinal(ct); pos = 0; counter++; done = last
                        return true
                    } catch (e: AEADBadTagException) { /* try the other flag */ }
                }
                throw if (counter == 0) BackupCrypto.WrongPasswordException() else TruncatedException()
            }
            override fun read(): Int { while (pos >= plain.size) { if (!next()) return -1 }; return plain[pos++].toInt() and 0xFF }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= plain.size) { if (!next()) return -1 }
                val n = minOf(len, plain.size - pos)
                System.arraycopy(plain, pos, b, off, n); pos += n
                return n
            }
        }
    }
}
