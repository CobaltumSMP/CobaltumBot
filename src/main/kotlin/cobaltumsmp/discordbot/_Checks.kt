package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.memberFor
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import com.kotlindiscord.kord.extensions.utils.permissionsForMember
import com.kotlindiscord.kord.extensions.utils.selfMember
import dev.kord.common.entity.Permission
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.GuildChannel

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

suspend fun checkHasPermissionsInChannel(channel: Channel, vararg permissions: Permission) {
    if (channel !is GuildChannel) {
        return
    }

    if (!channel.botHasPermissions(*permissions)) {
        val botPermissions = channel.permissionsForMember(channel.guild.selfMember()).values
        val missingPermissions = permissions.filter { !botPermissions.contains(it) }
        val missingPermissionsStr = "'" + missingPermissions.joinToString("', '") { permissionToString(it) } + "'"

        throw DiscordRelayedException("Missing permissions in channel: $missingPermissionsStr")
    }
}
