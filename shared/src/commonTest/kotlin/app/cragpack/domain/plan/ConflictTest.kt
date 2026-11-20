package app.cragpack.domain.plan

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictTest {
    @Test
    fun twoMovesToDifferentDaysConflict() {
        assertTrue(isPlanConflict(base = "sat", mine = "sun", theirs = "mon"))
        assertTrue(isPlanConflict(base = null, mine = "sun", theirs = "mon")) // both planned it, on different days
        assertTrue(isPlanConflict(base = "sat", mine = null, theirs = "mon")) // I unplanned it, they moved it
    }

    @Test
    fun anythingElseIsLastWriteWins() {
        assertFalse(isPlanConflict(base = "sat", mine = "sun", theirs = "sat")) // they only reordered it within Saturday
        assertFalse(isPlanConflict(base = "sat", mine = "sat", theirs = "mon")) // I only reordered it
        assertFalse(isPlanConflict(base = "sat", mine = "sun", theirs = "sun")) // we agree
    }
}
