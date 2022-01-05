package cobaltumsmp.discordbot.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable

@OptIn(ExperimentalUnsignedTypes::class)
object TicketConfigs : IntIdTable() {
    const val NAME_LENGTH = 64
    const val ROLES_LENGTH = 255

    // The ID of the category which open ticket channels belong to
    val ticketCategoryId = ulong("ticket_category_id")

    // The ID of the category which closed ticket channels belong to
    val closedTicketCategoryId = ulong("closed_ticket_category_id")

    // The ID of the message with the button to open a ticket
    val messageId = ulong("message_id")

    // The ID of the channel with the message with the button to open a ticket
    val messageChannelId = ulong("message_channel_id")

    // A list of role IDs which have access to all tickets in this ticket config, separated by a comma
    val roles = varchar("roles", ROLES_LENGTH)

    // How many tickets have been opened in this ticket config
    val ticketCount = integer("ticket_count").default(0)

    // The base name of the ticket channels
    val ticketsBaseName = varchar("tickets_base_name", Tickets.BASE_NAME_LENGTH).default("ticket")

    // The name of the ticket config
    val name = varchar("name", NAME_LENGTH).default("")
}
