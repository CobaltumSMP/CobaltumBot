package cobaltumsmp.discordbot.extensions.suggestions

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.DISCORD_FUCHSIA
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_RED
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import dev.kord.common.Color

enum class SuggestionStatus(override val readableName: String, val color: Color) : ChoiceEnum {
    Open("Open", DISCORD_BLURPLE),

    Approved("Approved", DISCORD_FUCHSIA),
    Denied("Denied", DISCORD_RED),
    Implemented("Implemented", DISCORD_GREEN),

    Duplicate("Duplicate", DISCORD_BLACK),
    Invalid("Invalid", DISCORD_RED),
}
