package app.cragpack.data

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.objective.BetaType
import app.cragpack.domain.objective.ClimbStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectiveRepositoryTest {
    private val graph = AppGraph(testDriver())
    private val repo = graph.objectives
    private val tripId = graph.trips.create("Font Easter", "Fontainebleau", LocalDate.parse("2026-04-17"), LocalDate.parse("2026-04-21"))
    private val font7a = Grade(GradeScale.Font, "7A")

    @Test
    fun addedObjectiveShowsOnTheWishlistWithItsSector() = runTest {
        val sector = repo.addSector(tripId, " Bas Cuvier ")
        val id = repo.add(tripId, " La Joker ", ClimbStyle.Boulder, font7a, sector)

        val o = repo.observeWishlist(tripId).first().single()
        assertEquals(id, o.id)
        assertEquals("La Joker", o.name)
        assertEquals("Bas Cuvier", o.sector?.name)
        assertFalse(o.starred)
        assertEquals(1, graph.trips.observeTrip(tripId).first()!!.stats.unplanned)
    }

    @Test
    fun starTogglesOnAndOff() = runTest {
        val id = repo.add(tripId, "Carnage", ClimbStyle.Boulder, Grade(GradeScale.Font, "7B+"), null)
        repo.setStarred(id, true)
        val starred = repo.observeWishlist(tripId).first().single()
        assertTrue(starred.starred)
        assertEquals(graph.myId, starred.wanters.single().id)

        repo.setStarred(id, false)
        assertFalse(repo.observeWishlist(tripId).first().single().starred)
    }

    @Test
    fun betaIsListedAndCountedOnTheTrip() = runTest {
        val id = repo.add(tripId, "La Joker", ClimbStyle.Boulder, font7a, null)
        repo.addNote(id, "Two pads under the lip.")
        repo.addYoutube(id, "youtu.be/9xQ2abc")
        repo.addMedia(id, BetaType.Photo, newId(), "image/jpeg", 1200, "media/x.jpg")

        val beta = repo.observeObjective(id).first()!!.beta
        assertEquals(listOf(BetaType.Note, BetaType.Youtube, BetaType.Photo), beta.map { it.type })
        assertEquals("media/x.jpg", beta.last().localPath)
        assertEquals(3, graph.trips.observeTrip(tripId).first()!!.stats.beta)
    }

    @Test
    fun projectRowKnowsWhichBetaItHas() = runTest {
        val id = repo.add(tripId, "La Joker", ClimbStyle.Boulder, font7a, null)
        assertEquals(emptySet(), repo.observeWishlist(tripId).first().single().beta)
        repo.addNote(id, "Heel hook")
        repo.addYoutube(id, "youtu.be/9xQ2abc")
        repo.addYoutube(id, "youtu.be/9xQ2abd")
        assertEquals(setOf(BetaType.Note, BetaType.Youtube), repo.observeWishlist(tripId).first().single().beta)
    }

    @Test
    fun noteCanBeEditedAndRemoved() = runTest {
        val id = repo.add(tripId, "La Joker", ClimbStyle.Boulder, font7a, null)
        repo.addNote(id, "Heel hook")
        val note = repo.observeObjective(id).first()!!.beta.single()
        assertEquals(graph.myId, note.author.id)

        repo.editNote(note.id, " Toe hook ")
        assertEquals("Toe hook", repo.observeObjective(id).first()!!.beta.single().text)
        assertFailsWith<IllegalArgumentException> { repo.editNote(note.id, " ") }

        repo.deleteBeta(note.id)
        assertEquals(emptyList(), repo.observeObjective(id).first()!!.beta)
        assertEquals(emptySet(), repo.observeWishlist(tripId).first().single().beta)
    }

    @Test
    fun youtubeLinksGiveTheirThumbnail() {
        val img = "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
        assertEquals(img, youtubeThumbnail("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"))
        assertEquals(img, youtubeThumbnail("youtu.be/dQw4w9WgXcQ?si=x"))
        assertEquals(img, youtubeThumbnail("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(youtubeThumbnail("https://www.youtube.com/@cragpack"))
    }

    @Test
    fun deletedObjectiveDisappears() = runTest {
        val id = repo.add(tripId, "La Joker", ClimbStyle.Boulder, font7a, null)
        repo.delete(id)
        assertEquals(emptyList(), repo.observeWishlist(tripId).first())
        assertNull(repo.observeObjective(id).first())
    }

    @Test
    fun rejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { repo.add(tripId, " ", ClimbStyle.Boulder, font7a, null) }
        assertFailsWith<IllegalArgumentException> { repo.add(tripId, "x", ClimbStyle.Sport, font7a, null) }
        assertFailsWith<IllegalArgumentException> { repo.addYoutube("o", "https://vimeo.com/1") }
        assertTrue(isYoutubeLink("https://www.youtube.com/watch?v=abc"))
    }
}
