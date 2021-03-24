package cobaltumsmp.discordbot;

import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Utility methods.
 */
public class Util {
    private static final Properties dotEnv = new Properties();

    public static boolean isSystemEnvEmpty(String name) {
        return isMapValueEmpty(System.getenv(), name);
    }

    public static boolean isMapValueEmpty(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null || value.trim().isEmpty();
    }

    /**
     * Checks if the {@linkplain Message#getUserAuthor() message user author}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isMessageUserAuthorEmpty(Message message) {
        if (message.getUserAuthor().isEmpty()) {
            Main.LOGGER.error(new IllegalStateException(
                    "Message#getUserAuthor was empty"));
            return true;
        }
        return false;
    }

    /**
     * Checks if {@link org.javacord.api.DiscordApi#getChannelById}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isChannelByIdEmpty(Optional<Channel> channel) {
        if (channel.isEmpty()) {
            Main.LOGGER.error(new IllegalStateException("DiscordApi#getChannelById was empty"));
            return true;
        }
        return false;
    }

    /**
     * Checks if {@link Channel#asTextChannel()}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isTextChannelEmpty(Channel channel) {
        if (channel.asTextChannel().isEmpty()) {
            Main.LOGGER.error(new IllegalStateException("Channel#asTextChannel was empty"));
            return true;
        }
        return false;
    }

    /**
     * Sends "There was an unexpected error." to the channel of the provided message.
     */
    public static void unexpectedErrorMessageResponse(Message message) {
        unexpectedErrorMessage(message.getChannel());
    }

    /**
     * Sends "There was an unexpected error." to the provided channel.
     */
    public static void unexpectedErrorMessage(TextChannel channel) {
        channel.sendMessage(I18nUtil.key("unexpected_error"));
    }

    /**
     * Load properties from a {@code .env} file in the directory from where the bot was launched.
     *
     * @throws IOException if an error occurs during the file reading
     */
    public static void loadDotEnv() throws IOException {
        File dotEnvFile = new File(".env");
        if (dotEnvFile.exists() && !dotEnvFile.isDirectory()) {
            dotEnv.load(new FileReader(dotEnvFile));
        }
    }

    public static String getEnv(String variableName) {
        return dotEnv.getProperty(variableName, isSystemEnvEmpty(variableName)
                ? "" : System.getenv(variableName)).trim();
    }
}
