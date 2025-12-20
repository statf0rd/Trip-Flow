package com.triloo.data.relay

class RelayQrCollector {
    private val chunks = mutableMapOf<Int, RelayQrChunk>()
    private var currentPackageId: String? = null
    private var currentType: RelayPayloadType? = null
    private var totalChunks: Int = 0

    fun addChunk(chunk: RelayQrChunk): Boolean {
        if (currentPackageId == null) {
            currentPackageId = chunk.packageId
            currentType = chunk.type
            totalChunks = chunk.total
        }

        if (chunk.packageId != currentPackageId || chunk.type != currentType) {
            return false
        }

        if (chunk.index < 1 || chunk.index > chunk.total) return false
        chunks[chunk.index] = chunk
        return true
    }

    fun progress(): Pair<Int, Int> = chunks.size to totalChunks

    fun isComplete(): Boolean = totalChunks > 0 && chunks.size == totalChunks

    fun payloadType(): RelayPayloadType? = currentType

    fun assemblePayload(): String? {
        return RelayQrCodec.assemble(chunks.values)
    }

    fun reset() {
        chunks.clear()
        currentPackageId = null
        currentType = null
        totalChunks = 0
    }
}
