package cobaltumsmp.discordbot

import cobaltumsmp.discordbot.extensions.UtilsExtension
import cobaltumsmp.discordbot.extensions.VersionCheckExtension
import com.kotlindiscord.kord.extensions.ExtensibleBot

internal suspend fun main() {
    val bot = ExtensibleBot(DISCORD_TOKEN) {
        chatCommands {
            defaultPrefix = PREFIX
            enabled = true

            prefix { default ->
                if (ENVIRONMENT == "development") {
                    "???"
                } else {
                    default
                }
            }
        }

        // TODO: Add back i18n

        extensions {
            add(::UtilsExtension)
            add(::VersionCheckExtension)
        }
    }
    bot.start()
}
