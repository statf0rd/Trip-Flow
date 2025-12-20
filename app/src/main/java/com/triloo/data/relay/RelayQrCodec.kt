package com.triloo.data.relay

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class RelayPayloadType {
    INVITE,
    RELAY
}

data class RelayQrChunk(
    val type: RelayPayloadType,
    val version: Int,
    val packageId: String,
    val index: Int,
    val total: Int,
    val data: String
)

object RelayQrCodec {
    private const val PREFIX = "TRILOO"
    private const val VERSION = 1
    private const val HEADER_SEPARATOR = "|"
    private const val INDEX_SEPARATOR = "/"

    fun encode(
        type: RelayPayloadType,
        jsonPayload: String,
        chunkSize: Int = 800
    ): List<String> {
        val encoded = Base64.encodeToString(
            jsonPayload.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        val packageId = UUID.randomUUID().toString()
        val chunks = encoded.chunked(chunkSize)
        val total = chunks.size.coerceAtLeast(1)

        return chunks.mapIndexed { index, chunk ->
            buildString {
                append(PREFIX)
                append(HEADER_SEPARATOR)
                append(type.name)
                append(HEADER_SEPARATOR)
                append("v")
                append(VERSION)
                append(HEADER_SEPARATOR)
                append(packageId)
                append(HEADER_SEPARATOR)
                append(index + 1)
                append(INDEX_SEPARATOR)
                append(total)
                append(HEADER_SEPARATOR)
                append(chunk)
            }
        }
    }

    fun parse(text: String): RelayQrChunk? {
        val parts = text.split(HEADER_SEPARATOR, limit = 6)
        if (parts.size < 6) return null
        if (parts[0] != PREFIX) return null

        val type = runCatching { RelayPayloadType.valueOf(parts[1]) }.getOrNull() ?: return null
        val version = parts[2].removePrefix("v").toIntOrNull() ?: return null
        val packageId = parts[3]
        val indexParts = parts[4].split(INDEX_SEPARATOR)
        if (indexParts.size != 2) return null
        val index = indexParts[0].toIntOrNull() ?: return null
        val total = indexParts[1].toIntOrNull() ?: return null
        val data = parts[5]

        return RelayQrChunk(
            type = type,
            version = version,
            packageId = packageId,
            index = index,
            total = total,
            data = data
        )
    }

    fun assemble(
        chunks: Collection<RelayQrChunk>
    ): String? {
        if (chunks.isEmpty()) return null
        val total = chunks.first().total
        if (chunks.size != total) return null

        val sorted = chunks.sortedBy { it.index }
        if (sorted.first().index != 1 || sorted.last().index != total) return null
        val encoded = sorted.joinToString(separator = "") { it.data }
        val decodedBytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
        return String(decodedBytes, StandardCharsets.UTF_8)
    }
}
