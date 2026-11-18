package dev.piko.shared.state

import dev.piko.data.repository.FileCategory
import dev.piko.data.repository.fileCategory
import dev.piko.shared.data.InstantFileItem

/**
 * Device-local default selection for resolved magnets. This is a selection preset,
 * NOT a promise that the cloud server will download only the chosen files.
 */
data class MagnetFileSelectionFilter(
    val enabled: Boolean = false,
    val minMb: Int = 0,
    val maxMb: Int = 500,
) {
    init {
        require(minMb in 0..102400 && maxMb in 0..102400 && minMb <= maxMb)
    }

    fun encode(): String = "$enabled:$minMb:$maxMb"

    fun matches(file: InstantFileItem): Boolean {
        val bytes = file.file.size
        if (bytes < 0) return false // Unknown size: never silently select it.
        return file.file.name.fileCategory() == FileCategory.VIDEO &&
            bytes >= minMb.toLong() * 1_000_000L &&
            bytes <= maxMb.toLong() * 1_000_000L
    }

    companion object {
        fun decode(value: String): MagnetFileSelectionFilter {
            val pieces = value.split(':')
            return runCatching {
                MagnetFileSelectionFilter(
                    enabled = pieces.getOrNull(0) == "true",
                    minMb = pieces.getOrNull(1)?.toInt() ?: 0,
                    maxMb = pieces.getOrNull(2)?.toInt() ?: 500,
                )
            }.getOrDefault(MagnetFileSelectionFilter())
        }
    }
}

fun matchingMagnetVideoIndices(
    files: List<InstantFileItem>,
    filter: MagnetFileSelectionFilter,
): Set<Int> = files.indices.filterTo(mutableSetOf()) { filter.matches(files[it]) }
