package cobaltumsmp.discordbot.database.entities

import cobaltumsmp.discordbot.database.tables.Suggestions
import cobaltumsmp.discordbot.extensions.suggestions.SuggestionStatus
import dev.kord.common.entity.Snowflake
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Suggestion(id: EntityID<Int>) : IntEntity(id) {
    private var _ownerId by Suggestions.ownerId
    var ownerId: Snowflake
        get() = _ownerId.deserialize()
        set(value) {
            _ownerId = value.serialize()
        }

    var ownerName by Suggestions.ownerName

    var ownerAvatarUrl by Suggestions.ownerAvatarUrl

    private var _status by Suggestions.status
    var status: SuggestionStatus
        get() = _status.deserializeSuggestionStatus()
        set(value) {
            _status = value.serialize()
        }

    private var _messageId by Suggestions.messageId
    var messageId: Snowflake?
        get() = _messageId?.deserialize()
        set(value) {
            _messageId = value?.serialize()
        }

    var description by Suggestions.description

    var response by Suggestions.response

    private var _positiveVoterIds by Suggestions.positiveVoterIds
    var positiveVoterIds: List<Snowflake>
        get() = _positiveVoterIds.deserializeSnowflakes()
        set(value) {
            _positiveVoterIds = value.serialize()
        }

    private var _negativeVoterIds by Suggestions.negativeVoterIds
    var negativeVoterIds: List<Snowflake>
        get() = _negativeVoterIds.deserializeSnowflakes()
        set(value) {
            _negativeVoterIds = value.serialize()
        }

    val positiveVotes get() = positiveVoterIds.size
    val negativeVotes get() = negativeVoterIds.size
    val voteDelta get() = positiveVotes - negativeVotes

    companion object : IntEntityClass<Suggestion>(Suggestions)
}
