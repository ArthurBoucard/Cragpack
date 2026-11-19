package app.cragpack.domain.grade

import app.cragpack.domain.grade.GradeScale.Font
import app.cragpack.domain.grade.GradeScale.French
import app.cragpack.domain.grade.GradeScale.V
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradeTest {
    @Test
    fun bandIsFirstDigitForFontAndFrench() {
        (Grade.all(Font) + Grade.all(French)).forEach {
            assertEquals(it.value.first().digitToInt(), it.band, "band of ${it.value}")
        }
    }

    @Test
    fun vTakesItsFontEquivalentsBand() {
        Grade.all(V).filter { it.value != "VB" }.forEach {
            assertEquals(Grade(Font, it.display(GradeDisplay.Font)).band, it.band, "band of ${it.value}")
        }
        assertEquals(3, Grade(V, "VB").band) // design rule: VB counts as band 3
    }

    @Test
    fun displayConvertsBouldersOnly() {
        assertEquals("7A", Grade(V, "V6").display(GradeDisplay.Font))
        assertEquals("V7", Grade(Font, "7A+").display(GradeDisplay.V))
        assertEquals("7A+", Grade(Font, "7A+").display(GradeDisplay.AsEntered))
        assertEquals("6c+", Grade(French, "6c+").display(GradeDisplay.V))
    }

    @Test
    fun fontAndVSortTogether() {
        assertEquals(Grade(Font, "7A").sortIndex, Grade(V, "V6").sortIndex)
        assertTrue(Grade(Font, "7A+").sortIndex > Grade(V, "V6").sortIndex)
        assertTrue(Grade.all(French).zipWithNext().all { (a, b) -> a.sortIndex < b.sortIndex })
    }

    @Test
    fun lowGradesHaveMinusAndPlus() {
        assertEquals(
            listOf("1", "1+", "2-", "2", "2+", "3-", "3", "3+", "4-", "4", "4+", "5-", "5", "5+", "6A"),
            Grade.all(Font).take(15).map { it.value },
        )
        assertEquals(listOf("VB", "V0-", "V0", "V0+", "V1"), Grade.all(V).take(5).map { it.value })
        assertEquals("V0-", Grade(Font, "4-").display(GradeDisplay.V))
        assertTrue(Grade.all(Font).zipWithNext().all { (a, b) -> a.sortIndex < b.sortIndex })
        assertTrue(Grade.all(V).zipWithNext().all { (a, b) -> a.sortIndex < b.sortIndex })
    }

    @Test
    fun rejectsUnknownGrades() {
        assertNull(Grade.of(Font, "7D"))
        assertNull(Grade.of(Font, "7a")) // French spelling isn't a Font grade
        assertFailsWith<IllegalArgumentException> { Grade(V, "V18") }
    }
}
