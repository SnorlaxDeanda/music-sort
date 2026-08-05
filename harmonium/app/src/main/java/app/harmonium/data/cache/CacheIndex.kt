package app.harmonium.data.cache

/**
 * Pure in-memory index for offline cache entries.
 * Serialization is a stable line format so unit tests need no Android deps.
 */
class CacheIndex(
    private val entries: MutableMap<String, CacheEntry> = linkedMapOf(),
) {
    fun all(): List<CacheEntry> = entries.values.sortedByDescending { it.lastAccessAt }

    fun get(trackId: String): CacheEntry? = entries[trackId]

    fun put(entry: CacheEntry) {
        entries[entry.trackId] = entry
    }

    fun remove(trackId: String): CacheEntry? = entries.remove(trackId)

    fun clearKind(kind: CacheKind): List<CacheEntry> {
        val removed = entries.values.filter { it.kind == kind || (kind == CacheKind.ROLLING && it.kind == CacheKind.PLAYBACK && !it.pinned) }
        removed.forEach { entries.remove(it.trackId) }
        return removed
    }

    fun clearEvictable(): List<CacheEntry> {
        val removed = entries.values.filter { !it.pinned && it.kind != CacheKind.PERMANENT }
        removed.forEach { entries.remove(it.trackId) }
        return removed
    }

    fun clearAll(): List<CacheEntry> {
        val removed = entries.values.toList()
        entries.clear()
        return removed
    }

    fun touch(trackId: String, now: Long = System.currentTimeMillis()): CacheEntry? {
        val current = entries[trackId] ?: return null
        val updated = current.copy(lastAccessAt = now)
        entries[trackId] = updated
        return updated
    }

    fun promoteToPermanent(trackId: String, now: Long = System.currentTimeMillis()): CacheEntry? {
        val current = entries[trackId] ?: return null
        val updated = current.copy(
            kind = CacheKind.PERMANENT,
            pinned = true,
            lastAccessAt = now,
        )
        entries[trackId] = updated
        return updated
    }

    fun demoteToRolling(trackId: String, now: Long = System.currentTimeMillis()): CacheEntry? {
        val current = entries[trackId] ?: return null
        val updated = current.copy(
            kind = CacheKind.ROLLING,
            pinned = false,
            lastAccessAt = now,
        )
        entries[trackId] = updated
        return updated
    }

    fun stats(): CacheStats {
        var permanentBytes = 0L
        var rollingBytes = 0L
        var playbackBytes = 0L
        var permanentCount = 0
        var rollingCount = 0
        var playbackCount = 0
        entries.values.forEach { entry ->
            when (entry.kind) {
                CacheKind.PERMANENT -> {
                    permanentBytes += entry.bytes
                    permanentCount++
                }
                CacheKind.ROLLING -> {
                    rollingBytes += entry.bytes
                    rollingCount++
                }
                CacheKind.PLAYBACK -> {
                    playbackBytes += entry.bytes
                    playbackCount++
                }
            }
        }
        return CacheStats(
            permanentBytes = permanentBytes,
            rollingBytes = rollingBytes,
            playbackBytes = playbackBytes,
            permanentCount = permanentCount,
            rollingCount = rollingCount,
            playbackCount = playbackCount,
        )
    }

    /**
     * Evict oldest non-pinned entries until [evictableBytes] <= [maxBytes].
     * Permanent/pinned entries are never removed.
     * @return entries that should be deleted from disk
     */
    fun enforceRollingLimit(maxBytes: Long, now: Long = System.currentTimeMillis()): List<CacheEntry> {
        if (maxBytes <= 0L) return clearEvictable()

        fun evictableBytes(): Long =
            entries.values
                .filter { !it.pinned && it.kind != CacheKind.PERMANENT }
                .sumOf { it.bytes }

        if (evictableBytes() <= maxBytes) return emptyList()

        val candidates = entries.values
            .filter { !it.pinned && it.kind != CacheKind.PERMANENT }
            .sortedBy { it.lastAccessAt }

        val removed = mutableListOf<CacheEntry>()
        for (entry in candidates) {
            if (evictableBytes() <= maxBytes) break
            entries.remove(entry.trackId)
            removed += entry.copy(lastAccessAt = now)
        }
        return removed
    }

    fun serialize(): String = buildString {
        entries.values.forEach { entry ->
            append(escape(entry.trackId)).append('|')
            append(escape(entry.title)).append('|')
            append(escape(entry.artistName)).append('|')
            append(escape(entry.sourceUrl)).append('|')
            append(escape(entry.fileName)).append('|')
            append(entry.bytes).append('|')
            append(entry.kind.name).append('|')
            append(entry.createdAt).append('|')
            append(entry.lastAccessAt).append('|')
            append(if (entry.pinned) "1" else "0")
            append('\n')
        }
    }

    companion object {
        fun deserialize(text: String): CacheIndex {
            val index = CacheIndex()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val parts = splitEscaped(line)
                    if (parts.size < 10) return@forEach
                    val kind = runCatching { CacheKind.valueOf(parts[6]) }.getOrElse { CacheKind.ROLLING }
                    index.put(
                        CacheEntry(
                            trackId = unescape(parts[0]),
                            title = unescape(parts[1]),
                            artistName = unescape(parts[2]),
                            sourceUrl = unescape(parts[3]),
                            fileName = unescape(parts[4]),
                            bytes = parts[5].toLongOrNull() ?: 0L,
                            kind = kind,
                            createdAt = parts[7].toLongOrNull() ?: 0L,
                            lastAccessAt = parts[8].toLongOrNull() ?: 0L,
                            pinned = parts[9] == "1" || kind == CacheKind.PERMANENT,
                        ),
                    )
                }
            return index
        }

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")

        private fun unescape(value: String): String =
            buildString {
                var i = 0
                while (i < value.length) {
                    val c = value[i]
                    if (c == '\\' && i + 1 < value.length) {
                        when (value[i + 1]) {
                            '\\' -> append('\\')
                            '|' -> append('|')
                            'n' -> append('\n')
                            else -> append(value[i + 1])
                        }
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }

        private fun splitEscaped(line: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '\\' && i + 1 < line.length) {
                    current.append('\\').append(line[i + 1])
                    i += 2
                } else if (c == '|') {
                    parts += current.toString()
                    current.clear()
                    i++
                } else {
                    current.append(c)
                    i++
                }
            }
            parts += current.toString()
            return parts
        }
    }
}
