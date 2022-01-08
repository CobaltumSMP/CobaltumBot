package cobaltumsmp.discordbot.extensions

import cobaltumsmp.discordbot.ADMINISTRATOR_ROLES
import cobaltumsmp.discordbot.CHANNEL_ID_BROADCAST
import cobaltumsmp.discordbot.MODERATOR_ROLES
import cobaltumsmp.discordbot.OWNER_ROLES
import cobaltumsmp.discordbot.ROLE_ID_ADMIN
import cobaltumsmp.discordbot.ROLE_ID_MOD
import cobaltumsmp.discordbot.ROLE_ID_OWNER
import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isAdministrator
import cobaltumsmp.discordbot.isModerator
import cobaltumsmp.discordbot.mainGuild
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import com.kotlindiscord.kord.extensions.utils.permissionsForMember
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.NamedFile
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import mu.KotlinLogging
import org.koin.core.component.inject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.utils")

internal class UtilsExtension : Extension() {
    override val name = "utils"
    override val bundle = "cobaltumbot"

    private val translationsProvider: TranslationsProvider by inject()
    private val broadcastTextChannels = mutableMapOf<Snowflake, TextChannel>()
    private val broadcastNewsChannels = mutableMapOf<Snowflake, NewsChannel>()
    private val client = HttpClient { } // Used for downloading attachments

    override suspend fun setup() {
        val guild = mainGuild(kord)
        if (guild == null) {
            LOGGER.error("Could not find main guild")
            return
        }

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
            description = translationsProvider.translate("utils.command.echo.description", bundleName = "cobaltumbot")

            check { inMainGuild() }

            action {
                val channel = arguments.channel?.let { it as TextChannel } ?: message.channel.asChannel()
                val guildChannel = channel as GuildChannel

                if (!guildChannel.botHasPermissions(Permission.SendMessages)) {
                    message.respondTranslated("utils.command.echo.missing_permissions.bot")
                    return@action
                } else if (!guildChannel.permissionsForMember(user!!).contains(Permission.SendMessages)) {
                    message.respondTranslated("utils.command.echo.missing_permissions.user")
                    return@action
                }

                channel.createMessage {
                    content = arguments.message
                }
            }
        }

        chatCommand(::EchoAttachmentsArguments) {
            name = "echoattachments"
            description =
                translationsProvider.translate("utils.command.echoattachments.description", bundleName = "cobaltumbot")

            check { inMainGuild() }
            check { hasPermission(Permission.AttachFiles) }

            action {
                val channel = arguments.channel?.let { it as TextChannel } ?: message.channel.asChannel()
                val guildChannel = channel as GuildChannel

                // Check permissions
                if (!guildChannel.botHasPermissions(Permission.SendMessages, Permission.AttachFiles)) {
                    message.respondTranslated("utils.command.echoattachments.missing_permissions.bot")
                    return@action
                } else if (!guildChannel.permissionsForMember(user!!)
                        .contains(Permission.SendMessages) || !guildChannel.permissionsForMember(user!!)
                        .contains(Permission.AttachFiles)
                ) {
                    message.respondTranslated("utils.command.echoattachments.missing_permissions.user")
                    return@action
                }

                // Check attachments
                val attachments = message.attachments
                if (attachments.isEmpty()) {
                    message.respondTranslated("utils.command.echoattachments.no_attachments")
                    return@action
                }

                // Download attachments
                val files = mutableListOf<NamedFile>()
                for (attachment in attachments) {
                    try {
                        val response: HttpResponse = client.get(attachment.url)
                        val byteArray: ByteArray = response.receive()
                        val inputStream = ByteArrayInputStream(byteArray)

                        files.add(NamedFile(attachment.filename, inputStream))
                    } catch (e: IOException) {
                        LOGGER.error("Could not download attachment $attachment", e)
                        throw DiscordRelayedException(translate("utils.command.echoattachments.download_failed"))
                    }
                }

                channel.createMessage {
                    content = ""
                    this.files.addAll(files)
                }
            }
        }

        for (channelId in CHANNEL_ID_BROADCAST) {
            val channel = kord.getChannel(channelId)
            if (channel != null) {
                when (channel.type) {
                    ChannelType.GuildText -> broadcastTextChannels[channelId] = channel as TextChannel
                    ChannelType.GuildNews -> broadcastNewsChannels[channelId] = channel as NewsChannel
                    else -> LOGGER.warn { "Channel $channel is not a guild text channel" }
                }
            } else {
                LOGGER.warn { "Channel $channelId does not exist" }
            }
        }

        if (broadcastTextChannels.isEmpty() && broadcastNewsChannels.isEmpty()) {
            LOGGER.warn { "No broadcast channel specified" }
        } else {
            val size = broadcastTextChannels.size + broadcastNewsChannels.size
            chatCommand(::BroadcastArguments) {
                name = "broadcast"
                description = translationsProvider.translate(
                    "utils.command.broadcast.description",
                    bundleName = "cobaltumbot",
                    replacements = arrayOf(size)
                )

                check { inMainGuild() }
                check { isModerator() }

                action {
                    for (channel in broadcastTextChannels.values) {
                        channel.createMessage {
                            content = arguments.message
                        }
                    }
                    for (channel in broadcastNewsChannels.values) {
                        channel.createMessage {
                            content = arguments.message
                        }
                    }

                    message.respondTranslated("utils.command.broadcast.success")
                }
            }
        }

        chatCommand(::SetPresenceArguments) {
            name = "setpresence"
            description =
                translationsProvider.translate("utils.command.setpresence.description", bundleName = "cobaltumbot")

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
                            message.respondTranslated("utils.command.setpresence.streaming_not_supported")
                            return@action
                        }
                        else -> {
                            message.respondTranslated("utils.command.setpresence.invalid_type")
                            return@action
                        }
                    }
                }

                message.respondTranslated(
                    "utils.command.setpresence.success",
                    arrayOf(type, arguments.description)
                )
            }
        }

        chatCommand(::SetPresenceStatusArguments) {
            name = "setpresencestatus"
            description = translationsProvider.translate(
                "utils.command.setpresencestatus.description",
                bundleName = "cobaltumbot"
            )

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
                            message.respondTranslated("utils.command.setpresencestatus.invalid_status")
                            return@action
                        }
                    }
                }

                message.respondTranslated("utils.command.setpresencestatus.success")
            }
        }

        chatCommand {
            name = "resetpresence"
            description =
                translationsProvider.translate("utils.command.resetpresence.description", bundleName = "cobaltumbot")

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                event.kord.editPresence {
                    playing("")
                    status = PresenceStatus.Online
                    afk = false
                }

                message.respondTranslated("utils.command.resetpresence.success")
            }
        }
    }

    inner class EchoArguments : Arguments() {
        val channel by optionalChannel(
            "channel",
            translationsProvider.translate("utils.command.echo.args.channel", bundleName = "cobaltumbot")
        )
        val message by coalescedString(
            "message",
            translationsProvider.translate("utils.command.echo.args.message", bundleName = "cobaltumbot")
        )
    }

    inner class EchoAttachmentsArguments : Arguments() {
        val channel by optionalChannel(
            "channel",
            translationsProvider.translate("utils.command.echoattachments.args.channel", bundleName = "cobaltumbot")
        )
    }

    inner class BroadcastArguments : Arguments() {
        val message by coalescedString(
            "message",
            translationsProvider.translate("utils.command.broadcast.args.message", bundleName = "cobaltumbot")
        )
    }

    inner class SetPresenceArguments : Arguments() {
        val type by string(
            "type",
            translationsProvider.translate("utils.command.setpresence.args.type", bundleName = "cobaltumbot")
        )
        val description by coalescedString(
            "text",
            translationsProvider.translate("utils.command.setpresence.args.description", bundleName = "cobaltumbot")
        )
    }

    inner class SetPresenceStatusArguments : Arguments() {
        val status by string(
            "status",
            translationsProvider.translate("utils.command.setpresencestatus.args.status", bundleName = "cobaltumbot")
        )
    }
}
