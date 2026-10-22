package com.yuyan.imemodule.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGeometryTest {
    @Test
    fun duoLandscapeAndPortraitFitCandidatesWithNoClipping() {
        // Lower display: 1280 x 960 landscape, 960 x 1280 when rotated.
        for (height in listOf(960, 1280)) {
            for (scale in listOf(0.55f, 1f)) {
                val size = KeyboardGeometry.calculate(height, 1f, scale, 0, true)
                assertFitsAndFills(size, height)
                assertTrue("The candidate row must remain visible", size.candidates > size.composing)
                assertTrue("The keyboard must keep most of the screen", size.keys > height / 2)
            }
        }
    }

    @Test
    fun knownDuoLandscapeAllocationIncludesCandidateRows() {
        val size = KeyboardGeometry.calculate(960, 1f, 0.55f, 0, true)
        assertEquals(827, size.keys)
        assertEquals(45, size.composing)
        assertEquals(85, size.candidates)
        assertEquals(957, size.total)
    }

    @Test
    fun largerCandidateTextTradesKeyHeightForReadableCandidates() {
        for (height in listOf(960, 1280)) {
            val normal = KeyboardGeometry.calculate(height, 1f, 0.55f, 0, true)
            val large = KeyboardGeometry.calculate(height, 1f, 1f, 0, true)
            assertTrue(large.keys < normal.keys)
            assertTrue(large.candidateArea > normal.candidateArea)
            assertFitsAndFills(large, height)
        }
    }

    @Test
    fun navigationAndUserBottomPaddingAreReservedBeforeAllocation() {
        val navigation = 48
        val padding = 120
        for (height in listOf(960, 1280)) {
            for (scale in listOf(0.55f, 1f)) {
                val unreserved = KeyboardGeometry.calculate(height, 1f, scale, 0, true)
                val size = KeyboardGeometry.calculate(height, 1f, scale, navigation + padding, true)
                assertFitsAndFills(size, height - navigation - padding)
                assertTrue(size.keys < unreserved.keys)
                assertTrue(size.total + navigation + padding <= height)
            }
        }
    }

    @Test
    fun normalImeLeavesAtLeastAQuarterOfTheScreenForTheEditor() {
        for (height in listOf(960, 1280)) {
            for (scale in listOf(0.55f, 1f)) {
                val reserved = 48
                val size = KeyboardGeometry.calculate(height, 1f, scale, reserved, false)
                assertFitsAndFills(size, height * 3 / 4 - reserved)
                assertTrue(size.total + reserved <= height * 3 / 4)
            }
        }
    }

    @Test
    fun chosenSmallerKeyboardHeightIsRespected() {
        val size = KeyboardGeometry.calculate(960, 0.3f, 0.55f, 0, true)
        assertEquals(288, size.keys)
        assertTrue(size.total < 960 / 2)
    }

    @Test
    fun nonFiniteSettingsUseSafeDefaults() {
        val fallback = KeyboardGeometry.calculate(960, 0.5f, 0.55f, 48, true)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(fallback, KeyboardGeometry.calculate(960, invalid, 0.55f, 48, true))
            assertEquals(fallback, KeyboardGeometry.calculate(960, 0.5f, invalid, 48, true))
        }
    }

    @Test
    fun finiteSettingsAndNegativeInsetsAreClamped() {
        assertEquals(
            KeyboardGeometry.calculate(960, 0.1f, 0.1f, 0, true),
            KeyboardGeometry.calculate(960, -10f, -10f, -48, true),
        )
        assertEquals(
            KeyboardGeometry.calculate(960, 1f, 3f, 0, true),
            KeyboardGeometry.calculate(960, 100f, 100f, 0, true),
        )
    }

    @Test
    fun unavailableAndTinyDisplaysKeepNonNegativeMeasurableDimensions() {
        for (height in listOf(-100, 0, 1, 2, 3)) {
            for (fullscreen in listOf(false, true)) {
                val size = KeyboardGeometry.calculate(height, 1f, 3f, 0, fullscreen)
                assertTrue(size.keys >= 1)
                assertTrue(size.composing >= 0)
                assertTrue(size.candidates >= 0)
                assertTrue(size.total <= height.coerceAtLeast(1))
            }
        }
        val fullyReserved = KeyboardGeometry.calculate(960, 1f, 1f, 2000, true)
        assertEquals(1, fullyReserved.keys)
        assertEquals(1, fullyReserved.total)
    }

    private fun assertFitsAndFills(size: KeyboardGeometry.Size, available: Int) {
        assertEquals(size.composing + size.candidates, size.candidateArea)
        assertEquals(size.keys + size.candidateArea, size.total)
        assertTrue("Keyboard ${size.total}px exceeds ${available}px", size.total <= available)
        assertTrue("Unexpected blank space: ${available - size.total}px", available - size.total <= 4)
    }
}
