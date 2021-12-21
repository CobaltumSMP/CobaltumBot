package cobaltumsmp.discordbot.extensions

import cobaltumsmp.discordbot.ADMINISTRATOR_ROLES
import cobaltumsmp.discordbot.CHANNEL_ID_BROADCAST
import cobaltumsmp.discordbot.GUILD_MAIN
import cobaltumsmp.discordbot.MODERATOR_ROLES
import cobaltumsmp.discordbot.OWNER_ROLES
import cobaltumsmp.discordbot.ROLE_ID_ADMIN
import cobaltumsmp.discordbot.ROLE_ID_MOD
import cobaltumsmp.discordbot.ROLE_ID_OWNER
import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isAdministrator
import cobaltumsmp.discordbot.isModerator
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import com.kotlindiscord.kord.extensions.utils.permissionsForMember
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.TextChannel
import mu.KotlinLogging
import java.util.Locale

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.UtilsExtension")

internal class UtilsExtension : Extension() {
    override val name = "utils"
    private val broadcastChannels = mutableMapOf<Snowflake, TextChannel>()

    override suspend fun setup() {
        if (kord.getGuild(GUILD_MAIN) == null) {
            LOGGER.error("Could not find main guild")
            return
        }

        val guild = kord.getGuild(GUILD_MAIN)!!

        // Check roles
        if (ROLE_ID_MOD == null || guild.getRoleOrNull(ROLE_ID_MOD) == null) {
            LOGGER.warn("Could not find moderator role, some commands may not work")
            MODERATOR_ROLES.remove(ROLE_ID_MOD)
        }

        if (ROLE_ID_ADMIN == null || guild.getRoleOrNull(ROLE_ID_ADMIN) == null) {
            LOGGER.warn("Could not find admin role, some commands may not work")
            MODERATOR_ROLES.remove(ROLE_ID_ADMIN)
            ADMINISTRATOR_ROLES.remove(ROLE_ID_ADMIN)
        }

        if (ROLE_ID_OWNER == null || guild.getRoleOrNull(ROLE_ID_OWNER) == null) {
            LOGGER.warn("Could not find owner role, some commands may not work")
            MODERATOR_ROLES.remove(ROLE_ID_OWNER)
            ADMINISTRATOR_ROLES.remove(ROLE_ID_OWNER)
            OWNER_ROLES.remove(ROLE_ID_OWNER)
        }

//        chatCommand {
//            name = "ping"
//            description = "Pong! Get the bot ping"
//            action {
//                // TODO
//            }
//        }

        chatCommand(::EchoArguments) {
            name = "echo"
            description = "Echo a message"

            check { inMainGuild() }

            action {
                val channel = arguments.channel?.let { it as TextChannel } ?: message.channel.asChannel()
                val guildChannel = channel as GuildChannel

                if (!guildChannel.botHasPermissions(Permission.SendMessages)) {
                    message.respond("I don't have permission to send messages in that channel")
                    return@action
                } else if (!guildChannel.permissionsForMember(user!!).contains(Permission.SendMessages)) {
                    message.respond("You don't have permission to send messages in that channel")
                    return@action
                }

                channel.createMessage {
                    content = arguments.message
                }
            }
        }

        for (channelId in CHANNEL_ID_BROADCAST) {
            val channel = kord.getChannel(channelId)
            if (channel != null && channel.type == ChannelType.GuildText) {
                broadcastChannels[channelId] = channel as TextChannel
            } else {
                channel?.let { LOGGER.warn { "Channel $it is not a guild text channel" } }
                    ?: LOGGER.warn { "Channel $channelId does not exist" }
            }
        }

        if (broadcastChannels.isEmpty()) {
            LOGGER.warn { "No broadcast channel specified" }
        }

        chatCommand(::BroadcastArguments) {
            name = "broadcast"
            description = "Send a message as the bot in the configured broadcast channel"

            check { inMainGuild() }
            check { isModerator() }

            action {
                if (broadcastChannels.isEmpty()) {
                    message.respond("No broadcast channel configured", pingInReply = false)
                    return@action
                }

                for (channel in broadcastChannels.values) {
                    channel.createMessage {
                        content = arguments.message
                    }
                }
            }
        }

        chatCommand(::SetPresenceArguments) {
            name = "setpresence"
            description = "Set the bot presence"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                val type = arguments.type.lowercase(Locale.getDefault())

                event.kord.editPresence {
                    when (type) {
                        "playing" -> playing(arguments.description)
                        "listening" -> listening(arguments.description)
                        "watching" -> watching(arguments.description)
                        "competing" -> competing(arguments.description)
                        "streaming" -> {
                            message.respond("Streaming is not supported")
                            return@action
                        }
                        else -> {
                            message.respond("Invalid presence type")
                            return@action
                        }
                    }
                }

                message.respond("Presence set to '$type ${arguments.description}'")
            }
        }

        chatCommand(::SetPresenceStatusArguments) {
            name = "setpresencestatus"
            description = "Set the bot presence status"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                val statusStr = arguments.status.lowercase(Locale.getDefault())

                event.kord.editPresence {
                    status = when (statusStr) {
                        "online" -> PresenceStatus.Online
                        "idle" -> PresenceStatus.Idle
                        "dnd" -> PresenceStatus.DoNotDisturb
                        "offline" -> PresenceStatus.Offline
                        "invisible" -> PresenceStatus.Invisible
                        else -> {
                            message.respond("Invalid status, must be one of online, idle, dnd, offline, invisible")
                            return@action
                        }
                    }
                }

                message.respond("Presence status set to $statusStr")
            }
        }

        chatCommand {
            name = "resetpresence"
            description = "Reset the bot presence"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                event.kord.editPresence {
                    playing("")
                    status = PresenceStatus.Online
                    afk = false
                }

                message.respond("Presence reset")
            }
        }
    }

    inner class EchoArguments : Arguments() {
        val channel by optionalChannel("channel", "The channel to send the message to")
        val message by coalescedString("message", "The message to send")
    }

    inner class BroadcastArguments : Arguments() {
        val message by coalescedString("message", "The message to send")
    }

    inner class SetPresenceArguments : Arguments() {
        val type by string("type", "The type of presence to set")
        val description by coalescedString("text", "The description to set")
    }

    inner class SetPresenceStatusArguments : Arguments() {
        val status by string("status", "The status to set")
    }
}
