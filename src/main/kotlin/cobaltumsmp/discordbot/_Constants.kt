package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake

internal val DISCORD_TOKEN = env("DISCORD_TOKEN")
internal val ENVIRONMENT = envOrNull("ENVIRONMENT") ?: "production"
internal val LOCALE = envOrNull("BOT_LOCALE")

internal val PREFIX = env("PREFIX")
internal val GUILD_MAIN = Snowflake(env("GUILD_ID_MAIN"))

// Roles
internal val ROLE_ID_MOD = envOrNull("ROLE_ID_MOD")?.let { Snowflake(it) }
internal val ROLE_ID_ADMIN = envOrNull("ROLE_ID_ADMIN")?.let { Snowflake(it) }
internal val ROLE_ID_OWNER = envOrNull("ROLE_ID_OWNER")?.let { Snowflake(it) }
internal val MODERATOR_ROLES = listOfNotNull(ROLE_ID_MOD, ROLE_ID_ADMIN, ROLE_ID_OWNER).toMutableList()
internal val ADMINISTRATOR_ROLES = listOfNotNull(ROLE_ID_ADMIN, ROLE_ID_OWNER).toMutableList()
internal val OWNER_ROLES = listOfNotNull(ROLE_ID_OWNER).toMutableList()

// Channels
internal val CHANNEL_ID_BROADCAST = multipleSnowflakes("CHANNEL_ID_BROADCAST")
