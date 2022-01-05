package cobaltumsmp.discordbot.database

import com.kotlindiscord.kord.extensions.utils.envOrNull
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import java.net.URI

private val DATABASE_URL = envOrNull("DATABASE_URL") // Heroku Postgres database URL
private val DB_URL = envOrNull("DB_URL")
private val DB_USER = envOrNull("DB_USER")
private val DB_PASS = envOrNull("DB_PASS")
private val JDBC_DRIVER = envOrNull("JDBC_DRIVER")

private val LOGGER = KotlinLogging.logger("cobaltumsmp.discordbot.database")

class Database private constructor() {
    companion object {
        init {
            val url: String
            val username: String
            val password: String
            if (DATABASE_URL != null) {
                val uri = URI(DATABASE_URL)

                username = uri.userInfo.split(":")[0]
                password = uri.userInfo.split(":")[1]
                url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"
            } else {
                url = DB_URL!!
                username = DB_USER!!
                password = DB_PASS!!
            }

            LOGGER.info { "Connecting to database $url" + (JDBC_DRIVER?.let { " with driver $it" } ?: "") }

            if (JDBC_DRIVER != null) {
                Database.connect(url, user = username, password = password, driver = JDBC_DRIVER)
            } else {
                Database.connect(url, user = username, password = password)
            }
        }

        fun connect() {
            // Do nothing, just used to make the init block run
        }
    }
}
