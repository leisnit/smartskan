package com.example.smartskan

import com.example.smartskan.pdf.BookLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookLayoutTest {
    private fun spreads(pageCount: Int) = (0 until BookLayout.spreadCount(pageCount)).map { s ->
        BookLayout.leftPage(s) to BookLayout.rightPage(s, pageCount)
    }

    @Test
    fun coverIsAloneOnTheRight() {
        assertEquals(listOf(null to 0), spreads(1))
    }

    @Test
    fun evenBookEndsWithBackCoverAloneOnTheLeft() {
        assertEquals(listOf(null to 0, 1 to 2, 3 to null), spreads(4))
    }

    @Test
    fun oddBookEndsWithAFullSpread() {
        assertEquals(listOf(null to 0, 1 to 2, 3 to 4), spreads(5))
    }

    @Test
    fun everyPageAppearsOnTheSpreadThatClaimsIt() {
        for (pageCount in 1..12) {
            for (page in 0 until pageCount) {
                val spread = BookLayout.spreadOfPage(page)
                val shown = listOf(BookLayout.leftPage(spread), BookLayout.rightPage(spread, pageCount))
                assert(page in shown) { "page $page of $pageCount isn't on spread $spread" }
            }
        }
    }

    @Test
    fun placeIsKeptWhenSwitchingBetweenLayouts() {
        assertEquals(0, BookLayout.firstPageOfSpread(0))
        assertEquals(1, BookLayout.firstPageOfSpread(1))
        assertEquals(3, BookLayout.firstPageOfSpread(2))
        for (spread in 0..6) {
            assertEquals(spread, BookLayout.spreadOfPage(BookLayout.firstPageOfSpread(spread)))
        }
    }

    @Test
    fun singlePageSwingsAwayWhileNextWaitsFlat() {
        assertEquals(0f, BookLayout.singlePageRotation(0f)!!, 0f)
        assertEquals(-45f, BookLayout.singlePageRotation(0.5f)!!, 0f)
        assertEquals(0f, BookLayout.singlePageRotation(-0.5f)!!, 0f)
        assertNull(BookLayout.singlePageRotation(1f))
        assertNull(BookLayout.singlePageRotation(-1f))
    }

    /** At a turn of t the old spread has turn t and the new spread has turn t - 1. */
    @Test
    fun spreadTurnSweepsContinuouslyAcrossTheSpine() {
        // First half: the old right page lifts, the new left page is still hidden.
        assertEquals(-45f, BookLayout.rightPageRotation(0.25f)!!, 0f)
        assertNull(BookLayout.leftPageRotation(0.25f - 1f))
        // Second half: the old right page is gone, the new left page settles.
        assertNull(BookLayout.rightPageRotation(0.75f))
        assertEquals(45f, BookLayout.leftPageRotation(0.75f - 1f)!!, 0f)
        // Pages stay flat on the side of the book they aren't leaving.
        assertEquals(0f, BookLayout.leftPageRotation(0.25f)!!, 0f)
        assertEquals(0f, BookLayout.rightPageRotation(0.25f - 1f)!!, 0f)
    }

    @Test
    fun fullyOpenSpreadIsFlat() {
        assertEquals(0f, BookLayout.leftPageRotation(0f)!!, 0f)
        assertEquals(0f, BookLayout.rightPageRotation(0f)!!, 0f)
    }
}
