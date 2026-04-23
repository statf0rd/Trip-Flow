package com.triloo.data.relay

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * AES-256-GCM шифрование для Bluetooth relay-пакетов.
 *
 * Ключ шифрования выводится из invite-кода поездки через SHA-256,
 * поэтому обе стороны могут шифровать/дешифровать без отдельного обмена ключами.
 */
object RelayEncryption {
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Выводит AES-256 ключ из строки (invite-кода поездки).
     */
    fun deriveKey(sharedSecret: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Шифрует payload. Возвращает IV (12 байт) + ciphertext.
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Дешифрует данные, полученные от [encrypt].
     */
    fun decrypt(data: ByteArray, key: SecretKey): ByteArray {
        require(data.size > GCM_IV_LENGTH) { "Данные слишком короткие для расшифровки" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
