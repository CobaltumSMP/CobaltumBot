package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake

internal val DISCORD_TOKEN = env("DISCORD_TOKEN")
internal val ENVIRONMENT = envOrNull("ENVIRONMENT") ?: "production"

internal val PREFIX = env("PREFIX")
internal val GUILD_MAIN = envOrNull("GUILD_ID_MAIN") ?.let { Snowflake(it) }
