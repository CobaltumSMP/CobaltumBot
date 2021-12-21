package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext

suspend fun CheckContext<*>.inMainGuild() {
    if (!passed) {
        return
    }

    val guild = guildFor(event)

    if (guild == null) {
        fail("Must be in the main server")
    } else {
        if (guild.id != GUILD_MAIN) {
            fail("Must be in the main server")
        }
    }
}

suspend fun CheckContext<*>.isModerator() {
    if (!passed) {
        return
    }

    inMainGuild()

    if (this.passed) {
        val member = memberFor(event)?.asMemberOrNull()

        if (member == null) {
            fail()
        } else {
            if (!member.roleIds.any { it in MODERATOR_ROLES }) {
                fail("Must be a moderator")
            }
        }
    }
}

suspend fun CheckContext<*>.isAdministrator() {
    if (!passed) {
        return
    }

    inMainGuild()

    if (this.passed) {
        val member = memberFor(event)?.asMemberOrNull()

        if (member == null) {
            fail()
        } else {
            if (!member.roleIds.any { it in ADMINISTRATOR_ROLES }) {
                fail("Must be an administrator")
            }
        }
    }
}
