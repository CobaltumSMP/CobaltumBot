package cobaltumsmp.discordbot.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import org.koin.core.component.inject

abstract class BaseExtension : Extension() {
    override val bundle = "cobaltumbot"

    private val translationsProvider: TranslationsProvider by inject()

    protected fun translate(key: String, replacements: Array<Any?> = arrayOf()): String =
        translationsProvider.translate(key, bundle, replacements)
}
