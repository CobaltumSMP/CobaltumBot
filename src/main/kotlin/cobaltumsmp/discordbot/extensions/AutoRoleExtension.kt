package cobaltumsmp.discordbot.extensions

import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.guild.MemberJoinEvent

private val AUTO_ROLE = envOrNull("ROLE_ID_AUTO_ROLE")?.let { Snowflake(it) }

class AutoRoleExtension : BaseExtension() {
    override val name = "autorole"

    override suspend fun setup() {
        if (AUTO_ROLE != null) {
            event<MemberJoinEvent> {
                action {
                    val member = event.member

                    member.addRole(AUTO_ROLE, "Auto role for new member")
                }
            }
        }
    }
}
