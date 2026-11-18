package dev.piko.shared.state

import dev.piko.data.repository.FileCategory
import dev.piko.data.repository.fileCategory
import dev.piko.shared.data.InstantFileItem

/**
 * Device-local default selection for resolved magnets. Size bounds are independent:
 * null means "no lower/upper bound". This controls what gets selected, not whether
 * PikPak's cloud offline download fetches the entire torrent before pruning.
 */
data class MagnetFileSelectionFilter(
    val enabled: Boolean = false,
    val minMb: Int? = 0,
    val maxMb: Int? = 500,
    val categories: Set<FileCategory> = setOf(FileCategory.VIDEO),
) {
    init {
        require(minMb == null || minMb in 0..MAX_MB)
        require(maxMb == null || maxMb in 0..MAX_MB)
        require(minMb == null || maxMb == null || minMb <= maxMb)
        require(categories.isNotEmpty())
    }

    /** The original 3-part encoding is still accepted when upgrading old installs. */
    fun encode(): String = listOf(
        enabled.toString(),
        minMb?.toString().orEmpty(),
        maxMb?.toString().orEmpty(),
        categories.sortedBy { it.ordinal }.joinToString(",") { it.name },
    ).joinToString(":")

    fun matches(file: InstantFileItem): Boolean {
        val bytes = file.file.size
        if (bytes <= 0) return false // Never auto-select an unknown or zero-sized file.
        if (file.file.name.fileCategory() !in categories) return false
        if (minMb != null && bytes < minMb.toLong() * 1_000_000L) return false
        if (maxMb != null && bytes > maxMb.toLong() * 1_000_000L) return false
        return true
    }

    companion object {
        const val MAX_MB = 1_000_000
        val DEFAULT = MagnetFileSelectionFilter()

        fun decode(value: String): MagnetFileSelectionFilter = runCatching {
            val parts = value.split(':')
            fun bound(index: Int, oldDefault: Int): Int? =
                when (val raw = parts.getOrNull(index)) {
                    null -> oldDefault
                    "" -> null
                    else -> raw.toInt()
                }
            val kinds = parts.getOrNull(3)?.split(',')?.map { FileCategory.valueOf(it) }?.toSet()
                ?: setOf(FileCategory.VIDEO)
            MagnetFileSelectionFilter(
                enabled = parts.getOrNull(0) == "true",
                minMb = bound(1, 0),
                maxMb = bound(2, 500),
                categories = kinds,
            )
        }.getOrDefault(DEFAULT)
    }
}

/** Keep the original helper name for existing callers, despite supporting every file category. */
fun matchingMagnetVideoIndices(
    files: List<InstantFileItem>,
    filter: MagnetFileSelectionFilter,
): Set<Int> = files.indices.filterTo(mutableSetOf()) { filter.matches(files[it]) }
