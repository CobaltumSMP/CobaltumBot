package cobaltumsmp.discordbot.extensions.ticketsystem

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

internal const val TICKET_CONFIG_NAME_LENGTH = 64
internal const val TICKET_CONFIG_ROLES_LENGTH = 255

object Tickets : Table() {

    // The ID of the ticket globally
    val globalTicketId = integer("global_ticket_id").autoIncrement().uniqueIndex()

    // The ID of the ticket within its config
    val ticketId = integer("ticketId")

    // The ID/Snowflake of the channel for this ticket
    val channelId = long("channel_id")

    // Whether this ticket is closed
    val closed = bool("closed")

    // The time this ticket will or has closed
    val closeTime = long("close_time")

    // The ID/Snowflake of the base message sent by the bot
    val botMsgId = long("bot_msg_id")

    // The ID/Snowflake of the owner of this ticket
    val ownerId = long("owner_id")

    // The ID of the ticket config this ticket belongs to
    val ticketConfigId = reference("ticket_config_id", TicketConfigs)
    override val primaryKey = PrimaryKey(globalTicketId, name = "PK_global_ticket_id")
}

object TicketConfigs : IntIdTable() {

    // The ID of the category which open ticket channels belong to
    val ticketCategoryId = long("ticket_category_id")

    // The ID of the category which closed ticket channels belong to
    val closedTicketCategoryId = long("closed_ticket_category_id")

    // The ID of the message with the button to open a ticket
    val messageId = long("message_id")

    // The ID of the channel with the message with the button to open a ticket
    val messageChannelId = long("message_channel_id")

    // A list of role IDs which have access to all tickets in this ticket config, separated by a comma
    val roles = varchar("roles", TICKET_CONFIG_ROLES_LENGTH)

    val name = varchar("name", TICKET_CONFIG_NAME_LENGTH)
}
