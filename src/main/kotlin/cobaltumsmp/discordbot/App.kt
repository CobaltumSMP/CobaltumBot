package cobaltumsmp.discordbot

import cobaltumsmp.discordbot.extensions.LoggingExtension
import cobaltumsmp.discordbot.extensions.ModerationExtension
import cobaltumsmp.discordbot.extensions.UtilsExtension
import cobaltumsmp.discordbot.extensions.VersionCheckExtension
import cobaltumsmp.discordbot.extensions.ticketsystem.TicketSystemExtension
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

        presence {
            val prefix = if (ENVIRONMENT == "development") {
                "???"
            } else {
                PREFIX
            }

            playing("${prefix}help")
        }

        // TODO: Add back i18n

        extensions {
            add(::UtilsExtension)
            add(::VersionCheckExtension)
            add(::LoggingExtension)
            add(::ModerationExtension)

            add(::TicketSystemExtension)
        }
    }
    bot.start()
}
