package cobaltumsmp.discordbot

import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake

internal fun multipleSnowflakes(key: String): List<Snowflake> =
    envOrNull(key)?.let { v -> v.split(",").map { Snowflake(it.trim()) } } ?: emptyList()
