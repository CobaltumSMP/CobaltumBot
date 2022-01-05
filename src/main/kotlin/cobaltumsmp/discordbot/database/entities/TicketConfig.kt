package cobaltumsmp.discordbot.database.entities

import cobaltumsmp.discordbot.database.tables.TicketConfigs
import cobaltumsmp.discordbot.database.tables.Tickets
import dev.kord.common.entity.Snowflake
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TicketConfig(id: EntityID<Int>) : IntEntity(id) {
    private var _ticketCategoryId by TicketConfigs.ticketCategoryId
    var ticketCategoryId: Snowflake
        get() = Snowflake(_ticketCategoryId)
        set(value) {
            _ticketCategoryId = value.value
        }

    private var _closedTicketCategoryId by TicketConfigs.closedTicketCategoryId
    var closedTicketCategoryId: Snowflake
        get() = Snowflake(_closedTicketCategoryId)
        set(value) {
            _closedTicketCategoryId = value.value
        }

    private var _messageId by TicketConfigs.messageId
    var messageId: Snowflake
        get() = Snowflake(_messageId)
        set(value) {
            _messageId = value.value
        }

    private var _messageChannelId by TicketConfigs.messageChannelId
    var messageChannelId: Snowflake
        get() = Snowflake(_messageChannelId)
        set(value) {
            _messageChannelId = value.value
        }

    private var _roles by TicketConfigs.roles
    var roles: List<Snowflake>
        get() = _roles.split(",").map { Snowflake(it) }
        set(value) {
            _roles = value.joinToString(",") { it.value.toString() }
        }

    var ticketCount by TicketConfigs.ticketCount

    var ticketsBaseName by TicketConfigs.ticketsBaseName

    var name by TicketConfigs.name

    val tickets by Ticket referrersOn Tickets.ticketConfigId

    fun displayName(): String = if (name.isNotBlank()) "'$name' #$id" else "#$id"

    override fun equals(other: Any?): Boolean = other is TicketConfig && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object : IntEntityClass<TicketConfig>(TicketConfigs) {
        const val MAX_ROLES = TicketConfigs.ROLES_LENGTH / 20
    }
}
