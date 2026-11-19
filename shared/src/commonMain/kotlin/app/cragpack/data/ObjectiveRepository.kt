package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.objective.BetaType
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.domain.objective.Sector
import app.cragpack.domain.trip.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class Objective(
    val id: String,
    val tripId: String,
    val name: String,
    val style: ClimbStyle,
    val grade: Grade,
    val sector: Sector?,
    val wanters: List<Person>,
    /** You starred it. */
    val starred: Boolean,
    /** Someone sent or flashed it. */
    val sent: Boolean,
    /** The kinds of beta it has, for the icons on its project row. */
    val beta: Set<BetaType> = emptySet(),
)

/** [localPath] is relative to the app's files folder (see [MediaFiles]); null for notes and YouTube links. [author]'s id is who added it. */
data class Beta(val id: String, val type: BetaType, val url: String?, val text: String?, val author: Person, val localPath: String?)

data class ObjectiveDetail(val objective: Objective, val beta: List<Beta>)

class ObjectiveRepository internal constructor(
    private val db: CragpackDb,
    private val myId: String,
    private val now: () -> Long,
) {
    private val io = Dispatchers.IO
    private val q get() = db.objectiveQueries

    fun observeWishlist(tripId: String): Flow<List<Objective>> = combine(
        q.selectByTrip(tripId, ::Row).asFlow().mapToList(io),
        observeWanters(tripId),
    ) { rows, wanters -> rows.map { it.toObjective(wanters[it.id].orEmpty()) } }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeObjective(id: String): Flow<ObjectiveDetail?> =
        q.selectObjective(id, ::Row).asFlow().mapToOneOrNull(io).flatMapLatest { row ->
            if (row == null) return@flatMapLatest flowOf(null)
            combine(observeWanters(row.tripId), observeBeta(id)) { wanters, beta ->
                ObjectiveDetail(row.toObjective(wanters[id].orEmpty()), beta)
            }
        }

    fun observeSectors(tripId: String): Flow<List<Sector>> =
        db.tripQueries.selectSectors(tripId, ::Sector).asFlow().mapToList(io)

    /** Returns the new objective's id. */
    fun add(tripId: String, name: String, style: ClimbStyle, grade: Grade, sectorId: String?): String {
        check(name, style, grade)
        val id = newId()
        val t = now()
        q.insertObjective(id, tripId, name.trim(), style.name.lowercase(), grade.scale.name, grade.value, sectorId, myId, t, t)
        return id
    }

    fun update(id: String, name: String, style: ClimbStyle, grade: Grade, sectorId: String?) {
        check(name, style, grade)
        q.updateObjective(name.trim(), style.name.lowercase(), grade.scale.name, grade.value, sectorId, now(), id)
    }

    fun delete(id: String) = q.deleteObjective(now(), id)

    fun setStarred(objectiveId: String, starred: Boolean) {
        val t = now()
        q.setInterest(objectiveId, myId, t, if (starred) null else t)
    }

    /** Returns the new sector's id. */
    fun addSector(tripId: String, name: String): String {
        require(name.isNotBlank()) { "A sector needs a name" }
        val t = now()
        return newId().also { db.tripQueries.insertSector(it, tripId, name.trim(), t, t) }
    }

    fun addNote(objectiveId: String, text: String) {
        require(text.isNotBlank()) { "A note needs text" }
        insertBeta(objectiveId, BetaType.Note, text = text.trim())
    }

    fun editNote(betaId: String, text: String) {
        require(text.isNotBlank()) { "A note needs text" }
        q.updateBetaText(text.trim(), now(), betaId)
    }

    /** Removes any beta item. ponytail: a removed photo or video keeps its file until storage management (Phase 3). */
    fun deleteBeta(betaId: String) = q.deleteBeta(now(), betaId)

    fun addYoutube(objectiveId: String, url: String) {
        require(isYoutubeLink(url)) { "Not a YouTube link: $url" }
        insertBeta(objectiveId, BetaType.Youtube, url = url.trim())
    }

    /** Records a file already copied to [localPath] by [MediaFiles]. It stays queued for upload until accounts exist. */
    fun addMedia(objectiveId: String, type: BetaType, mediaId: String, mime: String, bytes: Long, localPath: String) {
        require(type == BetaType.Photo || type == BetaType.Video || type == BetaType.Topo) { "$type has no file" }
        val t = now()
        db.transaction {
            q.insertMedia(mediaId, myId, mime, bytes, t, t)
            q.insertMediaCache(mediaId, localPath, bytes)
            insertBeta(objectiveId, type, mediaId = mediaId)
        }
    }

    private fun insertBeta(objectiveId: String, type: BetaType, mediaId: String? = null, url: String? = null, text: String? = null) {
        val t = now()
        q.insertBeta(newId(), objectiveId, type.name.lowercase(), mediaId, url, text, myId, t, t)
    }

    private fun observeWanters(tripId: String): Flow<Map<String, List<Person>>> =
        q.selectInterests(tripId).asFlow().mapToList(io).map { rows ->
            rows.groupBy({ it.objective_id }, { Person(it.user_id, it.name, it.color_index.toInt()) })
        }

    private fun observeBeta(objectiveId: String): Flow<List<Beta>> =
        q.selectBeta(objectiveId).asFlow().mapToList(io).map { rows ->
            rows.map {
                Beta(
                    id = it.id,
                    type = BetaType.entries.first { t -> t.name.equals(it.type, ignoreCase = true) },
                    url = it.url,
                    text = it.text,
                    author = Person(it.created_by, it.author_name, it.color_index.toInt()),
                    localPath = it.local_path,
                )
            }
        }

    private fun check(name: String, style: ClimbStyle, grade: Grade) {
        require(name.isNotBlank()) { "An objective needs a name" }
        require(grade.isBoulder == (style == ClimbStyle.Boulder)) { "$style can't have grade ${grade.value}" }
    }

    private inner class Row(
        val id: String,
        val tripId: String,
        val name: String,
        val style: String,
        val scale: String,
        val grade: String,
        val sectorId: String?,
        val sectorName: String?,
        val sent: Boolean,
        val betaTypes: String,
    ) {
        fun toObjective(wanters: List<Person>) = Objective(
            id = id,
            tripId = tripId,
            name = name,
            style = ClimbStyle.entries.first { it.name.equals(style, ignoreCase = true) },
            grade = Grade(GradeScale.valueOf(scale), grade),
            sector = if (sectorId != null && sectorName != null) Sector(sectorId, sectorName) else null,
            wanters = wanters,
            starred = wanters.any { it.id == myId },
            sent = sent,
            beta = betaTypes.split(',').mapNotNull { t -> BetaType.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } }.toSet(),
        )
    }
}

fun isYoutubeLink(url: String): Boolean =
    Regex("""^(https?://)?(www\.|m\.)?(youtube\.com|youtu\.be)/\S+$""").matches(url.trim())

/** The preview image of a YouTube link (watch?v=, youtu.be/, shorts/, embed/, live/), or null if it has no video id. */
fun youtubeThumbnail(url: String): String? =
    Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([\w-]{11})""").find(url)?.groupValues?.get(1)
        ?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" } // 16:9, no letterbox bars (hqdefault has them)
