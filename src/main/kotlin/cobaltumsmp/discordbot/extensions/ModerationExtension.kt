package cobaltumsmp.discordbot.extensions

import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isModerator
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.utils.delete
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toSet

internal const val RESPONSE_DELETE_DELAY = 5000L

class ModerationExtension : Extension() {
    override val name = "moderation"

    override suspend fun setup() {
        chatCommand(::ClearArguments) {
            name = "clear"
            description = "Delete a number of messages from the current channel."

            check { inMainGuild() }
            check { isModerator() }

            action {
                // Delete messages
                val channel = channel.asChannel() as TextChannel
                val messages = channel.getMessagesBefore(message.id, arguments.amount).takeWhile { it.id < message.id }
                    .map { it.id }.toSet()
                channel.bulkDelete(messages, reason = "Cleared by ${message.author?.username}")

                // Send response
                val response = message.respond {
                    content = "Cleared ${messages.size} messages."
                }
                // Delete invocation
                message.delete()

                // Delete response after delay
                response.delete(RESPONSE_DELETE_DELAY, true)
            }
        }
    }

    inner class ClearArguments : Arguments() {
        val amount by int("amount", "The amount of messages to delete")
    }
}
