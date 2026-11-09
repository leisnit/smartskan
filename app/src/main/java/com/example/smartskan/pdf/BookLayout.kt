package com.example.smartskan.pdf

/**
 * How PDF pages sit in an open book, and how far a page has swung during a turn.
 *
 * Facing pages follow a printed book: the cover is alone on the right, then pairs, and an
 * even-length book ends with the back cover alone on the left.
 *
 * "Turn" is how far the book has moved past a leaf: 0 when the leaf is open, towards 1 as it is
 * turned away forwards, towards -1 while it is still waiting to be reached. Rotations are degrees
 * around the vertical axis, and null means the page isn't visible.
 */
object BookLayout {
    fun spreadCount(pageCount: Int): Int = pageCount / 2 + 1

    fun leftPage(spread: Int): Int? = if (spread == 0) null else 2 * spread - 1

    fun rightPage(spread: Int, pageCount: Int): Int? = (2 * spread).takeIf { it < pageCount }

    fun spreadOfPage(page: Int): Int = (page + 1) / 2

    /** First page shown on [spread], used to keep the reader's place when the layout changes. */
    fun firstPageOfSpread(spread: Int): Int = leftPage(spread) ?: 0

    /** One page on screen: it swings away around its left edge, uncovering the next one beneath. */
    fun singlePageRotation(turn: Float): Float? = when {
        turn >= 1f || turn <= -1f -> null
        turn > 0f -> -90f * turn
        else -> 0f
    }

    /** Left page of a spread: flat once open, and swings down onto the book in the second half of the turn. */
    fun leftPageRotation(turn: Float): Float? = when {
        turn >= 1f || turn <= -0.5f -> null
        turn < 0f -> -180f * turn
        else -> 0f
    }

    /** Right page of a spread: flat until turned, then swings up off the book in the first half of the turn. */
    fun rightPageRotation(turn: Float): Float? = when {
        turn >= 0.5f || turn <= -1f -> null
        turn > 0f -> -180f * turn
        else -> 0f
    }
}
