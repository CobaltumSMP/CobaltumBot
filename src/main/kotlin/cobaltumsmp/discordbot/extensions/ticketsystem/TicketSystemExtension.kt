@file:OptIn(ExperimentalTime::class)

@file:Suppress("MagicNumber")

package cobaltumsmp.discordbot.extensions.ticketsystem

import cobaltumsmp.discordbot.GUILD_MAIN
import cobaltumsmp.discordbot.checkHasPermissionsInChannel
import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isAdministrator
import cobaltumsmp.discordbot.mainGuild
import cobaltumsmp.discordbot.memberOverwrite
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMessage
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.roleList
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
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
import dev.kord.core.behavior.channel.createTextChannel
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.ExperimentalTime

internal const val MAX_TICKET_CONFIG_ROLES = TICKET_CONFIG_ROLES_LENGTH / 20
internal const val MIN_TICKET_CREATE_DELAY_MINUTES = 30
internal const val MIN_TICKET_CREATE_DELAY = MIN_TICKET_CREATE_DELAY_MINUTES * 60 * 1000L
internal const val MAX_OPEN_TICKETS_PER_USER = 3

private val EMOTE_TICKET = ReactionEmoji.Unicode("🎫") // :ticket:
private val EMOTE_LOCK = ReactionEmoji.Unicode("🔒") // :lock:

private val DB_URL = env("DB_URL")
private val DB_USER = env("DB_USER")
private val DB_PASS = env("DB_PASS")
private val JDBC_DRIVER = envOrNull("JDBC_DRIVER")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.ticketsystem")

internal class TicketSystemExtension : Extension() {
    override val name: String = "Ticket System"

    val ticketConfigIds: MutableList<Int> = mutableListOf()
    private val ticketConfigMessageChannelIds: MutableList<Long> = mutableListOf()
    private val globalTicketIds: MutableList<Int> = mutableListOf()
    private val ticketsPendingClose: MutableMap<Int, Long> = mutableMapOf()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setup() {
        LOGGER.info { "Setting up Ticket System" }
        LOGGER.info { "Connecting to database $DB_URL" + (JDBC_DRIVER?.let { " with driver $it" } ?: "") }

        if (JDBC_DRIVER != null) {
            Database.connect(DB_URL, user = DB_USER, password = DB_PASS, driver = JDBC_DRIVER)
        } else {
            Database.connect(DB_URL, user = DB_USER, password = DB_PASS)
        }

        // Try to set up the database and retrieve the initial data
        var success = false
        transaction {
            try {
                setupDb()
                updateConfigs()
                updateTickets()
                success = true
            } catch (e: Exception) {
                LOGGER.error(e) { "Failed to setup database" }
                return@transaction
            }
        }

        // Abort if there was an exception
        if (!success) {
            return
        }

        setupInteractions()

        chatCommand(::SetupTicketsArguments) {
            name = "setuptickets"
            description = "Sets up the ticket system with a new ticket config"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                createConfig(message, arguments)
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
        // TODO: List ticket configs command
        // TODO: Fix ticket permissions command
        // TODO: Add user to ticket command
    }

    private fun setupDb() {
        // Create the tables if they don't exist
        SchemaUtils.createMissingTablesAndColumns(TicketConfigs, Tickets)
    }

    private fun updateConfigs() {
        val query = TicketConfigs.selectAll()
        ticketConfigIds.clear()
        ticketConfigIds.addAll(query.map { it[TicketConfigs.id].value })
        ticketConfigMessageChannelIds.clear()
        ticketConfigMessageChannelIds.addAll(query.map { it[TicketConfigs.messageChannelId] })
    }

    private fun updateTickets() {
        globalTicketIds.clear()
        globalTicketIds.addAll(Tickets.selectAll().map { it[Tickets.globalTicketId] })

        val time = System.currentTimeMillis()
        ticketsPendingClose.clear()
        ticketsPendingClose.putAll(Tickets.select {
            Tickets.closeTime.isNotNull() and (Tickets.closeTime greaterEq time)
        }.associate { it[Tickets.globalTicketId] to it[Tickets.closeTime]!! })
    }

    private suspend fun setupInteractions() {
        val guild = mainGuild(kord)!!
        val configs: MutableList<ResultRow> = mutableListOf()
        transaction {
            configs.addAll(TicketConfigs.selectAll())
        }

        configs.forEach {
            val channel = guild.getChannel(Snowflake(it[TicketConfigs.messageChannelId])) as TextChannel
            val message = channel.getMessageOrNull(Snowflake(it[TicketConfigs.messageId]))!!

            message.edit {
                setupTicketConfigButtons(it[TicketConfigs.id].value)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createConfig(message: Message, arguments: SetupTicketsArguments) {
        val categoryId = arguments.ticketCategory.id.value.toLong()
        val closedCategoryId = arguments.closedTicketCategory.id.value.toLong()

        val guild = mainGuild(kord)!!
        val category = guild.getChannel(Snowflake(categoryId)) as Category
        val closedCategory = guild.getChannel(Snowflake(closedCategoryId)) as Category

        // Setup permissions for the roles
        val allowedRolePermissions =
            Permissions(
                Permission.ViewChannel,
                Permission.ManageMessages,
                Permission.ReadMessageHistory,
                Permission.SendMessages
            )
        val deniedEveryonePermissions = Permissions(Permission.ViewChannel)
        val everyoneRole = guild.getEveryoneRole()
        category.edit {
            for (role in arguments.roles) {
                addRoleOverwrite(role.id) {
                    allowed = allowedRolePermissions
                }
                addRoleOverwrite(everyoneRole.id) {
                    denied = deniedEveryonePermissions
                }
            }
        }
        closedCategory.edit {
            for (role in arguments.roles) {
                addRoleOverwrite(role.id) {
                    allowed = allowedRolePermissions
                }
                addRoleOverwrite(everyoneRole.id) {
                    denied = deniedEveryonePermissions
                }
            }
        }

        // Send message if needed
        val msgChannel = arguments.messageChannel as TextChannel
        val msg = arguments.message ?: run {
            msgChannel.createMessage {
                embed {
                    description = """
                        Press the button below to open a new ticket

                        If the button doesn't do anything first time, try again in a few seconds
                    """.trimIndent()
                }
            }
        }

        val msgId = msg.id.value.toLong()
        val msgChannelId = msg.channelId.value.toLong()

        val rolesStr = arguments.roles.joinToString(",") { it.id.value.toString() }
        val configName = arguments.name ?: ""

        var id: Int = -1

        try {
            transaction {
                id = TicketConfigs.insertAndGetId {
                    it[ticketCategoryId] = categoryId
                    it[closedTicketCategoryId] = closedCategoryId
                    it[messageId] = msgId
                    it[messageChannelId] = msgChannelId
                    it[roles] = rolesStr
                    it[name] = configName
                }.value

                ticketConfigIds.add(id)
            }

            LOGGER.debug { "Inserted ticket config $id" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to insert the ticket config in the database" }
            throw DiscordRelayedException("Failed to insert the ticket config in the database")
        }

        // Add button to message
        msg.edit {
            setupTicketConfigButtons(id)
        }

        LOGGER.info { "Created ticket config${if (configName.isNotBlank()) " '$configName'" else ""} id $id" }
        message.respond("Created ticket config${if (configName.isNotBlank()) " '$configName'" else ""} id $id")
    }

    private suspend fun MessageModifyBuilder.setupTicketConfigButtons(id: Int) {
        components {
            publicButton {
                emoji(EMOTE_TICKET)
                label = "Open Ticket"
                this.id = "$id/CreateTicket"

                body = {
                    action {
                        val user = user.asUser()
                        LOGGER.debug {
                            "Opening ticket in config $id for user ${user.username}#${user.discriminator} (${user.id})"
                        }
                        createTicket(user, id)
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteConfig(configId: Int, message: Message) {
        try {
            var success = false
            transaction {
                success = TicketConfigs.deleteWhere { TicketConfigs.id eq configId } >= 1
            }

            if (!success) {
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

    private suspend fun createTicket(owner: User, configId: Int) {
        var config: ResultRow? = null
        val userOpenTickets: MutableList<ResultRow> = mutableListOf()
        var lastTicketCreateTime: Long = -1L
        val userId = owner.id.value.toLong()

        transaction {
            TicketConfigs.select { TicketConfigs.id eq configId }.forEach {
                config = it
            }
            Tickets.select { Tickets.ownerId eq userId and (Tickets.closed eq false) }.forEach(userOpenTickets::add)
            lastTicketCreateTime = Tickets.select { Tickets.ownerId eq userId }.maxByOrNull { it[Tickets.createTime] }
                ?.let { it[Tickets.createTime] } ?: -1L
        }

        if (userOpenTickets.size >= MAX_OPEN_TICKETS_PER_USER) {
            LOGGER.debug { "User ${owner.username}#${owner.discriminator} ($userId) " +
                    "has too many open tickets (${userOpenTickets.size})" }
            throw DiscordRelayedException("You can only have $MAX_OPEN_TICKETS_PER_USER open tickets at a time")
        }

        val time = System.currentTimeMillis()
        if (lastTicketCreateTime != -1L && time - lastTicketCreateTime <= MIN_TICKET_CREATE_DELAY) {
            LOGGER.debug { "User ${owner.username}#${owner.discriminator} ($userId) " +
                    "has created a ticket in the last 5 minutes" }
            throw DiscordRelayedException("You have opened a ticket in the last 5 minutes. " +
                    "Please wait 5 minutes before opening another ticket.")
        }

        val id = config!![TicketConfigs.ticketCount]
        val categoryId = config!![TicketConfigs.ticketCategoryId]
        val guild = mainGuild(kord)!!
        val category = guild.getChannel(Snowflake(categoryId)) as Category

        val channel = category.createTextChannel("ticket-${id.toString().padStart(4, '0')}")
        val chnlId = channel.id.value.toLong()

        var globalTicketId: Int = -1
        transaction {
            globalTicketId = Tickets.insertAndGetGlobalId {
                it[ticketId] = id
                it[channelId] = chnlId
                it[createTime] = time
                it[ownerId] = userId
                it[ticketConfigId] = configId
            }
            TicketConfigs.update({ TicketConfigs.id eq configId }) {
                it[ticketCount] = id + 1
            }
        }

        channel.edit {
            val overwrites = permissionOverwrites ?: mutableSetOf()
            overwrites.add(memberOverwrite(owner.id) {
                allowed = Permissions(Permission.ViewChannel) // Only this permission is denied for @everyone
            })
            permissionOverwrites = overwrites
        }

        val botMsg = channel.createMessage {
            content = owner.mention
            embed {
                description = """
                    **Support will be with you shortly**
                    In the meantime, please provide as much information about your issue as possible

                    You can use the button below to close the ticket.
                """.trimIndent()
                color = DISCORD_GREEN
            }
        }
        botMsg.edit {
            components {
                publicButton {
                    emoji(EMOTE_LOCK)
                    label = "Close Ticket"
                    this.id = "$globalTicketId/CloseTicket"

                    action {
                        // TODO
                        throw DiscordRelayedException("Not implemented yet")
                    }
                }
            }
        }
        val msgId = botMsg.id.value.toLong()

        transaction {
            Tickets.update({ Tickets.ownerId eq userId and (Tickets.globalTicketId eq globalTicketId) }) {
                it[botMsgId] = msgId
            }
        }
    }

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
