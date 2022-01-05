package cobaltumsmp.discordbot.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Tickets : IntIdTable(columnName = "global_ticket_id") {
    const val EXTRA_USERS_LENGTH = 255
    const val BASE_NAME_LENGTH = 15
    private const val TIME_LENGTH = 30

    // The ID of the ticket within its config
    val ticketId = integer("ticketId")

    // The ID/Snowflake of the channel for this ticket
    val channelId = long("channel_id")

    // The time the ticket was created
    val createTime = long("create_time")

    // Whether this ticket is closed
    val closed = bool("closed").default(false)

    // The time (in ISO-8601 format) this ticket will or has closed
    val closeTime = varchar("close_time", TIME_LENGTH).nullable()

    // The ID/Snowflake of the base message sent by the bot
    val botMsgId = long("bot_msg_id").nullable()

    // The ID/Snowflake of the owner of this ticket
    val ownerId = long("owner_id")

    // A list of user IDs/Snowflakes that have special access to this ticket
    val extraUsers = varchar("extra_users", EXTRA_USERS_LENGTH).default("")

    // The ID/Snowflake of the user that is currently assigned to this ticket
    val assignedUserId = long("assigned_user_id").nullable()

    // The ID of the ticket config this ticket belongs to
    val ticketConfigId = reference("ticket_config_id", TicketConfigs)
}
