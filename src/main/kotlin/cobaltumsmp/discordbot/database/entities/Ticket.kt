package cobaltumsmp.discordbot.database.entities

import cobaltumsmp.discordbot.database.tables.Tickets
import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Ticket(id: EntityID<Int>) : IntEntity(id) {
    var ticketId by Tickets.ticketId

    private var _channelId by Tickets.channelId
    var channelId: Snowflake
        get() = Snowflake(_channelId)
        set(value) {
            _channelId = value.value
        }

    private var _createTime by Tickets.createTime
    var createTime: Instant
        get() = _createTime.toInstant()
        set(value) {
            _createTime = value.toString()
        }

    var closed by Tickets.closed

    private var _closeTime by Tickets.closeTime
    var closeTime: Instant?
        get() = _closeTime?.toInstant()
        set(value) {
            _closeTime = value?.toString()
        }

    private var _botMsgId by Tickets.botMsgId
    var botMsgId: Snowflake?
        get() = _botMsgId?.let { Snowflake(it) }
        set(value) {
            _botMsgId = value?.value
        }

    private var _ownerId by Tickets.ownerId
    var ownerId: Snowflake
        get() = Snowflake(_ownerId)
        set(value) {
            _ownerId = value.value
        }

    private var _extraUsers by Tickets.extraUsers
    var extraUsers: List<Snowflake>
        get() = _extraUsers.split(",").map { Snowflake(it) }
        set(value) {
            _extraUsers = value.joinToString(",") { it.value.toString() }
        }

    private var _assignedUserId by Tickets.assignedUserId
    var assignedUserId: Snowflake?
        get() = _assignedUserId?.let { Snowflake(it) }
        set(value) {
            _assignedUserId = value?.value
        }

    var config by TicketConfig referencedOn Tickets.ticketConfigId

    override fun equals(other: Any?): Boolean = other is Ticket && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object : IntEntityClass<Ticket>(Tickets) {
        const val MAX_EXTRA_USERS = Tickets.EXTRA_USERS_LENGTH / 20
    }
}
