package cobaltumsmp.discordbot

import cobaltumsmp.discordbot.extensions.AutoRoleExtension
import cobaltumsmp.discordbot.extensions.ModerationExtension
import cobaltumsmp.discordbot.extensions.UtilsExtension
import cobaltumsmp.discordbot.extensions.VersionCheckExtension
import cobaltumsmp.discordbot.extensions.suggestions.SuggestionsExtension
import cobaltumsmp.discordbot.extensions.ticketsystem.TicketSystemExtension
import com.kotlindiscord.kord.extensions.ExtensibleBot
import java.util.Locale

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

        i18n {
            if (LOCALE != null) {
                defaultLocale = Locale.forLanguageTag(LOCALE)
            }
        }

        extensions {
            add(::UtilsExtension)
            add(::VersionCheckExtension)
            add(::ModerationExtension)
            add(::SuggestionsExtension)
            add(::AutoRoleExtension)

            add(::TicketSystemExtension)
        }
    }
    bot.start()
}
