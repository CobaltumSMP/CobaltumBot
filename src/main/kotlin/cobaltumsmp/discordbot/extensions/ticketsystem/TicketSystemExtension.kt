@file:OptIn(ExperimentalTime::class)

@file:Suppress("MagicNumber")

package cobaltumsmp.discordbot.extensions.ticketsystem

import cobaltumsmp.discordbot.database.Database
import cobaltumsmp.discordbot.database.entities.Ticket
import cobaltumsmp.discordbot.database.entities.TicketConfig
import cobaltumsmp.discordbot.database.tables.TicketConfigs
import cobaltumsmp.discordbot.database.tables.Tickets
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
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

internal const val MIN_TICKET_CREATE_DELAY_MINUTES = 0.5
internal const val MIN_TICKET_CREATE_DELAY = MIN_TICKET_CREATE_DELAY_MINUTES * 60
internal const val MAX_OPEN_TICKETS_PER_USER = 3
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

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.ticketsystem")

class TicketSystemExtension : Extension() {
    override val name: String = "Ticket System"

    private val scheduler: Scheduler = Scheduler()
    private val closeTicketTasks = mutableMapOf<Ticket, Task>()

    // Ticket configs cache
    private val ticketConfigs = mutableListOf<TicketConfig>()

    // Tickets cache
    private val tickets = mutableListOf<Ticket>()
    private val ticketsByChannelId = mutableMapOf<Snowflake, Ticket>()
    private val ticketOwnerIds = mutableMapOf<Ticket, Snowflake>()
    private val ticketsWaitingClose = mutableMapOf<Ticket, Instant>()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setup() {
        LOGGER.info { "Setting up Ticket System" }

        Database.connect()

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
                val channelId = Snowflake(channelIdFor(event)!!)
                if (channelId in ticketsByChannelId) {
                    val ticket = ticketsByChannelId[channelId]!!
                    passIf(ticket in ticketsWaitingClose)
                } else {
                    fail()
                }
            }

            action {
                val channelId = Snowflake(channelIdFor(event)!!)
                val ticket = ticketsByChannelId[channelId]!!
                val userId = userFor(event)!!.id

                // For some reason the check above doesn't work properly, so we check it again
                if (ticket !in ticketsWaitingClose || ticket !in closeTicketTasks) {
                    return@action
                }

                // Get the ticket info
                val ownerId = ticket.ownerId
                val extraUsers = ticket.extraUsers

                // Only messages by the owner or an extra user should cancel closing the ticket
                if (userId == ownerId || userId in extraUsers) {
                    // Cancel task
                    val task = closeTicketTasks[ticket]!!
                    task.cancel()

                    // Remove task and ticket from cache
                    closeTicketTasks.remove(ticket)
                    ticketsWaitingClose.remove(ticket)

                    // Update database
                    transaction {
                        ticket.closeTime = null
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

        // region commands
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
                val config = ticketConfigs.find { it.id.value == arguments.configId }
                val tickets = config!!.tickets

                val displayName = config.displayName()
                val response = msg.respond {
                    content = """
                        This will delete the ticket config $displayName and ${tickets.count()} ticket(s).
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
                configs.putAll(ticketConfigs.associate { it.id.value to it.name })

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
                val config = ticketConfigs.find { it.id.value == arguments.configId }!!
                val categoryId = config.ticketCategoryId
                val closedCategoryId = config.closedTicketCategoryId
                val roles = config.roles

                val guild = mainGuild(event.kord)!!
                val category = guild.getChannel(categoryId) as Category
                val closedCategory = guild.getChannel(closedCategoryId) as Category

                setupTicketCategoriesPermissions(category, closedCategory, roles)
                val displayName = config.displayName()
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
                // Get the ticket
                val ticket = getTicket(arguments.ticketId, arguments.isGlobalId, arguments.configId)
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val ticketChannelId = ticket.channelId
                val closed = ticket.closed
                val allowedUsers = mutableListOf<Snowflake>()
                allowedUsers.add(ticket.ownerId)
                allowedUsers.addAll(ticket.extraUsers)

                // Get the config
                val config = transaction { ticket.config } // Can only be done inside a transaction

                // Get the info from the config
                val roles = config.roles

                val guild = mainGuild(event.kord)!!
                val ticketChannel = guild.getChannel(ticketChannelId) as TextChannel

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
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val channelId = ticket.channelId
                val currentExtraUsers = ticket.extraUsers

                // Check the amount of users is allowed
                val newExtraUsers = arguments.users.map { it.id }.filter { it !in currentExtraUsers }
                val extraUsers = currentExtraUsers + newExtraUsers
                if (extraUsers.size > Ticket.MAX_EXTRA_USERS) {
                    message.respond {
                        content = "There can only be a maximum of ${Ticket.MAX_EXTRA_USERS} extra users in a ticket"
                    }
                    return@action
                }

                // Update the ticket channel
                val channel = mainGuild(event.kord)!!.getChannel(channelId) as TextChannel
                channel.edit {
                    val overwrites = permissionOverwrites ?: mutableSetOf()
                    for (user in newExtraUsers) {
                        overwrites.add(memberOverwrite(user) {
                            allowed = TICKET_PER_USER_ALLOWED_PERMISSIONS
                        })
                    }
                    permissionOverwrites = overwrites
                }

                // Update database
                transaction {
                    ticket.extraUsers = extraUsers
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
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val channelId = ticket.channelId
                val currentExtraUsers = ticket.extraUsers

                // Create the list of users to remove
                val usersToRemove = arguments.users.map { it.id }.filter { it in currentExtraUsers }
                val extraUsers = currentExtraUsers.filter { it !in usersToRemove }

                // Update the ticket channel
                val channel = mainGuild(event.kord)!!.getChannel(channelId) as TextChannel
                channel.edit {
                    val overwrites = permissionOverwrites ?: mutableSetOf()
                    for (user in usersToRemove) {
                        overwrites.removeIf { it.id == user }
                    }
                    permissionOverwrites = overwrites
                }

                // Update database
                transaction {
                    ticket.extraUsers = extraUsers
                }

                // Send response
                val removedUsers = usersToRemove.joinToString(", ") { "<@${it.value}>" }
                message.respond {
                    content = "Removed $removedUsers from ticket ${channel.mention}"
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
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val currentOwnerId = ticket.ownerId
                val extraUsers = ticket.extraUsers

                // Check if the user is in the ticket
                val user = arguments.user
                val userId = user.id
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

                val newExtraUsers = mutableListOf<Snowflake>()
                newExtraUsers.addAll(extraUsers.filter { it != userId })
                newExtraUsers.add(currentOwnerId)

                // Update database
                transaction {
                    ticket.extraUsers = newExtraUsers.toList()
                    ticket.ownerId = userId
                }
                // Update cache
                ticketOwnerIds.replace(ticket, userId)

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
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val channelId = ticket.channelId

                if (arguments.delay != null) {
                    val now = Clock.System.now()
                    val systemTZ = TimeZone.currentSystemDefault()
                    val closeTime = now.plus(arguments.delay!!, systemTZ)
                    scheduleTicketClose(ticket, closeTime, now)
                    // Update cache
                    ticketsWaitingClose[ticket] = closeTime

                    // Send embed
                    val guild = mainGuild(event.kord)!!
                    val channel = guild.getChannel(channelId) as TextChannel
                    val delayDisplay = arguments.delay!!.toReadableString()
                    channel.createMessage {
                        embed {
                            title = "Ticket will close"
                            description = "This ticket will be closed in $delayDisplay."
                            color = DISCORD_YELLOW
                        }
                    }

                    // Update database
                    transaction {
                        ticket.closeTime = closeTime
                    }
                    return@action
                }

                // Close the ticket
                doCloseTicket(ticket.id.value)
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
                    ?: return@action // Ticket not found

                // Get the info from the ticket
                val ticketId = ticket.id
                val channelId = ticket.channelId

                // Update the channel name
                val guild = mainGuild(event.kord)!!
                val channel = guild.getChannel(channelId) as TextChannel
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
        // endregion
    }

    private fun setupDb() {
        // Create the tables if they don't exist
        SchemaUtils.createMissingTablesAndColumns(TicketConfigs, Tickets)
    }

    private fun updateConfigs() {
        // Get the configs
        ticketConfigs.clear()
        ticketConfigs.addAll(TicketConfig.all())
    }

    private fun updateTickets() {
        // Get the tickets
        val allTickets = mutableListOf<Ticket>()
        allTickets.addAll(Ticket.all())
        tickets.clear()
        tickets.addAll(allTickets)

        // Update the tickets waiting to be closed
        val now = Clock.System.now()
        ticketsWaitingClose.clear()
        ticketsWaitingClose.putAll(allTickets
            .filter { !it.closed && it.closeTime != null && it.closeTime!! >= now }
            .associateWith { it.closeTime!! })

        // Update cache
        ticketsByChannelId.clear()
        ticketsByChannelId.putAll(allTickets.associateBy { it.channelId })
        ticketOwnerIds.clear()
        ticketOwnerIds.putAll(allTickets.associateWith { it.ownerId })
    }

    private suspend fun setupInteractions() {
        val guild = mainGuild(kord)!!

        // Setup config buttons
        ticketConfigs.forEach {
            val channel = guild.getChannel(it.messageChannelId) as TextChannel
            val message = channel.getMessageOrNull(it.messageId)!!

            message.edit {
                setupTicketConfigButtons(it.id.value)
            }
        }

        // Setup ticket buttons
        val openTickets = tickets.filter { !it.closed }
        openTickets.forEach {
            val channel = guild.getChannel(it.channelId) as TextChannel
            val message = channel.getMessageOrNull(it.botMsgId!!)!!

            message.setupTicketButtons(it.id.value)
        }
    }

    private suspend fun createConfig(message: Message, arguments: SetupTicketsArguments) {
        val categoryId = arguments.ticketCategory.id
        val closedCategoryId = arguments.closedTicketCategory.id
        val roleIds = arguments.roles.map { it.id }

        val guild = mainGuild(kord)!!
        val category = guild.getChannel(categoryId) as Category
        val closedCategory = guild.getChannel(closedCategoryId) as Category

        // Setup permissions for the roles
        setupTicketCategoriesPermissions(category, closedCategory, roleIds)

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

        val msgId = msg.id
        val msgChannelId = msg.channelId

        val configName = arguments.name ?: ""

        var ticketConfig: TicketConfig? = null

        try {
            // Update database
            transaction {
                ticketConfig = TicketConfig.new {
                    ticketCategoryId = categoryId
                    closedTicketCategoryId = closedCategoryId
                    messageId = msgId
                    messageChannelId = msgChannelId
                    roles = roleIds
                    name = configName

                    if (arguments.ticketsBaseName != null) {
                        ticketsBaseName = arguments.ticketsBaseName!!
                    }
                }
            }

            // Update cache
            ticketConfigs.add(ticketConfig!!)

            LOGGER.debug { "Inserted ticket config ${ticketConfig!!.displayName()}" }
        } catch (e: SQLException) {
            LOGGER.error(e) { "Failed to insert the ticket config in the database" }
            throw DiscordRelayedException("Failed to insert the ticket config in the database")
        }

        // Add button to message
        msg.edit {
            setupTicketConfigButtons(ticketConfig!!.id.value)
        }

        // Send message to the user
        LOGGER.info { "Created ticket config ${ticketConfig!!.displayName()}" }
        message.respond("Created ticket config ${ticketConfig!!.displayName()}")
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
                        "Opening ticket in config $id for user " +
                                "${user.username}#${user.discriminator} (${user.id.value})"
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

    private suspend fun deleteConfig(configId: Int, message: Message) {
        try {
            var config: TicketConfig? = null
            transaction {
                // Update database
                config = TicketConfig.findById(configId)
                config?.delete()
            }

            if (config == null) {
                return
            }

            ticketConfigs.remove(config)
            // TODO: Delete all tickets within this config
            LOGGER.info { "Deleted ticket config ${config!!.displayName()}" }
            message.respond {
                content = "Deleted the ticket config ${config!!.displayName()}"
            }
        } catch (e: SQLException) {
            LOGGER.error(e) { "Failed to delete the ticket config in the database" }
            throw DiscordRelayedException("Failed to delete the ticket config in the database")
        }
    }

    private suspend fun createTicket(owner: User, configId: Int, context: PublicInteractionContext) {
        val config = ticketConfigs.first { it.id.value == configId }
        val userId = owner.id
        val userTickets = tickets.filter { it.ownerId == userId }
        val userOpenTickets = userTickets.filter { !it.closed }
        val lastTicketCreateTime = userTickets.maxByOrNull { it.createTime }?.createTime ?: Instant.DISTANT_PAST

        if (userOpenTickets.size >= MAX_OPEN_TICKETS_PER_USER) {
            LOGGER.debug {
                "User ${owner.username}#${owner.discriminator} (${userId.value}) " +
                        "has too many open tickets (${userOpenTickets.size})"
            }
            context.respondEphemeral {
                content = "You can only have $MAX_OPEN_TICKETS_PER_USER open tickets at a time"
            }
            return
        }

        val time = Clock.System.now()
        val timeDelta = time - lastTicketCreateTime
        if (lastTicketCreateTime != Instant.DISTANT_PAST && timeDelta.inWholeSeconds <= MIN_TICKET_CREATE_DELAY) {
            LOGGER.debug {
                "User ${owner.username}#${owner.discriminator} (${userId.value}) " +
                        "has created a ticket in the last $MIN_TICKET_CREATE_DELAY_MINUTES minutes"
            }
            context.respondEphemeral {
                content = "You have opened a ticket in the last $MIN_TICKET_CREATE_DELAY_MINUTES minutes. " +
                        "Please wait before opening another ticket."
            }
            return
        }

        val id = config.ticketCount
        val categoryId = config.ticketCategoryId
        val roles = config.roles
        val baseName = config.ticketsBaseName
        val guild = mainGuild(kord)!!
        val category = guild.getChannel(categoryId) as Category

        val channel = category.createTextChannel("$baseName-${id.toString().padStart(4, '0')}")
        val chnlId = channel.id

        // Update database
        var ticket: Ticket? = null
        transaction {
            // Insert to database
            ticket = Ticket.new {
                ticketId = id
                channelId = chnlId
                createTime = time
                ownerId = userId
                this.config = config
            }

            // Increase ticket count
            config.ticketCount++
        }

        // Update cache
        tickets.add(ticket!!)
        ticketsByChannelId[chnlId] = ticket!!
        ticketOwnerIds[ticket!!] = userId

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

        // Send bot message in the ticket channel
        val botMsg = channel.createMessage {
            content = owner.mention
            embed {
                description = """
                    **Support will be with you shortly**
                    In the meantime, please provide as much information about your issue as possible

                    You can use the button below to close the ticket.
                """.trimIndent()
                color = DISCORD_GREEN
                footer {
                    text = "Ticket ID: ${ticket!!.id.value}"
                }
            }
        }
        botMsg.setupTicketButtons(ticket!!.id.value)

        // Add bot message id to the ticket in the database
        val msgId = botMsg.id
        transaction {
            ticket!!.botMsgId = msgId
        }

        // Send message to the user
        context.respondEphemeral {
            content = "Your ticket has been created in ${channel.mention}"
        }
    }

    private suspend fun doCloseTicket(globalTicketId: Int, closeTime: Instant = Clock.System.now()) {
        LOGGER.debug { "Closing ticket $globalTicketId" }

        // Get the ticket
        val ticket = tickets.find { it.id.value == globalTicketId }

        // Get ticket info
        val channelId = ticket!!.channelId
        val extraUsers = ticket.extraUsers
        val ownerId = ticket.ownerId
        val botMsgId = ticket.botMsgId

        // Get the config
        val config = transaction { ticket.config } // Can only be done in a transaction

        // Get ticket config info
        val closedCategoryId = config.closedTicketCategoryId
        val roles = config.roles

        // Send close message
        val guild = mainGuild(kord)!!
        val channel = guild.getChannel(channelId) as TextChannel
        channel.createMessage {
            content = "The ticket has been closed"
        }

        // Remove user permissions
        val everyoneRole = guild.getEveryoneRole()
        val users = extraUsers.toMutableList()
        users.add(ownerId)

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
            parentId = closedCategoryId
        }

        // Send close message to owner
        val owner = guild.getMember(ownerId)
        val dms = owner.getDmChannel()
        dms.createMessage {
            content = "Your ticket #${channel.name} has been closed"
        }

        // Remove buttons
        val botMsg = channel.getMessage(botMsgId!!)
        botMsg.edit {
            components {
                removeAll()
            }
        }

        // Update database
        transaction {
            ticket.closeTime = closeTime
            ticket.closed = true
        }

        // Update cache
        ticketsWaitingClose.remove(ticket)
        closeTicketTasks.remove(ticket)
    }

    private fun schedulePendingTicketsClose() {
        val now = Clock.System.now()
        for ((ticket, instant) in ticketsWaitingClose) {
            scheduleTicketClose(ticket, instant, now)
        }
    }

    private fun scheduleTicketClose(ticket: Ticket, closeInstant: Instant, now: Instant) {
        var scheduleTime = closeInstant - now
        if (scheduleTime.inWholeSeconds < MIN_SCHEDULE_TICKET_CLOSE_DELAY) {
            scheduleTime = Duration.seconds(MIN_SCHEDULE_TICKET_CLOSE_DELAY)
        }

        LOGGER.debug { "Scheduling ticket ${ticket.id} to close in $scheduleTime" }
        val task = scheduler.schedule(scheduleTime) {
            doCloseTicket(ticket.id.value, closeInstant)
        }
        closeTicketTasks[ticket] = task
    }

    fun ticketConfigExists(id: Int): Boolean = ticketConfigs.any { it.id.value == id }

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

    private suspend fun ChatCommandContext<*>.getTicket(
        ticketId: Int?,
        isGlobalId: Boolean?,
        configId: Int?
    ): Ticket? {
        if (ticketId != null) {
            // Get ticket using its ID
            return if (isGlobalId == true) {
                tickets.find { it.id.value == ticketId }
            } else if (configId != null) {
                tickets.find { it.ticketId == ticketId && it.ticketConfigId.value == configId }
            } else if (ticketConfigs.size == 1) {
                tickets.find { it.ticketId == ticketId }
            } else {
                message.respond {
                    content = "You must specify a ticket config id " +
                            "or a global ticket id if there are multiple ticket configs"
                }
                null
            }
        } else {
            // Get ticket corresponding to channel
            val channelId = channel.id
            val ticket = tickets.find { it.channelId == channelId }
            if (ticket == null) {
                message.respond {
                    content = "You must run this command in a ticket channel or specify a ticket id"
                }
            }

            return ticket
        }
    }

    private suspend fun CheckContext<*>.canCloseTicket() {
        if (!passed) {
            return
        }

        // Moderators can close tickets from anywhere
        val channelId = Snowflake(channelIdFor(event)!!)
        if (channelId !in ticketsByChannelId.keys) {
            isModerator()
        } else {
            // Only ticket owners can close tickets, from its channel
            val userId = userFor(event)!!.id
            val ticket = ticketsByChannelId[channelId]
            if (ticket!!.ownerId != userId) {
                fail("You must be the owner of the ticket to close it")
            }
        }
    }
}
