package app.cragpack.domain.objective

import app.cragpack.domain.grade.GradeScale

/** Stored lowercase in the database. */
enum class ClimbStyle(val label: String) {
    Boulder("Boulder"), Sport("Sport"), Trad("Trad");

    /** The scale new grades are entered in: Font for boulders, French for routes. */
    val scale: GradeScale get() = if (this == Boulder) GradeScale.Font else GradeScale.French
}

data class Sector(val id: String, val name: String)

enum class BetaType { Video, Photo, Topo, Note, Youtube }
