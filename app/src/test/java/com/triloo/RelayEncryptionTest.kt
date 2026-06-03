package com.triloo

import com.triloo.data.relay.RelayEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

/**
 * Проверка криптографических свойств канала Triloo Relay (§4.5 ВКР):
 * корректность round-trip, отказ при неверном секрете и при искажении шифртекста,
 * случайность вектора инициализации.
 */
class RelayEncryptionTest {

    @Test
    fun roundTripDecryptsToOriginal() {
        val key = RelayEncryption.deriveKey("trip-secret-123")
        val plain = "RelayPackage — поездка в Сеул".toByteArray(Charsets.UTF_8)
        val dec = RelayEncryption.decrypt(RelayEncryption.encrypt(plain, key), key)
        assertArrayEquals(plain, dec)
    }

    @Test
    fun wrongSecretFailsAuthentication() {
        val enc = RelayEncryption.encrypt("secret".toByteArray(), RelayEncryption.deriveKey("trip-A"))
        try {
            RelayEncryption.decrypt(enc, RelayEncryption.deriveKey("trip-B"))
            fail("ожидался отказ проверки тега GCM при неверном секрете")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e is BadPaddingException)
        }
    }

    @Test
    fun tamperedByteFailsTagCheck() {
        val key = RelayEncryption.deriveKey("trip-X")
        val enc = RelayEncryption.encrypt("payload".toByteArray(), key).copyOf()
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0x01).toByte()
        try {
            RelayEncryption.decrypt(enc, key)
            fail("ожидался отказ: искажён один байт шифртекста")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e is BadPaddingException)
        }
    }

    @Test
    fun ivIsRandomizedPerEncryption() {
        val key = RelayEncryption.deriveKey("trip-Y")
        val plain = "same".toByteArray()
        val e1 = RelayEncryption.encrypt(plain, key)
        val e2 = RelayEncryption.encrypt(plain, key)
        assertFalse(e1.copyOfRange(0, 12).contentEquals(e2.copyOfRange(0, 12)))
        assertFalse(e1.contentEquals(e2))
    }
}
