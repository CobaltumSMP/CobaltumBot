package cobaltumsmp.discordbot.database.entities

import cobaltumsmp.discordbot.database.tables.Tickets
import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Ticket(id: EntityID<Int>) : IntEntity(id) {
    var ticketId by Tickets.ticketId

    private var _channelId by Tickets.channelId
    var channelId: Snowflake
        get() = _channelId.deserialize()
        set(value) {
            _channelId = value.serialize()
        }

    private var _createTime by Tickets.createTime
    var createTime: Instant
        get() = _createTime.deserializeInstant()
        set(value) {
            _createTime = value.serialize()
        }

    var closed by Tickets.closed

    private var _closeTime by Tickets.closeTime
    var closeTime: Instant?
        get() = _closeTime?.deserializeInstant()
        set(value) {
            _closeTime = value?.serialize()
        }

    private var _botMsgId by Tickets.botMsgId
    var botMsgId: Snowflake?
        get() = _botMsgId?.deserialize()
        set(value) {
            _botMsgId = value?.serialize()
        }

    private var _ownerId by Tickets.ownerId
    var ownerId: Snowflake
        get() = _ownerId.deserialize()
        set(value) {
            _ownerId = value.serialize()
        }

    private var _extraUsers by Tickets.extraUsers
    var extraUsers: List<Snowflake>
        get() = _extraUsers.deserializeSnowflakes()
        set(value) {
            _extraUsers = value.serialize()
        }

    private var _assignedUserId by Tickets.assignedUserId
    var assignedUserId: Snowflake?
        get() = _assignedUserId?.deserialize()
        set(value) {
            _assignedUserId = value?.serialize()
        }

    var ticketConfigId by Tickets.ticketConfigId

    var config by TicketConfig referencedOn Tickets.ticketConfigId

    override fun equals(other: Any?): Boolean = other is Ticket && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object : IntEntityClass<Ticket>(Tickets) {
        const val MAX_EXTRA_USERS = Tickets.EXTRA_USERS_LENGTH / 20
    }
}
