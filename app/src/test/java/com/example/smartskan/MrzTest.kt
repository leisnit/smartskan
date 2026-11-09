package com.example.smartskan

import com.example.smartskan.pdf.WordExport
import com.example.smartskan.util.Mrz
import com.example.smartskan.util.MrzData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MrzTest {
    // ICAO 9303 specimen documents
    @Test
    fun passportTd3() {
        val mrz = Mrz.parse(
            listOf(
                "UTOPIA PASSPORT",
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
            ),
        )
        assertNotNull(mrz)
        mrz!!
        assertEquals(MrzData.Format.TD3, mrz.format)
        assertTrue(mrz.verified)
        assertEquals("ERIKSSON", mrz.surname)
        assertEquals("ANNA MARIA", mrz.givenNames)
        assertEquals("L898902C3", mrz.documentNumber)
        assertEquals("UTO", mrz.nationality)
        assertEquals("Female", mrz.sex)
    }

    @Test
    fun passportWithOcrNoise() {
        // Spaces inside lines, a dropped trailing filler and an 'O' read in place of a zero.
        val mrz = Mrz.parse(
            listOf(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO74O8122F1204159ZE184226B <<<<<10",
            ),
        )
        assertNotNull(mrz)
        assertTrue(mrz!!.verified)
        assertEquals("ERIKSSON", mrz.surname)
    }

    @Test
    fun idCardTd1() {
        val mrz = Mrz.parse(
            listOf(
                "I<UTOD231458907<<<<<<<<<<<<<<<",
                "7408122F1204159UTO<<<<<<<<<<<6",
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<",
            ),
        )
        assertNotNull(mrz)
        mrz!!
        assertEquals(MrzData.Format.TD1, mrz.format)
        assertTrue(mrz.verified)
        assertEquals("D23145890", mrz.documentNumber)
        assertEquals("ANNA MARIA", mrz.givenNames)
    }

    @Test
    fun ordinaryTextIsNotAnMrz() {
        assertNull(Mrz.parse(listOf("THIS IS A PERFECTLY ORDINARY LINE OF TEXT 12345678", "ANOTHER ONE")))
    }

    @Test
    fun paragraphsRejoinWrappedLines() {
        val paragraphs = WordExport.toParagraphs("The quick brown\nfox jumps over the lazy-\ndog.\n\n1. First\n2. Second")
        assertEquals(listOf("The quick brown fox jumps over the lazydog.", "1. First", "2. Second"), paragraphs)
    }
}
