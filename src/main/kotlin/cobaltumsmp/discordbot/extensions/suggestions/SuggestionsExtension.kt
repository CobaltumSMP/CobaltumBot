@file:OptIn(ExperimentalTime::class)

@file:Suppress("MagicNumber")

package cobaltumsmp.discordbot.extensions.suggestions

import cobaltumsmp.discordbot.GUILD_MAIN
import cobaltumsmp.discordbot.database.Database
import cobaltumsmp.discordbot.database.entities.Suggestion
import cobaltumsmp.discordbot.database.tables.Suggestions
import cobaltumsmp.discordbot.escapeCodeBlocks
import cobaltumsmp.discordbot.extensions.BaseExtension
import cobaltumsmp.discordbot.isModerator
import cobaltumsmp.discordbot.mainGuild
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.inChannel
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.userFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescedString
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalCoalescingString
import com.kotlindiscord.kord.extensions.components.ComponentContainer
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.delete
import com.kotlindiscord.kord.extensions.utils.deleteIgnoringNotFound
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.MessageType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import kotlin.time.ExperimentalTime

internal const val MAX_RESENT_TEXT_LENGTH = 1800

internal const val MAX_DESCRIPTION_LENGTH = 1000
internal const val MAX_RESPONSE_LENGTH = 700
internal const val DELETION_DELAY = 30_000L

// Button ids
private const val UPVOTE_BUTTON = "Upvote"
private const val DOWNVOTE_BUTTON = "Downvote"
private const val RETRACT_BUTTON = "Retract"

private val EMOTE_UP = ReactionEmoji.Unicode("⬆") // :arrow_up:
private val EMOTE_DOWN = ReactionEmoji.Unicode("⬇") // :arrow_down:
private val EMOTE_WASTEBASKET = ReactionEmoji.Unicode("🗑") // :wastebasket:

private val SUGGESTIONS_CHANNEL = envOrNull("CHANNEL_ID_SUGGESTIONS")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.suggestions")

class SuggestionsExtension : BaseExtension() {
    override val name = "suggestions"

    private val suggestions = mutableListOf<Suggestion>()
    private val suggestionMessages = mutableMapOf<Snowflake, Suggestion>()
    private lateinit var suggestionsChannel: TextChannel

    override suspend fun setup() {
        Database.connect()

        try {
            transaction {
                setupDatabase()
                cacheSuggestions()
            }
        } catch (e: SQLException) {
            LOGGER.error(e) { "Failed to setup the database" }
            return
        }

        // Check if the suggestions channel is set
        if (SUGGESTIONS_CHANNEL == null) {
            LOGGER.error { "No suggestions channel specified" }
            return
        }
        val channelId = Snowflake(SUGGESTIONS_CHANNEL)
        val channel = kord.getChannel(channelId)

        // Validate the channel
        if (channel == null) {
            LOGGER.error { "Suggestions channel not found" }
            return
        } else if (channel.type != ChannelType.GuildText) {
            LOGGER.error { "Suggestions channel is not a text channel" }
            return
        }
        suggestionsChannel = channel as TextChannel

        setupComponents()

        // region events
        // Create a new suggestion
        event<MessageCreateEvent> {
            check { failIfNot(event.message.type == MessageType.Default) }
            check { isNotBot() }

            check { inChannel(channelId) }

            action {
                createSuggestion(event.message)
            }
        }

        // Delete thread creation messages
        event<MessageCreateEvent> {
            check { failIfNot(event.message.channelId == channelId) }
            check { failIfNot(event.message.type == MessageType.ThreadCreated) }

            action {
                event.message.deleteIgnoringNotFound()
            }
        }
        // endregion

        // region commands
        ephemeralSlashCommand(::EditSuggestionArguments) {
            name = "edit-suggestion"
            description = translate("suggestions.command.edit_suggestion.description")

            guild(GUILD_MAIN)

            action {
                val suggestion = suggestions.find { it.id.value == arguments.suggestionId }!!

                // Check if the user is the owner
                if (suggestion.ownerId != user.id) {
                    respond {
                        content = translate("suggestions.command.edit_suggestion.not_owner")
                    }
                    return@action
                }

                // Update database
                transaction {
                    suggestion.description = arguments.description
                }

                // Update message
                updateSuggestion(suggestion)

                // Send response
                respond {
                    content = translate("suggestions.command.edit_suggestion.success")
                }
            }
        }

        ephemeralSlashCommand(::SuggestionStatusArguments) {
            name = "suggestion-status"
            description = translate("suggestions.command.suggestion_status.description")

            guild(GUILD_MAIN)

            check { isModerator() }

            action {
                val suggestion = suggestions.find { it.id.value == arguments.suggestionId }!!
                val clearResponse = when (arguments.response) {
                    "null" -> true
                    "clear" -> true
                    "empty" -> true
                    else -> false
                }

                // Update database
                transaction {
                    suggestion.status = arguments.status

                    if (clearResponse) {
                        // Clear response if the user requested it
                        suggestion.response = null
                    } else if (arguments.response != null) {
                        // Change response if a new one was provided
                        suggestion.response = arguments.response
                    }
                    // Leave the response unmodified if no new one was provided
                }

                // Update suggestion message
                updateSuggestion(suggestion)

                // Send response
                respond {
                    content = translate("suggestions.command.suggestion_status.success")
                }
            }
        }
        // endregion
    }

    private fun setupDatabase() {
        // Create the tables if they don't exist
        SchemaUtils.createMissingTablesAndColumns(Suggestions)
    }

    private fun cacheSuggestions() {
        suggestions.clear()
        suggestionMessages.clear()

        val all = Suggestion.all()
        suggestions.addAll(all)
        suggestionMessages.putAll(all.filter { it.messageId != null }.associateBy { it.messageId!! })
    }

    private suspend fun setupComponents() {
        val openSuggestions = suggestions.filter { it.status == SuggestionStatus.Open }

        openSuggestions.forEach {
            if (it.messageId != null) {
                val message = suggestionsChannel.getMessage(it.messageId!!)
                message.setupSuggestionButtons(it)
            } else {
                // Send the suggestion if it is in the database but has no message
                val message = sendSuggestion(it)

                // Add message id to the suggestion in the database
                transaction {
                    it.messageId = message.id
                }

                // Add the suggestion to the cache
                suggestionMessages[message.id] = it

                // Set up the buttons for the suggestion message
                message.setupSuggestionButtons(it)
            }
        }
    }

    private suspend fun createSuggestion(creationMessage: Message) {
        val user = creationMessage.author!!
        val description = creationMessage.content
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            val escapedDescription = escapeCodeBlocks(description)
            val resentText = if (escapedDescription.length > MAX_RESENT_TEXT_LENGTH) {
                escapedDescription.substring(0, MAX_RESENT_TEXT_LENGTH - 3) + "..."
            } else {
                escapedDescription
            }

            val errorMessage = translate(
                "suggestions.error.description_too_long",
                arrayOf(description.length, MAX_DESCRIPTION_LENGTH, resentText)
            )

            val dm = user.dm {
                content = errorMessage
            }

            if (dm != null) {
                creationMessage.delete()
            } else {
                creationMessage.respond {
                    content = errorMessage
                }.delete(DELETION_DELAY)

                creationMessage.delete(DELETION_DELAY)
            }

            return
        }

        // Get the user as a member
        val guild = mainGuild(kord)!!
        val member = guild.getMember(user.id)

        // Create the suggestion
        var suggestion: Suggestion? = null
        transaction {
            suggestion = Suggestion.new {
                ownerId = user.id
                ownerName = member.displayName
                ownerAvatarUrl = member.avatar?.url
                positiveVoterIds = listOf(ownerId)
                this.description = description
            }
        }

        // Send the suggestion
        val message = sendSuggestion(suggestion!!)

        // Add message id to the suggestion in the database
        transaction {
            suggestion!!.messageId = message.id
        }

        // Add the suggestion to the cache
        suggestions.add(suggestion!!)
        suggestionMessages[message.id] = suggestion!!

        // Set up the buttons for the suggestion message
        message.setupSuggestionButtons(suggestion!!)

        // Delete the creation message
        creationMessage.delete()
    }

    private suspend fun sendSuggestion(suggestion: Suggestion): Message {
        return suggestionsChannel.createMessage {
            embed {
                suggestionEmbed(suggestion)
            }
        }
    }

    private suspend fun updateSuggestion(suggestion: Suggestion) {
        val message = suggestionsChannel.getMessageOrNull(suggestion.messageId!!)
        if (message == null) {
            LOGGER.warn { "Message for suggestion ${suggestion.id}" }
            return
        }

        message.edit {
            embed {
                suggestionEmbed(suggestion)
            }
            components {
                // Add (or replace) the suggestion buttons if it is open, else removing them
                if (suggestion.status == SuggestionStatus.Open) {
                    addSuggestionButtons(suggestion)
                }
            }
        }
    }

    private fun EmbedBuilder.suggestionEmbed(suggestion: Suggestion) {
        author {
            name = suggestion.ownerName
            icon = suggestion.ownerAvatarUrl
        }

        description = "<@${suggestion.ownerId.value}>\n\n${suggestion.description}\n\n"

        if (suggestion.positiveVotes > 0) {
            description += translate("suggestions.positive_votes", arrayOf(suggestion.positiveVotes))
            description += "\n"
        }

        if (suggestion.negativeVotes > 0) {
            description += translate("suggestions.negative_votes", arrayOf(suggestion.negativeVotes))
            description += "\n"
        }

        description += translate("suggestions.total_votes", arrayOf(suggestion.voteDelta))

        if (suggestion.response != null) {
            description += translate("suggestions.response", arrayOf(suggestion.response))
        }

        color = suggestion.status.color

        footer {
            text = translate("suggestions.footer", arrayOf(suggestion.status.readableName, suggestion.id))
        }
    }

    private suspend fun Message.setupSuggestionButtons(suggestion: Suggestion) {
        val status = suggestion.status

        if (status == SuggestionStatus.Open) {
            edit {
                components {
                    addSuggestionButtons(suggestion)
                }
            }
        }
    }

    private suspend fun ComponentContainer.addSuggestionButtons(suggestion: Suggestion) {
        val id = suggestion.id.value

        ephemeralButton {
            emoji(EMOTE_UP)

            label = translate("suggestions.button.vote_positive")
            this.id = "$id/$UPVOTE_BUTTON"
            bundle = "cobaltumbot"

            action {
                // Check suggestion status
                if (suggestion.status != SuggestionStatus.Open) {
                    respond {
                        content = translate("suggestions.error.vote.closed")
                    }

                    return@action
                }

                val user = userFor(event)!!
                val userId = user.id
                val voterIds = suggestion.positiveVoterIds.toMutableList()
                var negativeVoterIds: MutableList<Snowflake>? = null

                // Check if the user has already voted
                if (userId in voterIds) {
                    respond {
                        content = translate("suggestions.error.vote.already_voted_positive")
                    }
                    return@action
                }

                // Remove the user from the negative voter list if they have voted
                if (userId in suggestion.negativeVoterIds) {
                    negativeVoterIds = suggestion.negativeVoterIds.toMutableList()
                    negativeVoterIds.remove(userId)
                }

                // Update database
                voterIds.add(userId)
                transaction {
                    suggestion.positiveVoterIds = voterIds.toList()
                    if (negativeVoterIds != null) {
                        suggestion.negativeVoterIds = negativeVoterIds.toList()
                    }
                }

                // Update suggestion message
                updateSuggestion(suggestion)

                // Send response
                respond {
                    content = translate("suggestions.vote_registered")
                }
            }
        }

        ephemeralButton {
            emoji(EMOTE_DOWN)

            label = translate("suggestions.button.vote_negative")
            this.id = "$id/$DOWNVOTE_BUTTON"
            bundle = "cobaltumbot"

            action {
                // Check suggestion status
                if (suggestion.status != SuggestionStatus.Open) {
                    respond {
                        content = translate("suggestions.error.vote.closed")
                    }

                    return@action
                }

                val user = userFor(event)!!
                val userId = user.id
                val voterIds = suggestion.negativeVoterIds.toMutableList()
                var positiveVoterIds: MutableList<Snowflake>? = null

                // Check if the user has already voted
                if (userId in voterIds) {
                    respond {
                        content = translate("suggestions.error.vote.already_voted_negative")
                    }
                    return@action
                }

                // Remove the user from the positive voter list if they have voted
                if (userId in suggestion.positiveVoterIds) {
                    positiveVoterIds = suggestion.positiveVoterIds.toMutableList()
                    positiveVoterIds.remove(userId)
                }

                // Update database
                voterIds.add(userId)
                transaction {
                    suggestion.negativeVoterIds = voterIds.toList()
                    if (positiveVoterIds != null) {
                        suggestion.positiveVoterIds = positiveVoterIds.toList()
                    }
                }

                // Update suggestion message
                updateSuggestion(suggestion)

                // Send response
                respond {
                    content = translate("suggestions.vote_registered")
                }
            }
        }

        ephemeralButton {
            emoji(EMOTE_WASTEBASKET)

            label = translate("suggestions.button.retract_vote")
            this.id = "$id/$RETRACT_BUTTON"
            style = ButtonStyle.Danger
            bundle = "cobaltumbot"

            action {
                // Check suggestion status
                if (suggestion.status != SuggestionStatus.Open) {
                    respond {
                        content = translate("suggestions.error.vote.closed")
                    }

                    return@action
                }

                val user = userFor(event)!!
                val userId = user.id

                // Check if the user has already voted
                if (userId !in suggestion.positiveVoterIds && userId !in suggestion.negativeVoterIds) {
                    respond {
                        content = translate("suggestions.error.vote.not_voted")
                    }
                    return@action
                }

                val positiveVoterIds = suggestion.positiveVoterIds.toMutableList()
                val negativeVoterIds = suggestion.negativeVoterIds.toMutableList()

                // Update database
                positiveVoterIds.remove(userId)
                negativeVoterIds.remove(userId)
                transaction {
                    suggestion.positiveVoterIds = positiveVoterIds.toList()
                    suggestion.negativeVoterIds = negativeVoterIds.toList()
                }

                // Update suggestion message
                updateSuggestion(suggestion)

                // Send response
                respond {
                    content = translate("suggestions.vote_retracted")
                }
            }
        }
    }

    inner class EditSuggestionArguments : Arguments() {
        val suggestionId by int("suggestionId", translate("suggestions.command_args.suggestionId")) { _, value ->
            if (!suggestions.any { it.id.value == value }) {
                throw DiscordRelayedException(
                    translate(
                        "suggestions.command_args.suggestionId.not_found",
                        arrayOf(value)
                    )
                )
            }
        }

        val description by coalescedString(
            "description",
            translate("suggestions.command.edit_suggestion.args.description")
        ) { _, value ->
            if (value.length > MAX_DESCRIPTION_LENGTH) {
                throw DiscordRelayedException(
                    translate(
                        "suggestions.command.edit_suggestion.args.description.too_long",
                        arrayOf(MAX_DESCRIPTION_LENGTH)
                    )
                )
            }
        }
    }

    inner class SuggestionStatusArguments : Arguments() {
        val suggestionId by int("suggestionId", translate("suggestions.command_args.suggestionId")) { _, value ->
            if (!suggestions.any { it.id.value == value }) {
                throw DiscordRelayedException(
                    translate(
                        "suggestions.command_args.suggestionId.not_found",
                        arrayOf(value)
                    )
                )
            }
        }

        val status by enumChoice<SuggestionStatus>(
            "status",
            translate("suggestions.command.suggestion_status.args.status"),
            "status"
        )

        val response by optionalCoalescingString(
            "response",
            translate("suggestions.command.suggestion_status.args.response")
        ) { _, value ->
            if (value != null && value.length > MAX_RESPONSE_LENGTH) {
                throw DiscordRelayedException(
                    translate(
                        "suggestions.command.suggestion_status.args.response.too_long",
                        arrayOf(MAX_RESPONSE_LENGTH)
                    )
                )
            }
        }
    }
}
