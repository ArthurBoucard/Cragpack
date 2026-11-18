package app.cragpack.domain.plan

private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"

/**
 * Fractional index: a key that sorts strictly between [before] and [after] (null = open end), so an item
 * can be moved without renumbering the others (tech spec §3). Keys never end in '0', so there is always room.
 */
fun keyBetween(before: String?, after: String?): String {
    require(before == null || after == null || before < after) { "$before must sort before $after" }
    val lo = before.orEmpty()
    var hi = after
    val key = StringBuilder()
    var i = 0
    while (true) {
        val l = if (i < lo.length) DIGITS.indexOf(lo[i]) else 0
        val h = if (hi != null && i < hi.length) DIGITS.indexOf(hi[i]) else DIGITS.length
        if (h - l > 1) return key.append(DIGITS[(l + h) / 2]).toString()
        key.append(DIGITS[l])
        if (l < h) hi = null // the prefix is now below [after]; only [before] constrains the rest
        i++
    }
}
