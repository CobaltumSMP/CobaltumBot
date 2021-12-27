package cobaltumsmp.discordbot.extensions

import cobaltumsmp.discordbot.isModerator
import cobaltumsmp.discordbot.multipleSnowflakes
import com.google.gson.JsonParseException
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.quiltmc.launchermeta.version_manifest.VersionEntry
import org.quiltmc.launchermeta.version_manifest.VersionManifest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_INITIAL_DATA_ATTEMPTS = 3
private const val DEFAULT_CHECK_DELAY = 30

private val CHECK_DELAY = envOrNull("VC_CHECK_DELAY")?.toInt() ?: DEFAULT_CHECK_DELAY
private val JIRA_UPDATE_CHANNELS = multipleSnowflakes("CHANNEL_ID_VC_JIRA")
private val MC_UPDATES_CHANNELS = multipleSnowflakes("CHANNEL_ID_VC_MC")
private val JIRA_URL = envOrNull("VC_JIRA_URL")
private val MINECRAFT_URL = envOrNull("VC_MINECRAFT_URL")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.extensions.VersionCheckExtension")

internal class VersionCheckExtension : Extension() {
    override val name = "Version checker"
    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {
                ignoreUnknownKeys = true
            })
        }
    }
    private val scheduler = Scheduler()
    private val jiraUpdateChannels = ArrayList<TextChannel>()
    private val mcUpdateChannels = ArrayList<TextChannel>()
    private var jiraVersions: List<JiraVersion> = ArrayList()
    private var mcVersions: List<VersionEntry> = ArrayList()
    private var checking = false

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setup() {
        JIRA_UPDATE_CHANNELS.forEach {
            val channel = kord.getChannelOf<TextChannel>(it)
            channel?.let(jiraUpdateChannels::add)
                ?: LOGGER.warn { "One of the Jira update channels is invalid ($it)" }
        }
        MC_UPDATES_CHANNELS.forEach {
            val channel = kord.getChannelOf<TextChannel>(it)
            channel?.let(mcUpdateChannels::add)
                ?: LOGGER.warn { "One of the Minecraft update channels is invalid ($it)" }
        }

        JIRA_URL ?: run {
            LOGGER.error { "Jira URL is not set" }
            return@setup
        }
        MINECRAFT_URL ?: run {
            LOGGER.error { "Minecraft URL is not set" }
            return@setup
        }

        event<ReadyEvent> {
            action {
                if (jiraUpdateChannels.isEmpty() && mcUpdateChannels.isEmpty()) {
                    LOGGER.warn { "No update channels are set" }
                    return@action
                }

                setupWithInitialData(AtomicInteger(0))
            }
        }

        chatCommand {
            name = "versioncheck"
            description = "Execute a version check"

            check { isModerator() }

            action {
                LOGGER.debug {
                    "Running manual version check task from command (requested by ${message.author?.username})"
                }

                message.respond("Manually running a version check")

                try {
                    val checked = checkUpdates()
                    if (!checked) {
                        message.respond("A version check was already running")
                    } else {
                        message.respond {
                            embed {
                                title = "Version check ran successfully"
                                description = """
                                    **Minecraft** | Latest version: ${getLatestMinecraftVersion().id}
                                    **Minecraft** | Total versions: ${mcVersions.size}
                                    **Jira** | Latest version: ${getLatestJiraVersion().name}
                                    **Jira** | Total versions: ${jiraVersions.size}
                                """.trimIndent()
                            }
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.error(e) { "There was an error in a manually run version check" }
                    message.respond("There was an error, please report this to a bot admin")
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun setupWithInitialData(attempt: AtomicInteger) {
        LOGGER.info { "Fetching initial data" }
        val jiraVersions = ArrayList<JiraVersion>()
        val mcVersions = ArrayList<VersionEntry>()

        try {
            jiraVersions.addAll(getJiraVersions())
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to get the initial Jira versions" }
        }

        try {
            mcVersions.addAll(getMinecraftVersions())
        } catch (e: JsonParseException) {
            LOGGER.error(e) { "Failed to parse the Minecraft version manifest" }
        } catch (e: Exception) {
            LOGGER.error(e) { "Failed to get the initial Minecraft versions" }
        }

        this.jiraVersions = jiraVersions
        this.mcVersions = mcVersions

        if (this.jiraVersions.isEmpty() && this.mcVersions.isEmpty()) {
            if (attempt.getAndIncrement() >= MAX_INITIAL_DATA_ATTEMPTS) {
                LOGGER.error { "Failed to get the initial data after $MAX_INITIAL_DATA_ATTEMPTS attempts" }
            } else {
                LOGGER.warn { "Failed to get the initial data, retrying in 30 seconds" }
                scheduler.schedule(DEFAULT_CHECK_DELAY.toLong()) { setupWithInitialData(attempt) }
            }

            return
        } else {
            if (this.jiraVersions.isEmpty()) {
                LOGGER.warn { "Failed to get the initial Jira versions, it will be fetched next check" }
            } else {
                LOGGER.info { "Jira      | Total versions: ${this.jiraVersions.size}" }
                LOGGER.info { "Jira      | Latest version: ${getLatestJiraVersion().name}" }
            }

            if (this.mcVersions.isEmpty()) {
                LOGGER.warn { "Failed to get the initial Minecraft versions, it will be fetched next check" }
            } else {
                LOGGER.info { "Minecraft | Total versions: ${this.mcVersions.size}" }
                LOGGER.info { "Minecraft | Latest version: ${getLatestMinecraftVersion().id}" }
            }
        }

        schedule()
        LOGGER.debug { "Scheduled version updates with $CHECK_DELAY seconds of delay" }
    }

    private fun schedule() {
        scheduler.schedule(CHECK_DELAY.toLong(), name = "Scheduled update check") {
            checkUpdates()
            schedule()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun checkUpdates(): Boolean {
        if (checking) {
            LOGGER.warn { "Already checking for updates. Skipping check" }
            return false
        }

        checking = true

        try {
            val newJiraVersion = checkJiraUpdates()

            if (newJiraVersion != null) {
                jiraUpdateChannels.forEach {
                    it.createMessage(
                        "A new version has been added to the Minecraft issue tracker: ${newJiraVersion.name}"
                    )
                }
            }

            val newMcVersion = checkMinecraftUpdates()

            if (newMcVersion != null) {
                mcUpdateChannels.forEach {
                    it.createMessage("A new Minecraft ${newMcVersion.type} is out: ${newMcVersion.id}!")
                }
            }
        } catch (e: Throwable) {
            LOGGER.catching(e)
        }

        checking = false

        return true
    }

    private suspend fun checkJiraUpdates(): JiraVersion? {
        LOGGER.debug { "Checking for Jira updates" }

        val newJiraVersions = getJiraVersions()
        if (jiraVersions.isEmpty()) {
            jiraVersions = newJiraVersions
            return null
        }

        val new = newJiraVersions.find {
            it !in jiraVersions && "future version" !in it.name.lowercase(Locale.getDefault())
        }

        if (new != null) {
            LOGGER.info { "Jira      | New version: ${new.name}" }
            LOGGER.info { "Jira      | Total versions: ${this.jiraVersions.size}" }
        } else {
            LOGGER.debug { "Jira      | New version: N/A" }
            LOGGER.debug { "Jira      | Total versions: ${this.jiraVersions.size}" }
        }

        jiraVersions = newJiraVersions

        return new
    }

    private suspend fun checkMinecraftUpdates(): VersionEntry? {
        LOGGER.debug { "Checking for Minecraft updates" }

        val newMcVersions = getMinecraftVersions()
        if (mcVersions.isEmpty()) {
            mcVersions = newMcVersions
            return null
        }

        val new = newMcVersions.find { it !in mcVersions }

        if (new != null) {
            LOGGER.info { "Minecraft | New version: ${new.id}" }
            LOGGER.info { "Minecraft | Total versions: ${this.mcVersions.size}" }
        } else {
            LOGGER.debug { "Minecraft | New version: N/A" }
            LOGGER.debug { "Minecraft | Total versions: ${this.mcVersions.size}" }
        }

        mcVersions = newMcVersions

        return new
    }

    private suspend fun getJiraVersions(): List<JiraVersion> = client.get(JIRA_URL!!)

    private suspend fun getMinecraftVersions(): List<VersionEntry> {
        val response: HttpResponse = client.get(MINECRAFT_URL!!)
        val body: String = response.receive()
        val manifest = VersionManifest.fromString(body)
        return manifest.versions
    }

    private fun getLatestJiraVersion(): JiraVersion = jiraVersions[jiraVersions.size - 1]

    private fun getLatestMinecraftVersion(): VersionEntry = mcVersions[0]

    @Serializable
    data class JiraVersion(val id: String, val name: String)
}
