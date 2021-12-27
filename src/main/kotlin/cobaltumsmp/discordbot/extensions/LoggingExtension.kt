@file:OptIn(ExperimentalTime::class)

@file:Suppress("MagicNumber")

package cobaltumsmp.discordbot.extensions

import cobaltumsmp.discordbot.mainGuild
import cobaltumsmp.discordbot.toReadableString
import com.kotlindiscord.kord.extensions.checks.userFor
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.BanRemoveEvent
import dev.kord.core.event.guild.InviteCreateEvent
import dev.kord.core.event.guild.InviteDeleteEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.message.MessageBulkDeleteEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.event.message.ReactionRemoveAllEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import kotlinx.datetime.Clock
import kotlinx.datetime.toDateTimePeriod
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

internal const val BULK_DELETE_SHOWN_MESSAGES = 5

private val LOGS_CHANNEL = env("CHANNEL_ID_LOGS")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.LoggingExtension")

class LoggingExtension : Extension() {
    override val name = "logging"

    private lateinit var logsChannel: TextChannel

    override suspend fun setup() {
        val guild = mainGuild(kord)!!
        val channel = guild.getChannelOrNull(Snowflake(LOGS_CHANNEL))
        if (channel != null) {
            if (channel.type == ChannelType.GuildText) {
                logsChannel = channel as TextChannel
            } else {
                LOGGER.warn { "Logs channel $channel is not a text channel" }
            }
        } else {
            LOGGER.warn { "Logs channel $LOGS_CHANNEL not found, logging will not be available" }
            return
        }

        // TODO: Add details to channel events
        // region channel events
//        // region category channel events
//        event<CategoryCreateEvent> {
//            action {
//                log("Category created") {
//                }
//            }
//        }
//
//        event<CategoryUpdateEvent> {
//            action {
//                log("Category updated") {
//                }
//            }
//        }
//
//        event<CategoryDeleteEvent> {
//            action {
//                log("Category deleted") {
//                }
//            }
//        }
//        // endregion
//
//        // region news channel events
//        event<NewsChannelCreateEvent> {
//            action {
//                log("News channel created") {
//                }
//            }
//        }
//
//        event<NewsChannelUpdateEvent> {
//            action {
//                log("News channel updated") {
//                }
//            }
//        }
//
//        event<NewsChannelDeleteEvent> {
//            action {
//                log("News channel deleted") {
//                }
//            }
//        }
//        // endregion
//
//        // region text channel events
//        event<TextChannelCreateEvent> {
//            action {
//                log("Text channel created") {
//                }
//            }
//        }
//
//        event<TextChannelUpdateEvent> {
//            action {
//                log("Text channel updated") {
//                }
//            }
//        }
//
//        event<TextChannelDeleteEvent> {
//            action {
//                log("Text channel deleted") {
//                }
//            }
//        }
//        // endregion
//
//        // region voice channel events
//        event<VoiceChannelCreateEvent> {
//            action {
//                log("Voice channel created") {
//                }
//            }
//        }
//
//        event<VoiceChannelUpdateEvent> {
//            action {
//                log("Voice channel updated") {
//                }
//            }
//        }
//
//        event<VoiceChannelDeleteEvent> {
//            action {
//                log("Voice channel deleted") {
//                }
//            }
//        }
//        // endregion
//
//        // region stage channel events
//        event<StageChannelCreateEvent> {
//            action {
//                log("Stage channel created") {
//                }
//            }
//        }
//
//        event<StageChannelUpdateEvent> {
//            action {
//                log("Stage channel updated") {
//                }
//            }
//        }
//
//        event<StageChannelDeleteEvent> {
//            action {
//                log("Stage channel deleted") {
//                }
//            }
//        }
//        // endregion
//
//        // region thread channel events
//        event<ThreadChannelCreateEvent> {
//            action {
//                log("Thread channel created") {
//                }
//            }
//        }
//
//        event<ThreadUpdateEvent> {
//            action {
//                log("Thread updated") {
//                }
//            }
//        }
//
//        event<ThreadMembersUpdateEvent> {
//            action {
//                log("Thread members updated") {
//                }
//            }
//        }
//
//        event<ThreadChannelDeleteEvent> {
//            action {
//                log("Thread channel deleted") {
//                }
//            }
//        }
//        // endregion
//
//        event<ChannelPinsUpdateEvent> {
//            action {
//                log("Channel pins updated") {
//                }
//            }
//        }
        // endregion

        // region guild events
        // TODO
//        event<GuildUpdateEvent> {
//            action {
//                log("Guild updated")
//            }
//        }

        // TODO
//        event<EmojisUpdateEvent> {
//            action {
//                log("Emojis updated")
//            }
//        }

        event<InviteCreateEvent> {
            action {
                log("Invite created") {
                    field {
                        name = "Max uses"
                        value = "${event.maxUses}"
                        inline = true
                    }
                    field {
                        name = "Duration"
                        value = event.maxAge.toDateTimePeriod().toReadableString()
                        inline = true
                    }
                    field {
                        name = "Temporary"
                        value = event.isTemporary.toString()
                        inline = true
                    }
                    field {
                        name = "Inviter"
                        value = event.inviter?.mention ?: "Unknown"
                        inline = true
                    }
                    field {
                        name = "Channel"
                        value = event.channel.mention
                        inline = true
                    }
                    field {
                        name = "Link"
                        value = "https://discord.gg/${event.code}"
                    }

                    footer {
                        text = "Invite code: `${event.code}`"
                    }
                }
            }
        }

        event<InviteDeleteEvent> {
            action {
                log("Invite deleted") {
                    field {
                        name = "Invite code"
                        value = "`${event.code}`"
                        inline = true
                    }
                    field {
                        name = "Channel"
                        value = event.channel.mention
                        inline = true
                    }
                }
            }
        }

        // region guild members events
        event<BanAddEvent> {
            action {
                val ban = event.getBan()
                val user = ban.user.asUser()
                log("Banned user") {
                    description = "Banned ${user.username}#${user.discriminator}"

                    if (ban.reason != null) {
                        field {
                            name = "Reason"
                            value = ban.reason!!
                            inline = true
                        }
                    }

                    footer {
                        text = "User ID: ${ban.userId.value}"
                    }
                }
            }
        }

        event<BanRemoveEvent> {
            action {
                val user = event.user.asUser()
                log("Unbanned user") {
                    description = "Unbanned ${user.username}#${user.discriminator}"
                    footer {
                        text = "User ID: ${user.id.value}"
                    }
                }
            }
        }

        event<MemberJoinEvent> {
            action {
                val member = event.member
                log("Member joined") {
                    description = "${member.username}#${member.discriminator} (${member.mention}) joined"
                    footer {
                        text = "User ID: ${member.id.value}"
                    }
                }
            }
        }

        event<MemberLeaveEvent> {
            action {
                val user = event.user
                log("Member left") {
                    description = "${user.username}#${user.discriminator} (${user.mention}) left"
                    footer {
                        text = "User ID: ${user.id.value}"
                    }
                }
            }
        }

        // TODO
//        event<MemberUpdateEvent> {
//            action {
//                log("Member updated") {
//                }
//            }
//        }
        // endregion
        // endregion

        // region role events
        // TODO
//        event<RoleCreateEvent> {
//            action {
//                log("Role created") {
//                }
//            }
//        }
//
//        event<RoleUpdateEvent> {
//            action {
//                log("Role updated") {
//                }
//            }
//        }
//
//        event<RoleDeleteEvent> {
//            action {
//                log("Role deleted") {
//                }
//            }
//        }
        // endregion

        // region message events
        event<MessageUpdateEvent> {
            action {
                val old = event.old
                val new = event.new

                val channelName = (event.channel.asChannel() as TextChannel).name
                if (old != null && new.content.value != null && old.content != new.content.value) {
                    val oldContent = if (old.content.length > 900) {
                        old.content.substring(0, 897) + "..."
                    } else {
                        old.content
                    }
                    val newContent = if (new.content.value!!.length > 900) {
                        new.content.value!!.substring(0, 897) + "..."
                    } else {
                        new.content.value!!
                    }

                    log("Message edited in #$channelName") {
                        field {
                            name = "Before"
                            value = oldContent
                        }
                        field {
                            name = "After"
                            value = newContent
                        }
                        footer {
                            text = "Message ID: ${event.messageId.value}"
                        }
                    }
                } else {
                    log("Message updated in #$channelName") {
                        description = "Unknown change"
                        field {
                            name = "Message ID"
                            value = event.messageId.value.toString()
                        }
                    }
                }
            }
        }

        event<MessageDeleteEvent> {
            action {
                val channelName = (event.getChannel() as TextChannel).name
                log("Message deleted in #$channelName") {
                    description = event.message?.content ?: "<unknown>"
                    footer {
                        text = "Message ID: ${event.messageId.value}"
                    }
                }
            }
        }

        event<MessageBulkDeleteEvent> {
            action {
                val channelName = (event.getChannel() as TextChannel).name
                val messages = event.messages.sortedBy { it.id } // sort by id, the oldest first
                val shownMessages =
                    messages.takeLast(BULK_DELETE_SHOWN_MESSAGES).map { "[${it.author?.mention ?: ""}] ${it.content}" }

                log("Multiple messages deleted in #$channelName") {
                    description = shownMessages.joinToString("\n")
                    footer {
                        text = "Last ${shownMessages.size} messages shown"
                    }
                }
            }
        }

        event<ReactionRemoveAllEvent> {
            action {
                val channelName = (event.getChannel() as TextChannel).name
                val content = event.getMessage().content
                log("All reactions removed from a message in #$channelName") {
                    description = content
                    footer {
                        text = "Message ID: ${event.messageId.value}"
                    }
                }
            }
        }
        // endregion

        // region user events
        // TODO
//        event<UserUpdateEvent> {
//            action {
//                log("User updated") {
//                    footer {
//                        text = "User ID: ${event.user.id}"
//                    }
//                }
//            }
//        }
        // endregion
    }

    private suspend fun EventContext<*>.log(title: String, builder: EmbedBuilder.() -> Unit = {}) {
        val user = userFor(event)?.asUser()
        logsChannel.createMessage {
            embed {
                this.title = title
                timestamp = Clock.System.now()

                if (user != null) {
                    author {
                        name = "${user.username}#${user.discriminator}"
                        icon = user.avatar?.url
                    }
                }

                apply(builder)
            }
        }
    }
}
