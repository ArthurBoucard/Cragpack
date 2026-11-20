package app.cragpack.domain.invite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinkTest {
    private val token = "7a2f00aa11bb22cc"

    @Test
    fun findsTheTokenHoweverTheLinkWasPasted() {
        assertEquals(token, inviteToken(inviteLink(token)))
        assertEquals(token, inviteToken("  Join us! https://cragpack.app/j/$token  "))
        assertEquals(token, inviteToken("cragpack.app/j/${token.uppercase()}?utm=x"))
        assertEquals(token, inviteToken(token))
    }

    @Test
    fun anythingElseIsNotAnInvite() {
        assertNull(inviteToken("https://cragpack.app/j/short"))
        assertNull(inviteToken("https://example.com/${token}x"))
        assertNull(inviteToken(""))
    }
}
