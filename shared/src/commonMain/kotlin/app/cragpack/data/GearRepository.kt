package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.trip.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/** A climber bringing [quantity] of an item. */
data class Bringer(val person: Person, val quantity: Int)

data class GearItem(
    val id: String,
    val name: String,
    val needed: Int,
    /** The day it's for; null = the whole trip. */
    val dayId: String?,
    val date: LocalDate?,
    val bringers: List<Bringer>,
    /** How many you bring. */
    val mine: Int,
) {
    /** Can exceed [needed]: people may bring extra. */
    val claimed: Int get() = bringers.sumOf { it.quantity }
    val missing: Int get() = (needed - claimed).coerceAtLeast(0)
}

class GearRepository internal constructor(
    private val db: CragpackDb,
    private val me: StateFlow<String>,
    private val now: () -> Long,
) {
    private val myId get() = me.value
    private val io = Dispatchers.IO
    private val q get() = db.gearQueries

    fun observeGear(tripId: String): Flow<List<GearItem>> = combine(
        q.selectItems(tripId).asFlow().mapToList(io),
        q.selectClaims(tripId).asFlow().mapToList(io),
    ) { items, claims ->
        val byItem = claims.groupBy({ it.gear_item_id }, { Bringer(Person(it.user_id, it.name, it.color_index.toInt()), it.quantity.toInt()) })
        items.map {
            val bringers = byItem[it.id].orEmpty()
            GearItem(
                it.id, it.name, it.quantity_needed.toInt(), it.day_id, it.day_date?.let(LocalDate::parse),
                bringers, bringers.firstOrNull { b -> b.person.id == myId }?.quantity ?: 0,
            )
        }
    }

    /** Adds an item; with [iBringIt] you bring all of it. Returns the new item's id. */
    fun add(tripId: String, name: String, needed: Int, dayId: String?, iBringIt: Boolean): String {
        require(name.isNotBlank()) { "Gear needs a name" }
        require(needed >= 1) { "Needed must be at least 1" }
        val id = newId()
        val t = now()
        db.transaction {
            q.insertItem(id, tripId, name.trim(), needed.toLong(), dayId, myId, t, t)
            if (iBringIt) setMine(id, needed)
        }
        return id
    }

    /*
     * Changes are relative (+1 / −1) and read the stored value inside a transaction, so two quick taps
     * add up instead of both writing the same number from a stale screen.
     */

    /** "Needed" stepper; never below 1. */
    fun changeNeeded(itemId: String, delta: Int) = db.transaction {
        val needed = q.selectNeeded(itemId).executeAsOne() + delta
        q.setNeeded(needed.coerceAtLeast(1), now(), itemId)
    }

    /** "I bring" stepper and "Bring 1" (+1); 0 = you're not bringing it. */
    fun changeMine(itemId: String, delta: Int) = db.transaction {
        setMine(itemId, ((q.selectMyClaim(itemId, myId).executeAsOneOrNull()?.toInt() ?: 0) + delta).coerceAtLeast(0))
    }

    private fun setMine(itemId: String, quantity: Int) {
        val t = now()
        q.setClaim(itemId, myId, quantity.toLong(), t, if (quantity == 0) t else null)
    }

    fun delete(itemId: String) = q.deleteItem(now(), itemId)
}
