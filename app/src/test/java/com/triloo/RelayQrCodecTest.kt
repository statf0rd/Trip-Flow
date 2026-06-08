package com.triloo

import com.triloo.data.relay.RelayPayloadType
import com.triloo.data.relay.RelayQrChunk
import com.triloo.data.relay.RelayQrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayQrCodecTest {

    @Test
    fun encodeSmallPayloadProducesSingleChunk() {
        val payload = """{"trip":"Rome"}"""
        val chunks = RelayQrCodec.encode(RelayPayloadType.RELAY, payload)

        assertEquals(1, chunks.size)
        val parsed = RelayQrCodec.parse(chunks.first())!!
        assertEquals(RelayPayloadType.RELAY, parsed.type)
        assertEquals(1, parsed.version)
        assertEquals(1, parsed.index)
        assertEquals(1, parsed.total)
    }

    @Test
    fun encodeParseAssembleRoundTripSingleChunk() {
        val payload = """{"trip":"Lisbon","places":[1,2,3]}"""
        val chunks = RelayQrCodec.encode(RelayPayloadType.INVITE, payload)
        val parsed = chunks.mapNotNull { RelayQrCodec.parse(it) }

        assertEquals(chunks.size, parsed.size)
        assertEquals(payload, RelayQrCodec.assemble(parsed))
    }

    @Test
    fun encodeLargePayloadChunksAndReassembles() {
        val payload = "Triloo relay payload — повтор. ".repeat(40)
        val chunks = RelayQrCodec.encode(RelayPayloadType.RELAY, payload, chunkSize = 16)
        val parsed = chunks.mapNotNull { RelayQrCodec.parse(it) }

        assertTrue("ожидаем несколько чанков", chunks.size > 1)
        // У всех чанков одинаковый packageId и корректный total.
        assertEquals(1, parsed.map { it.packageId }.distinct().size)
        assertTrue(parsed.all { it.total == chunks.size })
        assertEquals(listOf(1, chunks.size), listOf(parsed.first().index, parsed.last().index))
        assertEquals(payload, RelayQrCodec.assemble(parsed))
    }

    @Test
    fun assembleIgnoresChunkOrder() {
        val payload = "Порядок чанков не должен влиять на сборку payload."
        val chunks = RelayQrCodec.encode(RelayPayloadType.RELAY, payload, chunkSize = 8)
        val parsedShuffled = chunks.mapNotNull { RelayQrCodec.parse(it) }.reversed()

        assertEquals(payload, RelayQrCodec.assemble(parsedShuffled))
    }

    @Test
    fun parseRejectsMalformedInput() {
        // Чужой/отсутствующий префикс и слишком мало частей.
        assertNull(RelayQrCodec.parse("GARBAGE"))
        assertNull(RelayQrCodec.parse("TRILOO|RELAY|v1|id|1"))
        assertNull(RelayQrCodec.parse("XXX|RELAY|v1|id|1/1|data"))
        // Неизвестный тип payload.
        assertNull(RelayQrCodec.parse("TRILOO|FOO|v1|id|1/1|data"))
        // Битая версия и битый индекс.
        assertNull(RelayQrCodec.parse("TRILOO|RELAY|vX|id|1/1|data"))
        assertNull(RelayQrCodec.parse("TRILOO|RELAY|v1|id|x/1|data"))
        // Индекс без разделителя «/».
        assertNull(RelayQrCodec.parse("TRILOO|RELAY|v1|id|1|data"))
    }

    @Test
    fun assembleReturnsNullOnIncompleteOrInconsistentChunks() {
        // Пустой вход.
        assertNull(RelayQrCodec.assemble(emptyList()))

        // Количество чанков не совпадает с total.
        val incomplete = listOf(
            RelayQrChunk(RelayPayloadType.RELAY, 1, "pkg", index = 1, total = 2, data = "QQ")
        )
        assertNull(RelayQrCodec.assemble(incomplete))

        // Количество совпадает, но набор не начинается с индекса 1.
        val wrongStart = listOf(
            RelayQrChunk(RelayPayloadType.RELAY, 1, "pkg", index = 2, total = 2, data = "QQ"),
            RelayQrChunk(RelayPayloadType.RELAY, 1, "pkg", index = 3, total = 2, data = "QQ")
        )
        assertNull(RelayQrCodec.assemble(wrongStart))
    }
}
