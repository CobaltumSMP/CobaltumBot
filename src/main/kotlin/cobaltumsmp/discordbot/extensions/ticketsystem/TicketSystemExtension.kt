@file:OptIn(ExperimentalTime::class)

@file:Suppress("MagicNumber")

package cobaltumsmp.discordbot.extensions.ticketsystem

import cobaltumsmp.discordbot.inMainGuild
import cobaltumsmp.discordbot.isAdministrator
import cobaltumsmp.discordbot.isModerator
import cobaltumsmp.discordbot.mainGuild
import cobaltumsmp.discordbot.memberOverwrite
import cobaltumsmp.discordbot.roleOverwrite
import cobaltumsmp.discordbot.toReadableString
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_YELLOW
import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.channelIdFor
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.checks.userFor
import com.kotlindiscord.kord.extensions.commands.chat.ChatCommandContext
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.PublicInteractionContext
import com.kotlindiscord.kord.extensions.types.respondEphemeral
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.scheduling.Task
import dev.kord.common.entity.ButtonStyle
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
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.AllowedMentionsBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

internal const val MAX_TICKET_CONFIG_ROLES = TICKET_CONFIG_ROLES_LENGTH / 20
internal const val MIN_TICKET_CREATE_DELAY_MINUTES = 0.5
internal const val MIN_TICKET_CREATE_DELAY = MIN_TICKET_CREATE_DELAY_MINUTES * 60 * 1000L
internal const val MAX_OPEN_TICKETS_PER_USER = 3
internal const val MAX_EXTRA_USERS_PER_TICKET = TICKET_EXTRA_USERS_LENGTH / 20
internal const val MIN_SCHEDULE_TICKET_CLOSE_DELAY = 30

private val EMOTE_TICKET = ReactionEmoji.Unicode("🎫") // :ticket:
private val EMOTE_LOCK = ReactionEmoji.Unicode("🔒") // :lock:
private val EMOTE_WARNING = ReactionEmoji.Unicode("⚠") // :warning:

private val TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS = Permissions(
    Permission.ViewChannel,
    Permission.ManageMessages,
    Permission.ReadMessageHistory,
    Permission.SendMessages
)
private val CLOSED_TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS = Permissions(
    Permission.ViewChannel,
    Permission.ReadMessageHistory
)
private val TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS = Permissions(Permission.ViewChannel)
private val CLOSED_TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS =
    Permissions(Permission.ViewChannel, Permission.SendMessages)
private val TICKET_PER_USER_ALLOWED_PERMISSIONS =
    Permissions(Permission.ViewChannel) // Only this permission is denied for @everyone

private val DATABASE_URL = envOrNull("DATABASE_URL") // Heroku Postgres database URL
private val DB_URL = envOrNull("DB_URL")
private val DB_USER = envOrNull("DB_USER")
private val DB_PASS = envOrNull("DB_PASS")
private val JDBC_DRIVER = envOrNull("JDBC_DRIVER")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.ticketsystem")

class TicketSystemExtension : Extension() {
    override val name: String = "Ticket System"

    private val scheduler: Scheduler = Scheduler()
    private val closeTicketTasks = mutableMapOf<Int, Task>()

    // Ticket configs cache
    val ticketConfigIds = mutableListOf<Int>()

    // Tickets cache
    private val ticketChannelIds = mutableMapOf<Long, Int>()
    private val ticketOwners = mutableMapOf<Int, Long>()
    private val ticketsPendingClose = mutableMapOf<Int, Instant>()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setup() {
        LOGGER.info { "Setting up Ticket System" }

        val url: String
        val username: String
        val password: String
        if (DATABASE_URL != null) {
            val uri = URI(DATABASE_URL)

            username = uri.userInfo.split(":")[0]
            password = uri.userInfo.split(":")[1]
            url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"
        } else {
            url = DB_URL!!
            username = DB_USER!!
            password = DB_PASS!!
        }

        LOGGER.info { "Connecting to database $url" + (JDBC_DRIVER?.let { " with driver $it" } ?: "") }

        if (JDBC_DRIVER != null) {
            Database.connect(url, user = username, password = password, driver = JDBC_DRIVER)
        } else {
            Database.connect(url, user = username, password = password)
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

        schedulePendingTicketsClose()

        // Cancel closing a ticket if the owner or an extra user says something
        event<MessageCreateEvent> {
            check { isNotBot() }
            check {
                val channelId = channelIdFor(event)!!.toLong()
                if (channelId in ticketChannelIds) {
                    val ticketId = ticketChannelIds[channelId]!!
                    passIf(ticketId in ticketsPendingClose)
                } else {
                    fail()
                }
            }

            action {
                val channelId = channelIdFor(event)!!.toLong()
                val ticketId = ticketChannelIds[channelId]!!
                val userId = userFor(event)!!.id.value.toLong()

                // For some reason the check above doesn't work properly, so we check it again
                if (ticketId !in ticketsPendingClose || ticketId !in closeTicketTasks) {
                    return@action
                }

                // Get the ticket
                var ticket: ResultRow? = null
                transaction {
                    ticket = Tickets.select { Tickets.globalTicketId eq ticketId }.first()
                }

                // Get the ticket info
                val ownerId = ticket!![Tickets.ownerId]
                val extraUserList = ticket!![Tickets.extraUsers]
                val extraUsers = if (extraUserList.isNotBlank()) {
                    extraUserList.split(",").map { it.toLong() }
                } else {
                    emptyList()
                }

                if (userId == ownerId || userId in extraUsers) {
                    // Cancel task
                    val task = closeTicketTasks[ticketId]!!
                    task.cancel()

                    // Remove task and ticket from cache
                    closeTicketTasks.remove(ticketId)
                    ticketsPendingClose.remove(ticketId)

                    // Update database
                    transaction {
                        Tickets.update({ Tickets.globalTicketId eq ticketId }) {
                            it[closeTime] = null
                        }
                    }

                    // Send message
                    val channel = event.message.channel
                    channel.createMessage {
                        embed {
                            color = DISCORD_YELLOW
                            title = "Ticket Closing Cancelled"
                            description = "Ticket closing cancelled by ${userFor(event)!!.mention}"
                        }
                    }
                }
            }
        }

        chatCommand(::SetupTicketsArguments) {
            name = "setuptickets"
            description = "Sets up the ticket system with a new ticket config"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                createConfig(message, arguments)
            }
        }

        chatCommand({ GenericTicketConfigArguments(this) }) {
            name = "deleteticketconfig"
            description = "Deletes a ticket config"

            check { inMainGuild() }
            check { isAdministrator() }

            action {
                val msg = message
                var configName = ""
                var tickets = 0
                transaction {
                    configName =
                        TicketConfigs.select { TicketConfigs.id eq arguments.configId }.first()[TicketConfigs.name]
                    tickets = TicketConfigs.select { TicketConfigs.id eq arguments.configId }.count().toInt()
                }

                val displayName = getTicketConfigDisplayName(configName, arguments.configId)
                val response = msg.respond {
                    content = """
                        This will delete the ticket config $displayName and $tickets ticket(s).
                        Are you sure you want to do this? Press the button below to confirm.
                    """.trimIndent()
                }

                // For some reason adding the button directly in the message creation gives an Invalid Permissions error
                response.edit {
                    components {
                        ephemeralButton {
                            emoji(EMOTE_WARNING)

                            label = "Delete Ticket Config $displayName"
                            style = ButtonStyle.Danger

                            action {
                                deleteConfig(arguments.configId, msg)
                            }
                        }
                    }
                }
            }
        }

        chatCommand {
            name = "listticketconfigs"
            description = "List all ticket configs"

            check { inMainGuild() }
            check { isModerator() }

            action {
                val configs = mutableMapOf<Int, String>()
                transaction {
                    TicketConfigs.selectAll().forEach {
                        configs[it[TicketConfigs.id].value] = it[TicketConfigs.name]
                    }
                }

                val chunked = configs.map { (id, name) ->
                    if (name.isNotBlank()) {
                        "`$id`: '$name'"
                    } else {
                        "`$id`"
                    }
                }.chunked(10)

                paginator {
                    for (list in chunked) {
                        page {
                            description = list.joinToString("\n")
                        }
                    }
                }
            }
        }

        chatCommand({ GenericTicketConfigArguments(this) }) {
            name = "fixticketconfig"
            description = "Fix permissions for a ticket config"

            check { inMainGuild() }
            check { isModerator() }

            action {
                val config = TicketConfigs.select { TicketConfigs.id eq arguments.configId }.first()
                val categoryId = config[TicketConfigs.ticketCategoryId]
                val closedCategoryId = config[TicketConfigs.closedTicketCategoryId]
                val roleList = config[TicketConfigs.roles]
                val roles = roleList.split(",").map { it.toLong() }

                val guild = mainGuild(event.kord)!!
                val category = guild.getChannel(Snowflake(categoryId)) as Category
                val closedCategory = guild.getChannel(Snowflake(closedCategoryId)) as Category

                setupTicketCategoriesPermissions(category, closedCategory, roles.map { Snowflake(it) })
                val displayName = getTicketConfigDisplayName(config[TicketConfigs.name], arguments.configId)
                message.respond {
                    content = "Permissions for ticket config $displayName have been fixed"
                }
            }
        }

        chatCommand({ FixTicketArguments(this) }) {
            name = "fixticket"
            description = "Fix permissions for a ticket"

            check { inMainGuild() }
            check { isModerator() }

            action {
                val ticketChannel: TextChannel
                val allowedUsers: List<Snowflake>

                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketChannelId = Snowflake(ticket[Tickets.channelId])
                val closed = ticket[Tickets.closed]
                val configId = ticket[Tickets.ticketConfigId]
                val allowedUsersList = mutableListOf<String>()
                allowedUsersList.add(ticket[Tickets.ownerId].toString())
                val extraUserList = ticket[Tickets.extraUsers]
                if (extraUserList.isNotBlank()) {
                    allowedUsersList.addAll(extraUserList.split(","))
                }

                // Get the config
                var config: ResultRow? = null
                transaction {
                    config = TicketConfigs.select { TicketConfigs.id eq configId }.first()
                }

                // Get the info from the config
                val roleList = config!![TicketConfigs.roles]
                val roles = roleList.split(",").map { Snowflake(it.toLong()) }

                val guild = mainGuild(event.kord)!!
                ticketChannel = guild.getChannel(ticketChannelId) as TextChannel
                allowedUsers = allowedUsersList.map { Snowflake(it) }

                // Update permissions
                val everyoneRole = guild.getEveryoneRole()
                ticketChannel.edit {
                    val overwrites = permissionOverwrites ?: mutableSetOf()
                    for (user in allowedUsers) {
                        overwrites.add(memberOverwrite(user) {
                            allowed = TICKET_PER_USER_ALLOWED_PERMISSIONS
                        })
                    }

                    // Category permissions
                    val rolePermissions = if (closed) {
                        CLOSED_TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                    } else {
                        TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                    }
                    val everyonePermissions = if (closed) {
                        CLOSED_TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
                    } else {
                        TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
                    }

                    for (role in roles) {
                        overwrites.add(roleOverwrite(role) {
                            allowed = rolePermissions
                        })
                    }
                    overwrites.add(roleOverwrite(everyoneRole.id) {
                        allowed = everyonePermissions
                    })

                    permissionOverwrites = overwrites
                }

                message.respond {
                    content = "Permissions for ticket channel ${ticketChannel.mention} have been fixed"
                }
            }
        }

        chatCommand({ AddUserToTicketArguments(this) }) {
            name = "addusertoticket"
            description = "Add a user or users to a ticket"

            check { inMainGuild() }
            check { isModerator() }

            action {
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketId = ticket[Tickets.globalTicketId]
                val channelId = ticket[Tickets.channelId]
                val currentExtraUserList = ticket[Tickets.extraUsers]
                val currentExtraUsers = if (currentExtraUserList.isNotBlank()) {
                    currentExtraUserList.split(",").map { it.toLong() }
                } else {
                    emptyList()
                }

                // Check the amount of users is allowed
                val newExtraUsers = arguments.users.map { it.id.value.toLong() }.filter { it !in currentExtraUsers }
                val extraUsers = currentExtraUsers + newExtraUsers
                if (extraUsers.size > MAX_EXTRA_USERS_PER_TICKET) {
                    message.respond {
                        content = "There can only be a maximum of $MAX_EXTRA_USERS_PER_TICKET extra users in a ticket"
                    }
                    return@action
                }

                val extraUserList = extraUsers.joinToString(",")

                // Update the ticket channel
                val channel = mainGuild(event.kord)!!.getChannel(Snowflake(channelId)) as TextChannel
                channel.edit {
                    val overwrites = permissionOverwrites ?: mutableSetOf()
                    for (user in newExtraUsers) {
                        overwrites.add(memberOverwrite(Snowflake(user)) {
                            allowed = TICKET_PER_USER_ALLOWED_PERMISSIONS
                        })
                    }
                    permissionOverwrites = overwrites
                }

                // Update the ticket data
                transaction {
                    Tickets.update({ Tickets.globalTicketId eq ticketId }) {
                        it[Tickets.extraUsers] = extraUserList
                    }
                }

                // Send notification message
                channel.createMessage {
                    content = newExtraUsers.joinToString(", ") { "<@$it>" }

                    embed {
                        description = """
                            You have been added to ticket ${channel.mention}
                            A staff member will explain the situation shortly
                        """.trimIndent()
                    }
                }
            }
        }

        chatCommand({ RemoveUserFromTicketArguments(this) }) {
            name = "removeuserfromticket"
            description = "Remove a user or users from a ticket"

            check { inMainGuild() }
            check { isModerator() }

            action {
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketId = ticket[Tickets.globalTicketId]
                val channelId = ticket[Tickets.channelId]
                val currentExtraUserList = ticket[Tickets.extraUsers]
                val currentExtraUsers = if (currentExtraUserList.isNotBlank()) {
                    currentExtraUserList.split(",").map { it.toLong() }
                } else {
                    emptyList()
                }

                // Create the list of users to remove
                val usersToRemove = arguments.users.map { it.id.value.toLong() }.filter { it in currentExtraUsers }
                val extraUsers = currentExtraUsers.filter { it !in usersToRemove }
                val extraUserList = extraUsers.joinToString(",")

                // Update the ticket channel
                val channel = mainGuild(event.kord)!!.getChannel(Snowflake(channelId)) as TextChannel
                channel.edit {
                    val overwrites = permissionOverwrites ?: mutableSetOf()
                    for (user in usersToRemove) {
                        overwrites.removeIf { it.id.value.toLong() == user }
                    }
                    permissionOverwrites = overwrites
                }

                // Update the ticket data
                transaction {
                    Tickets.update({ Tickets.globalTicketId eq ticketId }) {
                        it[Tickets.extraUsers] = extraUserList
                    }
                }

                // Send response
                message.respond {
                    content = "Removed ${usersToRemove.joinToString(", ") { "<@$it>" }} from ticket ${channel.mention}"
                    allowedMentions = AllowedMentionsBuilder()
                }
            }
        }

        chatCommand({ TransferTicketArguments(this) }) {
            name = "transferticket"
            description = "Transfer ownership of a ticket to another user"

            check { inMainGuild() }
            check { isModerator() }

            action {
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketId = ticket[Tickets.globalTicketId]
                val currentOwnerId = ticket[Tickets.ownerId]
                val extraUserList = ticket[Tickets.extraUsers]
                val extraUsers = if (extraUserList.isNotBlank()) {
                    extraUserList.split(",").map { it.toLong() }
                } else {
                    emptyList()
                }

                // Check if the user is in the ticket
                val user = arguments.user
                val userId = user.id.value.toLong()
                if (userId !in extraUsers) {
                    message.respond {
                        content =
                            "User ${user.mention} is not in ticket ${channel.mention}. " +
                                    "Please add them first with `addusertoticket`"
                        allowedMentions = AllowedMentionsBuilder()
                    }
                    return@action
                }

                // Ticket owners don't have extra discord permissions, so we don't need to update them

                // Update the ticket data
                val newExtraUsers = mutableListOf<Long>()
                newExtraUsers.addAll(extraUsers.filter { it != userId })
                newExtraUsers.add(currentOwnerId)
                val newExtraUserList = newExtraUsers.joinToString(",")
                transaction {
                    Tickets.update({ Tickets.globalTicketId eq ticketId }) {
                        it[Tickets.extraUsers] = newExtraUserList
                        it[ownerId] = userId
                    }
                }
                // Update cache
                ticketOwners.replace(ticketId, userId)

                // Send response
                message.respond {
                    content = "Transferred ticket ${channel.mention} to ${user.mention}"
                }
            }
        }

        chatCommand({ CloseTicketArguments(this) }) {
            name = "closeticket"
            description = "Close a ticket"

            check { inMainGuild() }
            check { canCloseTicket() }

            action {
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketId = ticket[Tickets.globalTicketId]
                val channelId = ticket[Tickets.channelId]

                if (arguments.delay != null) {
                    val now = Clock.System.now()
                    val systemTZ = TimeZone.currentSystemDefault()
                    val closeTime = now.plus(arguments.delay!!, systemTZ)
                    scheduleTicketClose(ticketId, closeTime, now)
                    // Update cache
                    ticketsPendingClose[ticketId] = closeTime

                    // Send embed
                    val guild = mainGuild(event.kord)!!
                    val channel = guild.getChannel(Snowflake(channelId)) as TextChannel
                    val delayDisplay = arguments.delay!!.toReadableString()
                    channel.createMessage {
                        embed {
                            title = "Ticket will close"
                            description = "This ticket will be closed in $delayDisplay."
                            color = DISCORD_YELLOW
                        }
                    }

                    // Update the ticket data
                    transaction {
                        Tickets.update({ Tickets.globalTicketId eq ticketId }) {
                            it[Tickets.closeTime] = closeTime.toString()
                        }
                    }
                    return@action
                }

                // Close the ticket
                doCloseTicket(ticketId)
            }
        }

        chatCommand({ RenameTicketArguments(this) }) {
            name = "renameticket"
            description = "Rename a ticket"

            check { inMainGuild() }
            check { isModerator() }

            action {
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)

                // Get the info from the ticket
                val ticketId = ticket[Tickets.ticketId]
                val channelId = ticket[Tickets.channelId]

                // Update the channel name
                val guild = mainGuild(event.kord)!!
                val channel = guild.getChannel(Snowflake(channelId)) as TextChannel
                val baseName = arguments.name
                val id = ticketId.toString().padStart(4, '0')
                channel.edit {
                    name = "$baseName-$id"
                }

                message.respond {
                    content = "Renamed the ticket"
                }
            }
        }

        // TODO: Repoen ticket command
        // TODO: Delete ticket command
        // TODO: Claim ticket command
        // TODO: Unclaim ticket command
        // TODO: Transfer ticket claim command
        // TODO: Assign ticket command
    }

    private fun setupDb() {
        // Create the tables if they don't exist
        SchemaUtils.createMissingTablesAndColumns(TicketConfigs, Tickets)
    }

    private fun updateConfigs() {
        val allConfigs = mutableListOf<ResultRow>()
        allConfigs.addAll(TicketConfigs.selectAll())
        ticketConfigIds.clear()
        ticketConfigIds.addAll(allConfigs.map { it[TicketConfigs.id].value })
    }

    private fun updateTickets() {
        val allTickets = mutableListOf<ResultRow>()
        allTickets.addAll(Tickets.selectAll())

        val now = Clock.System.now().toEpochMilliseconds()
        ticketsPendingClose.clear()
        ticketsPendingClose.putAll(Tickets.select { Tickets.closeTime.isNotNull() and (Tickets.closed eq false) }
            .filter { Instant.parse(it[Tickets.closeTime]!!).toEpochMilliseconds() >= now }
            .associate { it[Tickets.globalTicketId] to Instant.parse(it[Tickets.closeTime]!!) })

        ticketChannelIds.clear()
        ticketChannelIds.putAll(allTickets.associate { it[Tickets.channelId] to it[Tickets.globalTicketId] })
        ticketOwners.clear()
        ticketOwners.putAll(allTickets.associate { it[Tickets.globalTicketId] to it[Tickets.ownerId] })
    }

    private suspend fun setupInteractions() {
        val guild = mainGuild(kord)!!
        val configs = mutableListOf<ResultRow>()
        val openTickets = mutableListOf<ResultRow>()
        transaction {
            configs.addAll(TicketConfigs.selectAll())
            openTickets.addAll(Tickets.select { Tickets.closed eq false })
        }

        configs.forEach {
            val channel = guild.getChannel(Snowflake(it[TicketConfigs.messageChannelId])) as TextChannel
            val message = channel.getMessageOrNull(Snowflake(it[TicketConfigs.messageId]))!!

            message.edit {
                setupTicketConfigButtons(it[TicketConfigs.id].value)
            }
        }
        openTickets.forEach {
            val channel = guild.getChannel(Snowflake(it[Tickets.channelId])) as TextChannel
            val message = channel.getMessageOrNull(Snowflake(it[Tickets.botMsgId]!!))!!

            message.setupTicketButtons(it[Tickets.globalTicketId])
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
        setupTicketCategoriesPermissions(category, closedCategory, arguments.roles.map { it.id })

        // Send message if needed
        val msgChannel = arguments.messageChannel as TextChannel
        val msg = arguments.message ?: run {
            msgChannel.createMessage {
                embed {
                    description = """
                        Press the button below to open a new ticket
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
            // Update database
            transaction {
                id = TicketConfigs.insertAndGetId {
                    it[ticketCategoryId] = categoryId
                    it[closedTicketCategoryId] = closedCategoryId
                    it[messageId] = msgId
                    it[messageChannelId] = msgChannelId
                    it[roles] = rolesStr
                    it[name] = configName

                    if (arguments.ticketsBaseName != null) {
                        it[ticketsBaseName] = arguments.ticketsBaseName!!
                    }
                }.value
            }

            // Update cache
            ticketConfigIds.add(id)

            LOGGER.debug { "Inserted ticket config $id" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to insert the ticket config in the database" }
            throw DiscordRelayedException("Failed to insert the ticket config in the database")
        }

        // Add button to message
        msg.edit {
            setupTicketConfigButtons(id)
        }

        // Send message to the user
        LOGGER.info { "Created ticket config${if (configName.isNotBlank()) " '$configName'" else ""} id $id" }
        message.respond("Created ticket config${if (configName.isNotBlank()) " '$configName'" else ""} id $id")
    }

    private suspend fun MessageModifyBuilder.setupTicketConfigButtons(id: Int) {
        components {
            publicButton {
                emoji(EMOTE_TICKET)
                label = "Open Ticket"
                this.id = "$id/CreateTicket"

                action {
                    val user = user.asUser()
                    LOGGER.debug {
                        "Opening ticket in config $id for user ${user.username}#${user.discriminator} (${user.id})"
                    }
                    createTicket(user, id, this)
                }
            }
        }
    }

    private suspend fun setupTicketCategoriesPermissions(
        category: Category,
        closedCategory: Category,
        roles: List<Snowflake>
    ) {
        val guild = mainGuild(kord)!!
        val everyoneRole = guild.getEveryoneRole()
        category.edit {
            for (role in roles) {
                addRoleOverwrite(role) {
                    allowed = TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                }
            }
            addRoleOverwrite(everyoneRole.id) {
                denied = TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
            }
        }
        closedCategory.edit {
            for (role in roles) {
                addRoleOverwrite(role) {
                    allowed = CLOSED_TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                }
            }
            addRoleOverwrite(everyoneRole.id) {
                denied = CLOSED_TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
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

    private suspend fun createTicket(owner: User, configId: Int, context: PublicInteractionContext) {
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
            LOGGER.debug {
                "User ${owner.username}#${owner.discriminator} ($userId) " +
                        "has too many open tickets (${userOpenTickets.size})"
            }
            context.respondEphemeral {
                content = "You can only have $MAX_OPEN_TICKETS_PER_USER open tickets at a time"
            }
            return
        }

        val time = System.currentTimeMillis()
        if (lastTicketCreateTime != -1L && time - lastTicketCreateTime <= MIN_TICKET_CREATE_DELAY) {
            LOGGER.debug {
                "User ${owner.username}#${owner.discriminator} ($userId) " +
                        "has created a ticket in the last $MIN_TICKET_CREATE_DELAY_MINUTES minutes"
            }
            context.respondEphemeral {
                content = "You have opened a ticket in the last $MIN_TICKET_CREATE_DELAY_MINUTES minutes. " +
                        "Please wait before opening another ticket."
            }
            return
        }

        val id = config!![TicketConfigs.ticketCount]
        val categoryId = config!![TicketConfigs.ticketCategoryId]
        val roleList = config!![TicketConfigs.roles]
        val roles = roleList.split(",").map { Snowflake(it.toLong()) }
        val baseName = config!![TicketConfigs.ticketsBaseName]
        val guild = mainGuild(kord)!!
        val category = guild.getChannel(Snowflake(categoryId)) as Category

        val channel = category.createTextChannel("$baseName-${id.toString().padStart(4, '0')}")
        val chnlId = channel.id.value.toLong()

        // Insert to database
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

        // Update cache
        ticketChannelIds[channel.id.value.toLong()] = globalTicketId
        ticketOwners[globalTicketId] = userId

        val everyoneRole = guild.getEveryoneRole()
        channel.edit {
            val overwrites = permissionOverwrites ?: mutableSetOf()
            overwrites.add(memberOverwrite(owner.id) {
                allowed = TICKET_PER_USER_ALLOWED_PERMISSIONS
            })

            // Add the permissions from the category, apparently they are not inherited automatically
            for (role in roles) {
                overwrites.add(roleOverwrite(role) {
                    allowed = TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                })
            }
            overwrites.add(roleOverwrite(everyoneRole.id) {
                denied = TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
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
        botMsg.setupTicketButtons(globalTicketId)
        val msgId = botMsg.id.value.toLong()

        // Add bot message id to the ticket in the database
        transaction {
            Tickets.update({ Tickets.ownerId eq userId and (Tickets.globalTicketId eq globalTicketId) }) {
                it[botMsgId] = msgId
            }
        }

        // Send message to the user
        context.respondEphemeral {
            content = "Your ticket has been created in ${channel.mention}"
        }
    }

    private suspend fun doCloseTicket(globalTicketId: Int, closeTime: Instant = Clock.System.now()) {
        LOGGER.debug { "Closing ticket $globalTicketId" }

        // Get the ticket
        var ticket: ResultRow? = null
        transaction {
            ticket = Tickets.select { Tickets.globalTicketId eq globalTicketId }.first()
        }

        // Get ticket info
        val channelId = ticket!![Tickets.channelId]
        val extraUserList = ticket!![Tickets.extraUsers]
        val ownerId = ticket!![Tickets.ownerId].toLong()
        val configId = ticket!![Tickets.ticketConfigId]
        val botMsgId = ticket!![Tickets.botMsgId]

        // Get the ticket config
        var config: ResultRow? = null
        transaction {
            config = TicketConfigs.select { TicketConfigs.id eq configId }.first()
        }

        // Get ticket config info
        val closedCategoryId = config!![TicketConfigs.closedTicketCategoryId]
        val roleList = config!![TicketConfigs.roles]
        val roles = roleList.split(",").map { Snowflake(it.toLong()) }

        // Send close message
        val guild = mainGuild(kord)!!
        val channel = guild.getChannel(Snowflake(channelId)) as TextChannel
        channel.createMessage {
            content = "The ticket has been closed"
        }

        // Remove user permissions
        val everyoneRole = guild.getEveryoneRole()
        val extraUsers = if (extraUserList.isNotBlank()) {
            extraUserList.split(",").map { Snowflake(it.toLong()) }.toMutableList()
        } else {
            mutableListOf()
        }
        extraUsers.add(Snowflake(ownerId))

        channel.edit {
            val overwrites = permissionOverwrites ?: mutableSetOf()
            overwrites.removeIf { it.id in extraUsers }

            // Add the permissions from the category, apparently they are not inherited automatically
            for (role in roles) {
                overwrites.add(roleOverwrite(role) {
                    allowed = CLOSED_TICKET_CATEGORY_ROLE_ALLOWED_PERMISSIONS
                })
            }
            overwrites.add(roleOverwrite(everyoneRole.id) {
                denied = CLOSED_TICKET_CATEGORY_EVERYONE_DENIED_PERMISSIONS
            })

            permissionOverwrites = overwrites
        }

        // Change category
        channel.edit {
            parentId = Snowflake(closedCategoryId)
        }

        // Send close message to owner
        val owner = guild.getMember(Snowflake(ownerId))
        val dms = owner.getDmChannel()
        dms.createMessage {
            content = "Your ticket #${channel.name} has been closed"
        }

        // Remove buttons
        val botMsg = channel.getMessage(Snowflake(botMsgId!!))
        botMsg.edit {
            components {
                removeAll()
            }
        }

        // Update database
        transaction {
            Tickets.update({ Tickets.globalTicketId eq globalTicketId }) {
                it[Tickets.closeTime] = closeTime.toString()
                it[closed] = true
            }
        }

        // Update cache
        ticketsPendingClose.remove(globalTicketId)
        closeTicketTasks.remove(globalTicketId)
    }

    private fun schedulePendingTicketsClose() {
        val now = Clock.System.now()
        for ((ticketId, instant) in ticketsPendingClose) {
            scheduleTicketClose(ticketId, instant, now)
        }
    }

    private fun scheduleTicketClose(ticketId: Int, instant: Instant, now: Instant) {
        var scheduleTime = instant - now
        if (scheduleTime.inWholeSeconds < MIN_SCHEDULE_TICKET_CLOSE_DELAY) {
            scheduleTime = Duration.seconds(MIN_SCHEDULE_TICKET_CLOSE_DELAY)
        }

        LOGGER.debug { "Scheduling ticket $ticketId to close in $scheduleTime" }
        val task = scheduler.schedule(scheduleTime) {
            doCloseTicket(ticketId)
        }
        closeTicketTasks[ticketId] = task
    }

    private suspend fun Message.setupTicketButtons(globalTicketId: Int) {
        LOGGER.debug { "Adding ticket buttons for ticket $globalTicketId in message $id" }
        edit {
            components {
                ephemeralButton {
                    emoji(EMOTE_LOCK)

                    label = "Close ticket"
                    style = ButtonStyle.Danger
                    id = "$globalTicketId/CloseTicket"

                    check { canCloseTicket() }

                    action {
                        this@setupTicketButtons.addCloseTicketConfirmationButtons(globalTicketId)
                    }
                    initialResponse {
                        content = "Are you sure you want to close this ticket? Press the confirm button to do it."
                    }
                }
            }
        }
    }

    private suspend fun Message.addCloseTicketConfirmationButtons(globalTicketId: Int) {
        edit {
            components {
                ephemeralButton {
                    emoji(EMOTE_WARNING)
                    label = "Confirm"
                    style = ButtonStyle.Danger
                    this.id = "$globalTicketId/CloseTicketConfirm"

                    check { canCloseTicket() }

                    action {
                        doCloseTicket(globalTicketId)
                    }
                }

                ephemeralButton {
                    label = "Cancel"
                    this.id = "$globalTicketId/CloseTicketCancel"

                    check { canCloseTicket() }

                    action {
                        // Reset buttons
                        this@addCloseTicketConfirmationButtons.setupTicketButtons(globalTicketId)
                    }
                }
            }
        }
    }

    private fun ChatCommandContext<*>.getTicket(
        ticketId: Int?,
        isGlobalId: Boolean?,
        configId: Int?
    ): ResultRow {
        if (ticketId != null) {
            // Get info using the ticket ID
            var result: ResultRow? = null
            transaction {
                val query: Query
                if (isGlobalId != null) {
                    query = if (isGlobalId == true) {
                        Tickets.select { Tickets.globalTicketId eq ticketId }
                    } else if (configId != null) {
                        Tickets.select {
                            Tickets.ticketConfigId eq configId and (Tickets.ticketId eq ticketId)
                        }
                    } else if (ticketConfigIds.size == 1) {
                        // Ticket ids are unique when there is only one config
                        Tickets.select { Tickets.ticketId eq ticketId }
                    } else {
                        throw DiscordRelayedException(
                            "You must specify a ticket config id or " +
                                    "a global ticket id if there are multiple ticket configs"
                        )
                    }
                } else {
                    query = if (ticketConfigIds.size == 1) {
                        // Ticket ids are unique when there is only one config
                        Tickets.select { Tickets.ticketId eq ticketId }
                    } else {
                        throw DiscordRelayedException(
                            "You must specify a ticket config id or " +
                                    "a global ticket id if there are multiple ticket configs"
                        )
                    }
                }
                result = query.first()
            }

            return result!!
        } else {
            // Get info from ticket corresponding to channel
            val channelId = channel.id.value.toLong()
            var ticket: ResultRow? = null
            transaction {
                ticket = Tickets.select { Tickets.channelId eq channelId }.firstOrNull()
                    ?: throw DiscordRelayedException(
                        "You must run this command in a ticket channel or specify a ticket id"
                    )
            }

            return ticket!!
        }
    }

    private suspend fun CheckContext<*>.canCloseTicket() {
        if (!passed) {
            return
        }

        // Moderators can close tickets from anywhere
        val channelId = channelIdFor(event)!!.toLong()
        if (channelId !in ticketChannelIds.keys) {
            isModerator()
        } else {
            // Only ticket owners can close tickets, from its channel
            val userId = userFor(event)!!.id.value.toLong()
            val globalTicketId = ticketChannelIds[channelId]!!
            if (ticketOwners[globalTicketId] != userId) {
                fail("You must be owner of the ticket to close it")
            }
        }
    }

    private fun getTicketConfigDisplayName(configName: String, configId: Int) =
        if (configName.isNotBlank()) "'$configName'" else "ID $configId"
}
