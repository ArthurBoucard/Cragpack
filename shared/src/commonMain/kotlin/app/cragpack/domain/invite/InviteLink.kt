package app.cragpack.domain.invite

/**
 * Invite links look like https://cragpack.app/j/<token> (tech spec §5).
 * ponytail: placeholder domain until the name is secured (D13); only the host changes then.
 */
const val INVITE_HOST = "cragpack.app"

fun inviteLink(token: String) = "https://$INVITE_HOST/j/$token"

/** The token in a pasted or opened link (or a bare token); null if the text isn't an invite. */
fun inviteToken(text: String): String? =
    Regex("""(?:^|/j/)([0-9a-f]{16})(?:[/?#]|$)""").find(text.trim().lowercase())?.groupValues?.get(1)
