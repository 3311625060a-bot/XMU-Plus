package cn.edu.xmu.plus.auth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * XMU CAS (#pwdFromId) password encryption helper matching ids.xmu.edu.cn's encrypt.js:
 * AES-128-CBC + PKCS7/PKCS5 padding, Base64 output, 64-char random prefix + raw password.
 */
object CasCrypto {

    const val AES_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    fun randomString(length: Int, random: SecureRandom = SecureRandom()): String {
        val sb = StringBuilder(length)
        val poolSize = AES_CHARS.length
        for (i in 0 until length) {
            sb.append(AES_CHARS[random.nextInt(poolSize)])
        }
        return sb.toString()
    }

    fun encryptPassword(
        rawPassword: String,
        salt: String,
        random: SecureRandom = SecureRandom(),
    ): String {
        val trimmedSalt = salt.trim()
        require(trimmedSalt.length == 16) { "CAS pwdEncryptSalt must be 16 bytes" }
        val keySpec = SecretKeySpec(trimmedSalt.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(randomString(16, random).toByteArray(Charsets.UTF_8))
        val plaintext = (randomString(64, random) + rawPassword).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(encrypted)
    }
}
