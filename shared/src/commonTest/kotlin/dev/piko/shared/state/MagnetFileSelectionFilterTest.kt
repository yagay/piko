package dev.piko.shared.state

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
    fun onlyVideosWithinInclusive500MbThresholdAreSelected() {
        val files = listOf(
            file("part.mp4", 500_000_000L),
            file("next.mkv", 499_999_999L),
            file("larger.mp4", 500_000_001L),
            file("cover.jpg", 1_000_000L),
            file("notes.txt", 200L),
        )
        assertEquals(setOf(0, 1), matchingMagnetVideoIndices(files, MagnetFileSelectionFilter(true, 0, 500)))
    }

    @Test
    fun savedSettingsRoundTripAndInvalidValuesReset() {
        val settings = MagnetFileSelectionFilter(true, 0, 500)
        assertEquals(settings, MagnetFileSelectionFilter.decode(settings.encode()))
        assertFalse(MagnetFileSelectionFilter.decode("true:500:0").enabled)
        assertTrue(MagnetFileSelectionFilter.decode("true:0:500").enabled)
    }
}
