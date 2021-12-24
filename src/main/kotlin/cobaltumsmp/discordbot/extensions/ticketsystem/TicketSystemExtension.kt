package cobaltumsmp.discordbot.extensions.ticketsystem

import cobaltumsmp.discordbot.GUILD_MAIN
import cobaltumsmp.discordbot.checkHasPermissionsInChannel
import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isAdministrator
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMessage
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.roleList
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.create.embed
import kotlinx.atomicfu.atomic
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

internal const val MAX_TICKET_CONFIG_ROLES = TICKET_CONFIG_ROLES_LENGTH / 20

private val DB_URL = env("DB_URL")
private val DB_USER = env("DB_USER")
private val DB_PASS = env("DB_PASS")
private val JDBC_DRIVER = envOrNull("JDBC_DRIVER")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.ticketsystem")

internal class TicketSystemExtension : Extension() {
    override val name: String = "Ticket System"
    val ticketConfigIds: MutableList<Int> = mutableListOf()
    private val globalTicketIds: MutableList<Int> = mutableListOf()
    private val ticketsPendingClose: MutableMap<Int, Long> = mutableMapOf()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setup() {
        LOGGER.info { "Setting up Ticket System" }
        LOGGER.info { "Connecting to database $DB_URL" + (JDBC_DRIVER?.let { " with driver $it" } ?: "") }

        try {
            if (JDBC_DRIVER != null) {
                Database.connect(DB_URL, user = DB_USER, password = DB_PASS, driver = JDBC_DRIVER)
            } else {
                Database.connect(DB_URL, user = DB_USER, password = DB_PASS)
            }
            LOGGER.info { "Connected to database" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to connect to database" }
            return
        }

        transaction {
            setupDb()
            updateConfigs()
            updateTickets()
        }

        chatCommand(::SetupTicketsArguments) {
            name = "setuptickets"
            description = "Sets up the ticket system with a new ticket config"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                createConfig(arguments)
            }
        }

        chatCommand(::DeleteTicketConfigArguments) {
            name = "deleteticketconfig"
            description = "Deletes a ticket config"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                deleteConfig(arguments.configId, message)
            }
        }

        // TODO: Fix category permissions command
    }

    private fun setupDb() {
        // Create the tables if they don't exist
        SchemaUtils.create(TicketConfigs, Tickets)
    }

    private fun updateConfigs() {
        val query = TicketConfigs.selectAll()
        ticketConfigIds.clear()
        ticketConfigIds.addAll(query.map { it[TicketConfigs.id].value })
    }

    private fun updateTickets() {
        globalTicketIds.clear()
        globalTicketIds.addAll(Tickets.selectAll().map { it[Tickets.globalTicketId] })

        val time = System.currentTimeMillis()
        ticketsPendingClose.clear()
        ticketsPendingClose.putAll(Tickets.select { Tickets.closeTime greaterEq time }
            .associate { it[Tickets.globalTicketId] to it[Tickets.closeTime] })
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createConfig(arguments: SetupTicketsArguments) {
        val categoryId = arguments.ticketCategory.id.value.toLong()
        val closedCategoryId = arguments.closedTicketCategory.id.value.toLong()

        val guild = kord.getGuild(GUILD_MAIN)!!
        val category = guild.getChannel(Snowflake(categoryId)) as Category
        val closedCategory = guild.getChannel(Snowflake(closedCategoryId)) as Category

        // Setup permissions for the roles
        val allowedRolePermissions =
            Permissions(Permission.ManageMessages, Permission.ReadMessageHistory, Permission.SendMessages)
        category.edit {
            for (role in arguments.roles) {
                addRoleOverwrite(role.id) {
                    allowed = allowedRolePermissions
                }
            }
        }
        closedCategory.edit {
            for (role in arguments.roles) {
                addRoleOverwrite(role.id) {
                    allowed = allowedRolePermissions
                }
            }
        }

        // Send message if needed
        val msgChannel = arguments.messageChannel as TextChannel
        val msg = arguments.message ?: run {
            msgChannel.createMessage {
                embed {
                    description = "To open a ticket press the button below"
                }
            }
        }

        // Add button to message
        // TODO

        val msgId = msg.id.value.toLong()
        val msgChannelId = msg.channelId.value.toLong()

        val rolesStr = arguments.roles.joinToString(",") { it.id.value.toString() }

        val configName = arguments.name ?: ""

        try {
            transaction {
                val id = TicketConfigs.insertAndGetId {
                    it[ticketCategoryId] = categoryId
                    it[closedTicketCategoryId] = closedCategoryId
                    it[messageId] = msgId
                    it[messageChannelId] = msgChannelId
                    it[roles] = rolesStr
                    it[name] = configName
                }

                ticketConfigIds.add(id.value)
                LOGGER.info { "Created ticket config${if (configName.isNotBlank()) " '$configName'" else ""} id $id" }
            }

            LOGGER.debug { "Configs: $ticketConfigIds" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to insert the ticket config in the database" }
            throw DiscordRelayedException("Failed to insert the ticket config in the database")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteConfig(configId: Int, message: Message) {
        try {
            val success = atomic(false)
            transaction {
                success.value = TicketConfigs.deleteWhere { TicketConfigs.id eq configId } >= 1
            }

            if (!success.value) {
                return
            }

            ticketConfigIds.remove(configId)
            // TODO: Delete all tickets within this config
            LOGGER.info { "Deleted ticket config $configId" }
            message.respond {
                content = "Deleted the ticket config $configId"
            }

            LOGGER.debug { "Configs: $ticketConfigIds" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to delete the ticket config in the database" }
            throw DiscordRelayedException("Failed to delete the ticket config in the database")
        }
    }

//    fun openTicket(ticketConfigId: Int) {
//        LOGGER.info { "Opening ticket" }
//    }

    inner class SetupTicketsArguments : Arguments() {
        val ticketCategory by channel(
            "ticketCategory",
            "The category which open ticket channels belong to",
            requiredGuild = { GUILD_MAIN }) { _, channel ->
            if (channel.type != ChannelType.GuildCategory) {
                throw DiscordRelayedException("Channel must be a category")
            } else {
                checkHasPermissionsInChannel(
                    channel,
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

        val message by optionalMessage("message", "The message that should have the button to open a ticket")
    }

    inner class DeleteTicketConfigArguments : Arguments() {
        val configId by int("configId", "The id of the ticket config to delete") { _, value ->
            if (!ticketConfigIds.contains(value)) {
                throw DiscordRelayedException("A config with this id does not exist")
            }
        }
    }
}
