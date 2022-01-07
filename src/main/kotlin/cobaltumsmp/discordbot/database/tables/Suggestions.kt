package cobaltumsmp.discordbot.database.tables

import cobaltumsmp.discordbot.extensions.suggestions.SuggestionStatus
import org.jetbrains.exposed.dao.id.IntIdTable

@OptIn(ExperimentalUnsignedTypes::class)
object Suggestions : IntIdTable() {
    private const val USERNAME_LENGTH = 255
    private const val STATUS_LENGTH = 32
    private const val VOTERS_LENGTH = 255

    val ownerId = ulong("owner_id")

    val ownerName = varchar("owner_name", USERNAME_LENGTH)

    val ownerAvatarUrl = text("owner_avatar_url").nullable()

    val status = varchar("status", STATUS_LENGTH).default(SuggestionStatus.Open.name)

    val messageId = ulong("message_id").nullable()

    val description = text("description")

    val response = text("response").nullable()

    val positiveVoterIds = varchar("positive_voter_ids", VOTERS_LENGTH).default("")

    val negativeVoterIds = varchar("negative_voter_ids", VOTERS_LENGTH).default("")
}
