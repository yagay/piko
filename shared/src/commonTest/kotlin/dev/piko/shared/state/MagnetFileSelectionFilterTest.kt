package dev.piko.shared.state

import dev.piko.data.repository.FileCategory
import dev.piko.shared.data.InstantFileItem
import io.github.nihildigit.pikpak.ResolvedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MagnetFileSelectionFilterTest {
    private fun file(name: String, size: Long) =
        InstantFileItem(ResolvedFile(name, size, null), isInstantReady = false)

    @Test
    fun defaultIsVideoFromZeroToInclusive500Mb() {
        val files = listOf(
            file("part.mp4", 500_000_000L),
            file("next.mkv", 499_999_999L),
            file("larger.mp4", 500_000_001L),
            file("cover.jpg", 1_000_000L),
            file("notes.txt", 200L),
            file("empty.mkv", 0L),
        )
        assertEquals(setOf(0, 1), matchingMagnetVideoIndices(files, MagnetFileSelectionFilter(enabled = true)))
    }

    @Test
    fun independentLowerOrUpperBoundAndMultipleCategories() {
        val files = listOf(
            file("movie.mp4", 250_000_000L),
            file("movie2.mp4", 600_000_000L),
            file("cover.jpg", 200_000_000L),
            file("movie.ass", 1_000_000L),
            file("movie.mp3", 700_000_000L),
        )
        val both = MagnetFileSelectionFilter(true, 150, 500, setOf(FileCategory.VIDEO, FileCategory.IMAGE))
        assertEquals(setOf(0, 2), matchingMagnetVideoIndices(files, both))
        val noMax = both.copy(maxMb = null)
        assertEquals(setOf(0, 1, 2), matchingMagnetVideoIndices(files, noMax))
        val noMin = both.copy(minMb = null)
        assertEquals(setOf(0, 2), matchingMagnetVideoIndices(files, noMin))
        val noLimits = MagnetFileSelectionFilter(true, null, null, setOf(FileCategory.SUBTITLE))
        assertEquals(setOf(3), matchingMagnetVideoIndices(files, noLimits))
    }

    @Test
    fun codecRoundTripsLegacyEncodingAndInvalidValues() {
        val settings = MagnetFileSelectionFilter(true, null, 800, setOf(FileCategory.AUDIO, FileCategory.SUBTITLE))
        assertEquals(settings, MagnetFileSelectionFilter.decode(settings.encode()))
        assertEquals(MagnetFileSelectionFilter(true, 0, 500), MagnetFileSelectionFilter.decode("true:0:500"))
        assertEquals(MagnetFileSelectionFilter(), MagnetFileSelectionFilter.decode("true:500:0"))
        assertEquals(MagnetFileSelectionFilter(), MagnetFileSelectionFilter.decode("true:::-"))
        assertFalse(MagnetFileSelectionFilter.decode("false:0:500").enabled)
        assertTrue(MagnetFileSelectionFilter.decode("true:0:500").enabled)
    }
}
