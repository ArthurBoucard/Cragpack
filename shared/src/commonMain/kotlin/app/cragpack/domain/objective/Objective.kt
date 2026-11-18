package app.cragpack.domain.objective

/** Stored lowercase in the database. Boulders take Font or V grades, routes French (see entryScale). */
enum class ClimbStyle(val label: String) { Boulder("Boulder"), Sport("Sport"), Trad("Trad") }

/** [approach] is the card line on the plan: "5 min approach · dry from 11:00". */
data class Sector(val id: String, val name: String, val approach: String? = null)

enum class BetaType { Video, Photo, Topo, Note, Youtube }
