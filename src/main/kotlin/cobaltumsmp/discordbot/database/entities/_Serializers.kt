package cobaltumsmp.discordbot.database.entities

import dev.kord.common.entity.Snowflake
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

// Snowflake
internal fun Snowflake.serialize(): ULong = this.value

internal fun ULong.deserialize(): Snowflake = Snowflake(this)

// Snowflake list
internal fun List<Snowflake>.serialize(): String =
    this.joinToString(",") { it.serialize().toString() }

internal fun String.deserializeSnowflakes(): List<Snowflake> =
    this.split(",").filter { it.isNotBlank() }.map { Snowflake(it) }

// Instants
internal fun Instant.serialize(): String = this.toString()

internal fun String.deserializeInstant(): Instant = this.toInstant()
