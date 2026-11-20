package app.cragpack.domain.invite

import kotlinx.datetime.LocalDate

/**
 * Invite links look like https://cragpack.app/j/<token> (tech spec §5).
 * ponytail: placeholder domain until the name is secured (D13); then change the host here and in AndroidManifest.xml.
 */
const val INVITE_HOST = "cragpack.app"

fun inviteLink(token: String) = "https://$INVITE_HOST/j/$token"

/** The token in a pasted or opened link (or a bare token); null if the text isn't an invite. */
fun inviteToken(text: String): String? =
    Regex("""(?:^|/j/)([0-9a-f]{16})(?:[/?#]|$)""").find(text.trim().lowercase())?.groupValues?.get(1)

/** What Join (10) shows about a trip before joining it. */
data class InvitePreview(
    val tripId: String,
    val name: String,
    val crag: String,
    val start: LocalDate,
    val end: LocalDate,
    /** The trip's organiser, with their crew colour. */
    val inviter: Pair<String, Int>,
    /** Name and crew colour, in join order. */
    val crew: List<Pair<String, Int>>,
)

/** The link is dead: reset by an organiser, or the trip was deleted (Invite expired, 17). */
class InviteExpiredException : Exception("This link has expired")
