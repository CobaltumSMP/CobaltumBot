package cobaltumsmp.discordbot.extensions.ticketsystem

import cobaltumsmp.discordbot.GUILD_MAIN
import cobaltumsmp.discordbot.checkHasPermissionsInChannel
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalDuration
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMessage
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.roleList
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.commands.converters.impl.userList
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission

class SetupTicketsArguments : Arguments() {
    val ticketCategory by channel(
        "ticketCategory",
        "The category which open ticket channels belong to",
        requiredGuild = { GUILD_MAIN }) { _, channel ->
        if (channel.type != ChannelType.GuildCategory) {
            throw DiscordRelayedException("Channel must be a category")
        } else {
            checkHasPermissionsInChannel(
                channel,
                Permission.ViewChannel,
                Permission.ManageChannels,
                Permission.ManageRoles,
                Permission.ManageMessages
            )
        }
    }

    val closedTicketCategory by channel(
        "closedTicketCategory",
        "The category which closed ticket channels belong to",
        requiredGuild = { GUILD_MAIN }) { _, channel ->
        if (channel.type != ChannelType.GuildCategory) {
            throw DiscordRelayedException("Channel must be a category")
        } else {
            checkHasPermissionsInChannel(
                channel,
                Permission.ViewChannel,
                Permission.ManageChannels,
                Permission.ManageRoles,
                Permission.ManageMessages
            )
        }
    }

    val roles by roleList(
        "roles",
        "A list of roles which have access to all tickets in this ticket config",
        requiredGuild = { GUILD_MAIN }) { _, list ->
        if (list.size > MAX_TICKET_CONFIG_ROLES) {
            throw DiscordRelayedException("You can only have $MAX_TICKET_CONFIG_ROLES roles in a ticket config")
        }
    }

    val messageChannel by channel(
        "messageChannel",
        "The channel in which the message with a button to open a ticket is sent",
        requiredGuild = { GUILD_MAIN }) { _, channel ->
        if (channel.type != ChannelType.GuildText) {
            throw DiscordRelayedException("Channel must be a text channel")
        }
    }

    val name by optionalString("name", "The name of the ticket config") { _, value ->
        if (value != null && value.length > TICKET_CONFIG_NAME_LENGTH) {
            throw DiscordRelayedException("Name must be less than $TICKET_CONFIG_NAME_LENGTH characters")
        }
    }

    val ticketsBaseName by optionalString("ticketsBaseName", "The base name of the ticket channels") { _, value ->
        if (value != null && value.length > TICKET_BASE_NAME_LENGTH) {
            throw DiscordRelayedException("Name must be less than $TICKET_BASE_NAME_LENGTH characters")
        }
    }

    val message by optionalMessage("message", "The message that should have the button to open a ticket")
}

class GenericTicketConfigArguments(private val extension: TicketSystemExtension) : Arguments() {
    val configId by int("configId", "The id of the ticket config") { _, value ->
        if (!extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class FixTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to fix. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class AddUserToTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val users by userList("users", "The user or users to add to the ticket")
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to add users to. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class RemoveUserFromTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val users by userList("users", "The user or users to remove from the ticket")
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to remove users from. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class TransferTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val user by user("user", "The user to transfer the ticket ownership to")
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to remove users from. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class CloseTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val delay by optionalDuration("delay", "The delay before closing the ticket")
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to fix. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}

class RenameTicketArguments(private val extension: TicketSystemExtension) : Arguments() {
    val name by string("name", "The new name of the ticket")
    val ticketId by optionalInt(
        "ticketId", "The id of the ticket to fix. " +
                "Can be inferred from the current channel. " +
                "Must be the global id if there is more than one ticket config and it wasn't specified"
    )
    val isGlobalId by optionalBoolean("isGlobalId", "Whether the ticket id is a global id")
    val configId by optionalInt("configId", "The id of the ticket config from which the ticket is") { _, value ->
        if (value != null && !extension.ticketConfigIds.contains(value)) {
            throw DiscordRelayedException("A config with this id does not exist")
        }
    }
}
